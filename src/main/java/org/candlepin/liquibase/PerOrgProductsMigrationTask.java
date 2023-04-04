/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.liquibase;

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * The PerOrgProductsMigrationTask performs the post-db upgrade data migration to the cp2_* tables.
 *
 * This task migrates data from the old cp_* tables to cp2_* tables, adding UUIDs and the new
 * timestamps where necessary. Only products and content referenced by existing pools or
 * subscriptions will be migrated over. Unreferenced objects will be silently discarded.
 */
public class PerOrgProductsMigrationTask extends LiquibaseCustomTask {

    /** The maximum number of parameters we can cram into a single statement on all DBs. */
    private static final int MAX_PARAMETERS_PER_STATEMENT = 32000;

    protected Map<String, String> migratedProducts;
    protected Map<String, String> migratedContent;

    public PerOrgProductsMigrationTask(Database database, CustomTaskLogger logger) {
        super(database, logger);

        this.migratedProducts = new HashMap<>();
        this.migratedContent = new HashMap<>();
    }

    /**
     * Generates a prepared statement for performing a bulk insert into the given table
     *
     * @param table
     *  the table into which to insert the data
     *
     * @param rows
     *  the number of rows being inserted
     *
     * @param cols
     *  the columns receiving data for each row
     *
     * @return
     *  a PreparedStatement instance representing the bulk insert operation
     */
    protected PreparedStatement generateBulkInsertStatement(String table, int rows, String... cols)
        throws DatabaseException, SQLException {

        if (rows > 0) {
            StringBuilder builder, rowbuilder;

            rowbuilder = new StringBuilder(2 + cols.length * 2);

            rowbuilder.append('(');
            for (int i = 0; i < cols.length; ++i) {
                rowbuilder.append("?,");
            }
            rowbuilder.deleteCharAt(rowbuilder.length() - 1).append(')');

            builder = new StringBuilder(15 + 20 * cols.length + table.length() +
                (2 + cols.length * 3) * rows);

            builder.append("INSERT INTO ").append(table).append('(');
            for (String column : cols) {
                builder.append(column).append(',');
            }
            builder.deleteCharAt(builder.length() - 1).append(") VALUES");

            for (int i = 0; i < rows; ++i) {
                builder.append(rowbuilder).append(',');
            }
            builder.deleteCharAt(builder.length() - 1);

            return this.connection.prepareStatement(builder.toString());
        }

        return null;
    }

    /**
     * Executes the multi-org upgrade task.
     *
     * @throws DatabaseException
     *  if an error occurs while performing a database operation
     *
     * @throws SQLException
     *  if an error occurs while executing an SQL statement
     */
    public void execute() throws DatabaseException, SQLException {

        // Store the connection's auto commit setting, so we may temporarily clobber it.
        boolean autocommit = this.connection.getAutoCommit();

        try {
            this.connection.setAutoCommit(false);

            // Fetch our org count for prettier log messages
            ResultSet countQuery = this.executeQuery("SELECT count(id) FROM cp_owner");
            countQuery.next();
            int count = countQuery.getInt(1);
            countQuery.close();

            // Do our initial validation check to avoid doing a multi-hour migration and fail out
            // while validating the last org...
            boolean validated = true;
            ResultSet orgids = this.executeQuery("SELECT id, account FROM cp_owner");
            for (int index = 1; orgids.next(); ++index) {
                String orgid = orgids.getString(1);
                String account = orgids.getString(2);

                this.logger.info("Validating data for org %s (%s) (%d of %d)", account, orgid, index, count);

                int brokenKeys = fixBrokenActivationKeys(orgid);

                if (brokenKeys > 0) {
                    logger.info("Fixed %s activation keys referencing missing products", brokenKeys);
                }

                // Check for malformed objects which may end up in a bad state if we migrate
                boolean validationResult = this.checkForMalformedObjectRefs(orgid);

                if (!validationResult) {
                    validated = false;
                    this.logger.error("Org %s (%s) failed data validation", account, orgid);
                }
            }
            orgids.close();

            if (!validated) {
                throw new DatabaseException("One or more orgs failed data validation");
            }

            // Perform the actual per-org migration
            orgids = this.executeQuery("SELECT id, account FROM cp_owner");
            for (int index = 1; orgids.next(); ++index) {
                String orgid = orgids.getString(1);
                String account = orgids.getString(2);

                this.logger.info("Migrating data for org %s (%s) (%d of %d)", account, orgid, index, count);

                this.migrateProductData(orgid);
                this.migrateContentData(orgid);
            }

            this.migrateRelatedData();

            orgids.close();
            this.connection.commit();
        }
        finally {
            // Restore original autocommit state
            this.connection.setAutoCommit(autocommit);
        }
    }


    /**
     * Checks for any pools or subscriptions which contain bad data (typically products which do
     * not exist).
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     *
     * @return
     *  true if the check completed successfully; false otherwise
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected boolean checkForMalformedObjectRefs(String orgid) throws DatabaseException,
        SQLException {

        ResultSet badObjectRefs = this.executeQuery(
            "SELECT DISTINCT u.product_id, u.pool_id, u.subscription_id " +
            "FROM (SELECT NULLIF(p.product_id_old, '') AS product_id, p.id AS pool_id, " +
            "  NULL AS subscription_id " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "  UNION " +
            "  SELECT p.derived_product_id_old, p.id, NULL " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.derived_product_id_old, '') IS NULL " +
            "  UNION " +
            "  SELECT NULLIF(pp.product_id, ''), p.id, NULL " +
            "    FROM cp_pool p " +
            "    JOIN cp_pool_products pp " +
            "      ON p.id = pp.pool_id " +
            "    WHERE p.owner_id = ? " +
            "  UNION " +
            "  SELECT NULLIF(s.product_id, ''), NULL, s.id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "  UNION " +
            "  SELECT s.derivedproduct_id, NULL, s.id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(s.derivedproduct_id, '') IS NULL " +
            "  UNION " +
            "  SELECT NULLIF(sp.product_id, ''), NULL, s.id " +
            "    FROM cp_subscription_products sp " +
            "    JOIN cp_subscription s " +
            "      ON s.id = sp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "  UNION " +
            "  SELECT NULLIF(sdp.product_id, ''), NULL, s.id " +
            "    FROM cp_sub_derivedprods sdp " +
            "    JOIN cp_subscription s " +
            "      ON s.id = sdp.subscription_id " +
            "    WHERE s.owner_id = ?) u " +
            "LEFT JOIN cp_product p " +
            "  ON u.product_id = p.id " +
            "WHERE p.id IS NULL",
            orgid, orgid, orgid, orgid, orgid, orgid, orgid
        );

        boolean passed = true;

        while (badObjectRefs.next()) {
            passed = false;

            String productId = badObjectRefs.getString(1);
            String poolId = badObjectRefs.getString(2);
            String subscriptionId = badObjectRefs.getString(3);

            if (productId != null) {
                if (poolId != null) {
                    this.logger.error("  Pool \"%s\" references an unresolvable product: %s",
                        poolId, productId);
                }
                else if (subscriptionId != null) {
                    this.logger.error("  Subscription \"%s\" references an unresolvable product: %s",
                        subscriptionId, productId);
                }
            }
            else {
                if (poolId != null) {
                    this.logger.error("  Pool \"%s\" contains a null or empty product reference", poolId);
                }
                else if (subscriptionId != null) {
                    this.logger.error("  Subscription \"%s\" contains a null or empty product reference",
                        subscriptionId);
                }
            }
        }

        badObjectRefs.close();

        // Check for bad content refs on our environments
        badObjectRefs = this.executeQuery(
            "  SELECT e.id, ec.contentid " +
            "    FROM cp_environment e " +
            "    JOIN cp_env_content ec ON e.id = ec.environment_id " +
            "    LEFT JOIN cp_content c ON c.id = ec.contentid " +
            "    WHERE e.owner_id = ? " +
            "      AND c.id IS NULL",
            orgid
        );

        while (badObjectRefs.next()) {
            passed = false;

            String envId = badObjectRefs.getString(1);
            String contentId = badObjectRefs.getString(2);

            this.logger.error("  Environment \"%s\" references unresolvable content: %s", envId, contentId);
        }

        badObjectRefs.close();

        badObjectRefs = this.executeQuery(
            "  SELECT ak.id, akp.product_id " +
                "    FROM cp_activation_key ak " +
                "    JOIN cp_activationkey_product akp ON akp.key_id = ak.id " +
                "    LEFT JOIN cp_product p ON p.id = akp.product_id " +
                "    WHERE ak.owner_id = ? " +
                "      AND p.id IS NULL",
            orgid
        );

        while (badObjectRefs.next()) {
            passed = false;

            String keyId = badObjectRefs.getString(1);
            String productId = badObjectRefs.getString(2);

            this.logger.error("  Activation Key \"%s\" references an unresolvable product: %s", keyId,
                productId);
        }

        badObjectRefs.close();

        return passed;
    }

    private int fixBrokenActivationKeys(String orgid) throws DatabaseException, SQLException {
        ResultSet badObjectRefs = null;
        int brokenKeys = 0;

        try {
            // Check for bad product references on the activation keys
            badObjectRefs = this.executeQuery(
                "  SELECT ak.id, akp.product_id " +
                    "    FROM cp_activation_key ak " +
                    "    JOIN cp_activationkey_product akp ON akp.key_id = ak.id " +
                    "    LEFT JOIN cp_product p ON p.id = akp.product_id " +
                    "    WHERE ak.owner_id = ? " +
                    "      AND p.id IS NULL",
                orgid
            );

            while (badObjectRefs.next()) {
                brokenKeys++;

                String keyId = badObjectRefs.getString(1);
                String productId = badObjectRefs.getString(2);

                executeUpdate(
                    "DELETE FROM cp_activationkey_product WHERE product_id = ? and key_id = ?",
                    productId,
                    keyId
                );
            }
        }
        finally {
            if (badObjectRefs != null) {
                badObjectRefs.close();
            }
        }
        return brokenKeys;
    }

    /**
     * Performs bulk insertion of product. Used by the migrateProductData method.
     * <p></p>
     * Each row present in the specified collection should include seven elements, representing the
     * following columns: uuid, created, updated, multiplier, product_id, name, locked
     *
     * @param productRows
     *  A collection of object arrays representing a row of product data to insert
     */
    private void bulkInsertProductData(List<Object[]> productRows) throws DatabaseException, SQLException {
        if (productRows.size() > 0) {
            this.logger.info("  Performing bulk migration of %d product entities", productRows.size());

            PreparedStatement statement = this.generateBulkInsertStatement(
                "cp2_products", productRows.size(),
                "uuid", "created", "updated", "multiplier", "product_id", "name", "locked"
            );

            int index = 0;
            for (Object[] row : productRows) {
                for (Object col : row) {
                    this.setParameter(statement, ++index, col);
                }
            }

            int count = statement.executeUpdate();
            if (count != productRows.size()) {
                String errmsg = String.format(
                    "Wrong number of products migrated. Expected: %s, Inserted: %s",
                    productRows.size(), count
                );

                this.logger.error(errmsg);
                throw new DatabaseException(errmsg);
            }

            this.logger.info("  Migrated %d products", count);
            statement.close();
        }
    }

    /**
     * Migrates product data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateProductData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating product data...");

        List<Object[]> productRows = new LinkedList<>();
        Set<String> uuidCache = new HashSet<>();

        ResultSet productInfo = this.executeQuery(
            "SELECT DISTINCT p.id, p.created, p.updated, p.multiplier, p.name " +
            "FROM cp_product p " +
            "JOIN (" +
            "  SELECT p.product_id_old AS product_id " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.product_id_old, '') IS NULL " +
            "  UNION " +
            "  SELECT p.derived_product_id_old " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.derived_product_id_old, '') IS NULL " +
            "  UNION " +
            "  SELECT pp.product_id " +
            "    FROM cp_pool p " +
            "    JOIN cp_pool_products pp ON p.id = pp.pool_id " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(pp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT s.product_id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(s.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT s.derivedproduct_id " +
            "    FROM cp_subscription s " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(s.derivedproduct_id, '') IS NULL " +
            "  UNION " +
            "  SELECT sp.product_id " +
            "    FROM cp_subscription_products sp " +
            "    JOIN cp_subscription s ON s.id = sp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT sdp.product_id " +
            "    FROM cp_sub_derivedprods sdp " +
            "    JOIN cp_subscription s ON s.id = sdp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sdp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT pc.product_id " +
            "    FROM cp_product_content pc " +
            "    JOIN cp_env_content ec ON pc.content_id = ec.contentid " +
            "    JOIN cp_environment e ON e.id = ec.environment_id" +
            "    WHERE e.owner_id = ? " +
            "      AND NOT NULLIF(pc.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT akp.product_id " +
            "    FROM cp_activationkey_product akp " +
            "    JOIN cp_activation_key ak ON ak.id = akp.key_id " +
            "    WHERE ak.owner_id = ? " +
            "      AND NOT NULLIF(akp.product_id, '') IS NULL " +
            ") u ON u.product_id = p.id",
            orgid, orgid, orgid, orgid, orgid, orgid, orgid, orgid, orgid
        );

        int maxrows = MAX_PARAMETERS_PER_STATEMENT / 7;

        while (productInfo.next()) {
            String productId = productInfo.getString(1);
            String productUuid = this.migratedProducts.get(productId);

            if (productUuid == null) {
                this.logger.info("    Migrating product: %s", productId);

                productUuid = this.generateUUID();

                productRows.add(new Object[]{
                    productUuid,
                    productInfo.getObject(2),
                    productInfo.getObject(3),
                    productInfo.getObject(4),
                    productId,
                    productInfo.getObject(5),
                    0
                });

                // The rest of the product information will be migrated in one large batch operation
                // in the migrateRelatedData method.

                // If we've collected a full "block" of content data, migrate the block
                if (productRows.size() > maxrows) {
                    // Impl note: By some miracle, this doesn't close the outer result set.
                    this.bulkInsertProductData(productRows);
                    productRows.clear();
                }

                this.migratedProducts.put(productId, productUuid);
            }

            uuidCache.add(productUuid);
        }

        productInfo.close();

        // Do a bulk insert of any remaining unmigrated products we've encountered
        bulkInsertProductData(productRows);
        productRows.clear();

        // // Do a bulk insert for all the products for this orgs...
        if (uuidCache.size() > 0) {
            maxrows = MAX_PARAMETERS_PER_STATEMENT / 2;

            int lastBlock = 0;
            int blockSize = maxrows / 2; // 79999
            Iterator<String> uuidIterator = uuidCache.iterator();
            PreparedStatement statement = null;

            for (int offset = 0; offset < uuidCache.size(); offset += blockSize) {
                int remaining = Math.min(uuidCache.size() - offset, blockSize);
                if (remaining != lastBlock) {
                    if (statement != null) {
                        statement.close();
                    }

                    statement = this.generateBulkInsertStatement(
                        "cp2_owner_products", remaining, "owner_id", "product_uuid"
                    );

                    lastBlock = remaining;
                }

                int index = 0;
                while (remaining-- > 0) {
                    this.setParameter(statement, ++index, orgid);
                    this.setParameter(statement, ++index, uuidIterator.next());
                }

                int count = statement.executeUpdate();
                if (count != uuidCache.size()) {
                    String errmsg = String.format(
                        "Wrong number of products assigned to org: %s. Expected: %s, Inserted: %s",
                        orgid, uuidCache.size(), count
                    );

                    this.logger.error(errmsg);
                    throw new DatabaseException(errmsg);
                }
            }

            this.logger.info("  Assigned %d products to org", uuidCache.size());
            statement.close();
        }
    }

    /**
     * Performs bulk insertion of content. Used by the migrateContentData method. Each row present
     * in the specified collection should include 15 elements, representing the following columns:
     * uuid, content_id, created, updated, contenturl, gpgurl, label, metadataexpire, name,
     * releasever, requiredtags, type, vendor, arches and locked.
     *
     * @param contentRows
     *  A collection of object arrays representing a row of content data to insert
     */
    private void bulkInsertContentData(List<Object[]> contentRows) throws DatabaseException, SQLException {
        if (contentRows.size() > 0) {
            this.logger.info("  Performing bulk migration of %d content entities", contentRows.size());

            PreparedStatement statement = this.generateBulkInsertStatement(
                "cp2_content", contentRows.size(),
                "uuid", "content_id", "created", "updated", "contenturl", "gpgurl", "label",
                "metadataexpire", "name", "releasever", "requiredtags", "type", "vendor", "arches",
                "locked"
            );

            int index = 0;
            for (Object[] row : contentRows) {
                for (Object col : row) {
                    this.setParameter(statement, ++index, col);
                }
            }

            int count = statement.executeUpdate();
            if (count != contentRows.size()) {
                String errmsg = String.format(
                    "Wrong number of contents migrated. Expected: %s, Inserted: %s",
                    contentRows.size(), count
                );

                this.logger.error(errmsg);
                throw new DatabaseException(errmsg);
            }

            this.logger.info("  Migrated %d content", count);
            statement.close();
        }
    }

    /**
     * Migrates content data.
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateContentData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating content data...");

        List<Object[]> contentRows = new LinkedList<>();
        Set<String> uuidCache = new HashSet<>();

        ResultSet contentInfo = this.executeQuery(
            "SELECT DISTINCT c.id, c.created, c.updated, c.contenturl, c.gpgurl, c.label, " +
            "  c.metadataexpire, c.name, c.releasever, c.requiredtags, c.type, c.vendor, c.arches " +
            "FROM cp_content c " +
            "JOIN (" +
            "  SELECT pc.content_id AS content_id " +
            "    FROM cp_product_content pc" +
            "    JOIN cp2_products p ON pc.product_id = p.product_id " +
            "    JOIN cp2_owner_products op ON p.uuid = op.product_uuid " +
            "    WHERE op.owner_id = ?" +
            "      AND NOT NULLIF(pc.content_id, '') IS NULL " +
            "  UNION " +
            "  SELECT ec.contentid AS content_id " +
            "    FROM cp_env_content ec " +
            "    JOIN cp_environment e ON e.id = ec.environment_id " +
            "    WHERE e.owner_id = ?" +
            "      AND NOT NULLIF(ec.contentid, '') IS NULL " +
            ") u ON u.content_id = c.id",
            orgid, orgid
        );

        int maxrows = MAX_PARAMETERS_PER_STATEMENT / 15;

        while (contentInfo.next()) {
            String contentId = contentInfo.getString(1);
            String contentUuid = this.migratedContent.get(contentId);

            if (contentUuid == null) {
                this.logger.info("    Migrating content: %s", contentId);

                contentUuid = this.generateUUID();

                // Fetch current row...
                contentRows.add(new Object[] {
                    contentUuid,
                    contentInfo.getObject(1),
                    contentInfo.getObject(2),
                    contentInfo.getObject(3),
                    contentInfo.getObject(4),
                    contentInfo.getObject(5),
                    contentInfo.getObject(6),
                    contentInfo.getObject(7),
                    contentInfo.getObject(8),
                    contentInfo.getObject(9),
                    contentInfo.getObject(10),
                    contentInfo.getObject(11),
                    contentInfo.getObject(12),
                    contentInfo.getObject(13),
                    0
                });

                // The rest of the content information will be migrated in one large batch operation
                // in the migrateRelatedData method.

                // If we've collected a full "block" of content data, migrate the block
                if (contentRows.size() > maxrows) {
                    // Impl note: By some miracle, this doesn't close the outer result set.
                    this.bulkInsertContentData(contentRows);
                    contentRows.clear();
                }

                this.migratedContent.put(contentId, contentUuid);
            }

            uuidCache.add(contentUuid);
        }

        contentInfo.close();

        // Do a bulk insert of any remaining unmigrated content we've encountered
        this.bulkInsertContentData(contentRows);
        contentRows.clear();

        // Do a bulk insert for all the content for this orgs...
        if (uuidCache.size() > 0) {
            maxrows = MAX_PARAMETERS_PER_STATEMENT / 2;

            int lastBlock = 0;
            int blockSize = maxrows / 2;
            Iterator<String> uuidIterator = uuidCache.iterator();
            PreparedStatement statement = null;

            for (int offset = 0; offset < uuidCache.size(); offset += blockSize) {
                int remaining = Math.min(uuidCache.size() - offset, blockSize);
                if (remaining != lastBlock) {
                    if (statement != null) {
                        statement.close();
                    }

                    statement = this.generateBulkInsertStatement(
                        "cp2_owner_content", remaining, "owner_id", "content_uuid"
                    );

                    lastBlock = remaining;
                }

                int index = 0;
                while (remaining-- > 0) {
                    this.setParameter(statement, ++index, orgid);
                    this.setParameter(statement, ++index, uuidIterator.next());
                }

                int count = statement.executeUpdate();
                if (count != uuidCache.size()) {
                    String errmsg = String.format(
                        "Wrong number of contents assigned to org: %s. Expected: %s, Inserted: %s",
                        orgid, uuidCache.size(), count
                    );

                    this.logger.error(errmsg);
                    throw new DatabaseException(errmsg);
                }
            }

            this.logger.info("  Assigned %d contents to org", uuidCache.size());
            statement.close();
        }
    }

    /**
     * Migrates data linked to objects we have already migrated
     */
    protected void migrateRelatedData() throws DatabaseException, SQLException {
        this.logger.info("Migrating activation keys...");

        this.executeUpdate(
            "INSERT INTO cp2_activation_key_products(key_id, product_uuid) " +
            "SELECT akp.key_id, p.uuid " +
            "FROM cp_activationkey_product akp " +
            "JOIN cp2_products p ON akp.product_id = p.product_id"
        );


        this.logger.info("Migrating linked product data...");

        this.executeUpdate(
            "INSERT INTO cp2_product_attributes (id, created, updated, name, value, product_uuid) " +
            "SELECT pa.id, pa.created, pa.updated, pa.name, pa.value, p.uuid " +
            "FROM cp_product_attribute pa " +
            "JOIN cp2_products p ON pa.product_id = p.product_id"
        );

        this.executeUpdate(
            "INSERT INTO cp2_product_content (product_uuid, content_uuid, enabled, created, updated) " +
            "SELECT p.uuid, c.uuid, pc.enabled, pc.created, pc.updated " +
            "FROM cp_product_content pc " +
            "JOIN cp2_products p ON p.product_id = pc.product_id " +
            "JOIN cp2_content c ON c.content_id = pc.content_id "
        );

        this.executeUpdate(
            "INSERT INTO cp2_product_certificates (id, created, updated, cert, privatekey, product_uuid) " +
            "SELECT pc.id, pc.created, pc.updated, pc.cert, pc.privatekey, p.uuid " +
            "FROM cp_product_certificate pc " +
            "JOIN cp2_products p ON pc.product_id = p.product_id"
        );

        this.executeUpdate(
            "INSERT INTO cp2_product_dependent_products (product_uuid, element) " +
            "SELECT p.uuid, pdp.element " +
            "FROM cp_product_dependent_products pdp " +
            "JOIN cp2_products p ON pdp.cp_product_id = p.product_id"
        );

        this.executeUpdate(
            "INSERT INTO cp2_pool_provided_products (pool_id, product_uuid) " +
            "SELECT pool.id, prod.uuid " +
            "FROM cp_pool pool " +
            "JOIN cp_pool_products pp ON pool.id = pp.pool_id " +
            "JOIN cp2_products prod ON pp.product_id = prod.product_id " +
            "WHERE pp.dtype = 'provided'"
        );

        this.executeUpdate(
            "INSERT INTO cp2_pool_derprov_products " +
            "SELECT pool.id, prod.uuid " +
            "FROM cp_pool pool " +
            "JOIN cp_pool_products pp ON pool.id = pp.pool_id " +
            "JOIN cp2_products prod ON pp.product_id = prod.product_id " +
            "WHERE pp.dtype = 'derived'"
        );


        this.logger.info("Migrating linked content data...");

        this.executeUpdate(
            "INSERT INTO cp2_environment_content " +
            "  (id, created, updated, content_uuid, enabled, environment_id) " +
            "SELECT ec.id, ec.created, ec.updated, c.uuid, ec.enabled, ec.environment_id " +
            "FROM cp_env_content ec " +
            "JOIN cp2_content c ON ec.contentid = c.content_id"
        );

        this.executeUpdate(
            "INSERT INTO cp2_content_modified_products (content_uuid, element) " +
            "SELECT c.uuid, cmp.element " +
            "FROM cp_content_modified_products cmp " +
            "JOIN cp2_content c ON cmp.cp_content_id = c.content_id"
        );


        this.logger.info("Migrating global pool data...");

        this.executeUpdate(
            "UPDATE cp_pool p SET product_uuid = " +
            "  (SELECT prod.uuid FROM cp2_products prod WHERE prod.product_id = p.product_id_old), " +
            "derived_product_uuid = " +
            "  (SELECT prod.uuid FROM cp2_products prod WHERE prod.product_id = p.derived_product_id_old)"
        );

        this.executeUpdate(
            "INSERT INTO cp2_pool_source_sub " +
            "  (id, subscription_id, subscription_sub_key, pool_id, created, updated) " +
            "SELECT id, subscriptionid, subscriptionsubkey, pool_id, created, updated " +
            "FROM cp_pool_source_sub "
        );

        // Migrate upstream tracking columns from subscription to primary pool
        ResultSet subscriptionInfo = this.executeQuery(
            "SELECT ss.pool_id, s.cdn_id, s.certificate_id, s.upstream_entitlement_id, " +
            "  s.upstream_consumer_id, s.upstream_pool_id " +
            "FROM cp_subscription s " +
            "JOIN cp2_pool_source_sub ss ON s.id = ss.subscription_id " +
            "WHERE ss.subscription_sub_key = ?", PRIMARY_POOL_SUB_KEY
        );

        // Update any pool referencing this subscription...
        while (subscriptionInfo.next()) {
            String poolId = subscriptionInfo.getString(1);
            String cdnId = subscriptionInfo.getString(2);
            String certId = subscriptionInfo.getString(3);
            String upstreamEntitlementId = subscriptionInfo.getString(4);
            String upstreamConsumerId = subscriptionInfo.getString(5);
            String upstreamPoolId = subscriptionInfo.getString(6);

            int updated = this.executeUpdate(
                "UPDATE cp_pool SET cdn_id=?, certificate_id=?, upstream_entitlement_id=?, " +
                "  upstream_consumer_id=?, upstream_pool_id=? " +
                "WHERE id=?",
                cdnId, certId, upstreamEntitlementId, upstreamConsumerId, upstreamPoolId, poolId
            );
        }

        subscriptionInfo.close();
    }
}

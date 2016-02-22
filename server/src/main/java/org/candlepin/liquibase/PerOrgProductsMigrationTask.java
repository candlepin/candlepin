/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import liquibase.database.Database;
import liquibase.exception.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * The PerOrgProductsMigrationTask performs the post-db upgrade data migration to the cp2_* tables.
 */
public class PerOrgProductsMigrationTask extends LiquibaseCustomTask {

    protected Map<String, String> migratedProducts;
    protected Map<String, String> migratedContent;
    private Timestamp migrationTime;

    public PerOrgProductsMigrationTask(Database database, CustomTaskLogger logger) {
        super(database, logger);

        this.migratedProducts = new HashMap<String, String>();
        this.migratedContent = new HashMap<String, String>();
        this.migrationTime = new Timestamp(System.currentTimeMillis());
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

            if (!this.database.getDatabaseProductName().matches(".*(?i:oracle).*")) {
                rowbuilder = new StringBuilder(2 + cols.length << 1);

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
            }
            else {
                rowbuilder = new StringBuilder(20 * cols.length + table.length());

                rowbuilder.append("INTO ").append(table).append('(');
                for (String column : cols) {
                    rowbuilder.append(column).append(',');
                }
                rowbuilder.deleteCharAt(rowbuilder.length() - 1).append(") VALUES(");
                for (int i = 0; i < cols.length; ++i) {
                    rowbuilder.append("?,");
                }
                rowbuilder.deleteCharAt(rowbuilder.length() - 1).append(") ");

                builder = new StringBuilder(30 + (20 * cols.length + table.length()) * rows);
                builder.append("INSERT ALL ");

                for (int i = 0; i < rows; ++i) {
                    builder.append(rowbuilder);
                }

                builder.append("SELECT 1 FROM DUAL");
            }

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

            // Migrate orgs
            ResultSet result = this.executeQuery("SELECT count(id) FROM cp_owner");
            result.next();
            int count = result.getInt(1);
            result.close();

            ResultSet orgids = this.executeQuery("SELECT id, account FROM cp_owner");
            for (int index = 1; orgids.next(); ++index) {
                String orgid = orgids.getString(1);
                String account = orgids.getString(2);

                this.logger.info(
                    "Migrating data for org %s (%s) (%d of %d)", account, orgid, index, count
                );

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
     * Migrates product data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateProductData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating product data...");

        List<String> pidCache = new LinkedList<String>();
        ResultSet productids = this.executeQuery(
            "SELECT p.product_id_old AS product_id " +
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "    AND NOT NULLIF(p.product_id_old, '') IS NULL " +
            "UNION " +
            "SELECT p.derived_product_id_old " +
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "    AND NOT NULLIF(p.derived_product_id_old, '') IS NULL " +
            "UNION " +
            "SELECT pp.product_id " +
            "  FROM cp_pool p " +
            "  JOIN cp_pool_products pp " +
            "    ON p.id = pp.pool_id " +
            "  WHERE p.owner_id = ? " +
            "    AND NOT NULLIF(pp.product_id, '') IS NULL " +
            "UNION " +
            "SELECT s.product_id " +
            "  FROM cp_subscription s " +
            "  WHERE s.owner_id = ? " +
            "    AND NOT NULLIF(s.product_id, '') IS NULL " +
            "UNION " +
            "SELECT s.derivedproduct_id " +
            "  FROM cp_subscription s " +
            "  WHERE s.owner_id = ? " +
            "    AND NOT NULLIF(s.derivedproduct_id, '') IS NULL " +
            "UNION " +
            "SELECT sp.product_id " +
            "  FROM cp_subscription_products sp " +
            "  JOIN cp_subscription s " +
            "    ON s.id = sp.subscription_id " +
            "  WHERE s.owner_id = ? " +
            "    AND NOT NULLIF(sp.product_id, '') IS NULL " +
            "UNION " +
            "SELECT sdp.product_id " +
            "  FROM cp_sub_derivedprods sdp " +
            "  JOIN cp_subscription s " +
            "    ON s.id = sdp.subscription_id " +
            "  WHERE s.owner_id = ? " +
            "    AND NOT NULLIF(sdp.product_id, '') IS NULL",
            orgid, orgid, orgid, orgid, orgid, orgid, orgid
        );

        while (productids.next()) {
            String productid = productids.getString(1);
            String productuuid = this.migratedProducts.get(productid);

            if (productuuid != null) {
                // TODO: This likely isn't a warn condition anymore
                this.logger.warn(String.format(
                    "Skipping migration for already-migrated product: %s", productid
                ));

                continue;
            }
            else {
                this.logger.info(String.format("    Migrating product: %s", productid));

                productuuid = this.generateUUID();

                int count = this.executeUpdate(
                    "INSERT INTO cp2_products " +
                    "  (uuid, created, updated, updated_upstream, previous_version, multiplier, " +
                    "  product_id, name, locked) " +
                    "SELECT ?, created, updated, ?, null, multiplier, ?, name, 0 " +
                    "FROM cp_product p " +
                    "WHERE p.id = ?",
                    productuuid, this.migrationTime, productid, productid
                );

                if (count < 1) {
                    this.logger.error(String.format(
                        "    Product referenced by org which does not exist: %s", productid
                    ));

                    continue;
                }
                else if (count > 1) {
                    this.logger.error(String.format(
                        "    Product migration query resulted in multiple products for id: %s", productid
                    ));
                }

                // The rest of the product information will be migrated in one large batch operation
                // in the migrateRelatedData method.

                this.migratedProducts.put(productid, productuuid);
            }

            pidCache.add(productuuid);
        }

        productids.close();

        // Do a bulk insert for all the products for this orgs...
        if (pidCache.size() > 0) {
            PreparedStatement statement = this.generateBulkInsertStatement(
                "cp2_owner_products", pidCache.size(), "owner_id", "product_uuid"
            );
            int index = 0;

            for (String pid : pidCache) {
                this.setParameter(statement, ++index, orgid);
                this.setParameter(statement, ++index, pid);
            }

            statement.executeUpdate();
        }
    }

    /**
     * Migrates content data.
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateContentData(String orgid)
        throws DatabaseException, SQLException {

        // Impl note: This query is only safe because in the 09x era, content and product IDs are
        // expected to be unique.

        List<String> cidCache = new LinkedList<String>();
        ResultSet contentids = this.executeQuery(
            "SELECT c.id, c.created, c.updated, c.contenturl, c.gpgurl, c.label, " +
            "  c.metadataexpire, c.name, c.releasever, c.requiredtags, c.type, c.vendor, c.arches " +
            "FROM cp_content c " +
            "JOIN cp_product_content pc ON pc.content_id = c.id " +
            "JOIN cp2_products p ON pc.product_id = p.product_id " +
            "JOIN cp2_owner_products op ON p.uuid = op.product_uuid " +
            "WHERE op.owner_id = ?",
            orgid
        );

        while (contentids.next()) {
            String contentid = contentids.getString(1);
            String contentuuid = this.migratedContent.get(contentid);

            if (contentuuid != null) {
                // TODO: This likely isn't a warn condition anymore
                this.logger.warn(String.format(
                    "Skipping migration for already-migrated content: %s", contentid
                ));

                continue;
            }
            else {
                this.logger.info(String.format("    Migrating content: %s", contentid));

                contentuuid = this.generateUUID();

                // Fetch current row...
                Object[] row = new Object[13];

                for (int i = 0; i < row.length; ++i) {
                    row[i] = contentids.getObject(i + 1);
                }

                int count = this.executeUpdate(
                    "INSERT INTO cp2_content " +
                    "  (uuid, content_id, created, updated, updated_upstream, previous_version, " +
                    "  contenturl, gpgurl, label, metadataexpire, name, releasever, requiredtags, " +
                    "  type, vendor, arches) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    contentuuid, row[0], row[1], row[2], this.migrationTime, null, row[3], row[4],
                    row[5], row[6], row[7], row[8], row[9], row[10], row[11], row[12]
                );

                if (count < 1) {
                    this.logger.error(String.format(
                        "    Content referenced by product which does not exist: %s", contentid
                    ));

                    continue;
                }
                else if (count > 1) {
                    this.logger.error(String.format(
                        "    Content migration query resulted in multiple contents for id: %s", contentid
                    ));
                }

                // The rest of the content information will be migrated in one large batch operation
                // in the migrateRelatedData method.

                this.migratedContent.put(contentid, contentuuid);
            }

            cidCache.add(contentuuid);
        }

        contentids.close();

        // Do a bulk insert for all the content for this orgs...
        if (cidCache.size() > 0) {
            PreparedStatement statement = this.generateBulkInsertStatement(
                "cp2_owner_content", cidCache.size(), "owner_id", "content_uuid"
            );
            int index = 0;

            for (String cid : cidCache) {
                this.setParameter(statement, ++index, orgid);
                this.setParameter(statement, ++index, cid);
            }

            statement.executeUpdate();
        }
    }

    /**
     * Migrates data linked to objects we have already migrated
     */
    protected void migrateRelatedData() throws DatabaseException, SQLException {
        this.logger.info("Migrating activation keys...");

        this.executeUpdate(
            "INSERT INTO cp2_activation_key_products(key_id, product_uuid) " +
            "SELECT akp.id, p.uuid " +
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
            "INSERT INTO cp2_pool_dprovided_products " +
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
            "INSERT INTO cp2_pool_source_sub " +
            "  (id, subscription_id, subscription_sub_key, pool_id, created, updated) " +
            "SELECT id, subscriptionid, subscriptionsubkey, pool_id, created, updated " +
            "FROM cp_pool_source_sub "
        );
    }
}

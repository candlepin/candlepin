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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * The PerOrgProductsMigrationTask performs the post-db upgrade data migration to the cp2_* tables.
 */
public class PerOrgProductsMigrationTask extends LiquibaseCustomTask {

    protected Map<String, String> migratedProducts;
    private Date migrationTime;

    public PerOrgProductsMigrationTask(Database database, CustomTaskLogger logger) {
        super(database, logger);

        this.migratedProducts = new HashMap<String, String>();
        this.migrationTime = new Date();
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

            // Migrate content
            // this.migrateContent();

            // Migrate product data
            // this.migrateProductData();

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


                this.migrateActivationKeyData(orgid);
                this.migratePoolData(orgid);
            }

            this.migrateGlobalProductData();

            orgids.close();
            this.connection.commit();
        }
        finally {
            // Restore original autocommit state
            this.connection.setAutoCommit(autocommit);
        }
    }

    /**
     * Retrieves the products from the DB and stores them to be used later
     */
    protected void prefetchProducts() throws DatabaseException, SQLException {
        ResultSet prodQuery = this.executeQuery(
            "SELECT created, updated, multiplier, id, name FROM cp_product"
        );

        while (prodQuery.next()) {
            Map<String, Object> product = new HashMap<String, Object>();
            String productid = prodQuery.getString(4);

            product.put("info", new Object[] {
                "uuid_placeholder", prodQuery.getTimestamp(1), prodQuery.getTimestamp(2),
                prodQuery.getObject(3), "ownerid placeholder", prodQuery.getString(4),
                prodQuery.getString(5)
            });

            // Fetch attributes
            ResultSet attrQuery = this.executeQuery(
                "SELECT created, updated, name, value " +
                "FROM cp_product_attribute " +
                "WHERE product_id = ?",
                productid
            );

            List<Object[]> attributes = new LinkedList<Object[]>();
            while (attrQuery.next()) {
                attributes.add(new Object[] {
                    "id placeholder", attrQuery.getTimestamp(1), attrQuery.getTimestamp(2),
                    attrQuery.getString(3), attrQuery.getString(4), "product uuid placeholder"
                });
            }

            attrQuery.close();
            product.put("attributes", attributes);
            product.put("attributes_statement", this.generateBulkInsertStatement(
                "cp2_product_attributes", attributes.size(),
                "id", "created", "updated", "name", "value", "product_uuid"
            ));

            // Fetch certificates
            ResultSet certQuery = this.executeQuery(
                "SELECT created, updated, cert, privatekey " +
                "FROM cp_product_certificate " +
                "WHERE product_id = ?",
                productid
            );

            List<Object[]> certificates = new LinkedList<Object[]>();
            while (certQuery.next()) {
                certificates.add(new Object[] {
                    "id placeholder", certQuery.getTimestamp(1), certQuery.getTimestamp(2),
                    certQuery.getBytes(3), certQuery.getBytes(4), "product uuid placeholder"
                });
            }

            certQuery.close();
            product.put("certificates", certificates);
            product.put("certificates_statement", this.generateBulkInsertStatement(
                "cp2_product_certificates", certificates.size(),
                "id", "created", "updated", "cert", "privatekey", "product_uuid"
            ));

            // Fetch linked content
            ResultSet contentQuery = this.executeQuery(
                "SELECT " +
                "  content_id, enabled, created, updated " +
                "FROM cp_product_content " +
                "WHERE product_id = ?",
                productid
            );

            List<Object[]> content = new LinkedList<Object[]>();
            while (contentQuery.next()) {
                content.add(new Object[] {
                    "product uuid placeholder", contentQuery.getString(1),
                    contentQuery.getBoolean(2), contentQuery.getTimestamp(3),
                    contentQuery.getTimestamp(4)
                });
            }

            contentQuery.close();
            product.put("content", content);
            product.put("content_statement", this.generateBulkInsertStatement(
                "cp2_product_content", content.size(),
                "product_uuid", "content_uuid", "enabled", "created", "updated"
            ));

            // Fetch dependent products
            ResultSet dprodQuery = this.executeQuery(
                "SELECT element FROM cp_product_dependent_products WHERE cp_product_id = ?", productid
            );

            List<Object[]> dependents = new LinkedList<Object[]>();
            while (dprodQuery.next()) {
                dependents.add(new Object[] { "product uuid placeholder", dprodQuery.getString(1) });
            }

            dprodQuery.close();
            product.put("dependents", dependents);
            product.put("dependents_statement", this.generateBulkInsertStatement(
                "cp2_dependent_products", dependents.size(),
                "product_uuid", "element"
            ));

            this.productCache.put(productid, product);
        }

        prodQuery.close();
    }

    /**
     * Retrieves the content from the DB and stores them to be used later
     */
    protected void prefetchContent() throws DatabaseException, SQLException {
        ResultSet contentQuery = this.executeQuery(
            "SELECT " +
            "  id, created, updated, contenturl, gpgurl, label, metadataexpire, name, " +
            "  releasever, requiredtags, type, vendor, arches " +
            "FROM cp_content"
        );

        while (contentQuery.next()) {
            Map<String, Object> content = new HashMap<String, Object>();
            String contentid = contentQuery.getString(1);

            content.put("info", new Object[] {
                "content uuid placeholder", contentid, contentQuery.getTimestamp(2),
                contentQuery.getTimestamp(3), "owner id placeholder", contentQuery.getString(4),
                contentQuery.getString(5), contentQuery.getString(6), contentQuery.getObject(7),
                contentQuery.getString(8), contentQuery.getString(9), contentQuery.getString(10),
                contentQuery.getString(11), contentQuery.getString(12), contentQuery.getString(13)
            });

            // Fetch modified products...
            ResultSet prodQuery = this.executeQuery(
                "SELECT element FROM cp_content_modified_products WHERE cp_content_id = ?", contentid
            );

            List<Object[]> products = new LinkedList<Object[]>();
            while (prodQuery.next()) {
                products.add(new Object[] { "content uuid placeholder", prodQuery.getString(1) });
            }

            prodQuery.close();
            content.put("products", products);
            content.put("products_statement", this.generateBulkInsertStatement(
                "cp2_content_modified_products", products.size(),
                "content_uuid", "element"
            ));

            // Fetch environment content...
            ResultSet ecQuery = this.executeQuery(
                "SELECT created, updated, enabled, environment_id " +
                "FROM cp_env_content " +
                "WHERE contentid = ?",
                contentid
            );

            List<Object[]> envcontent = new LinkedList<Object[]>();
            while (ecQuery.next()) {
                envcontent.add(new Object[] {
                    "id placeholder", ecQuery.getTimestamp(1), ecQuery.getTimestamp(2),
                    "content uuid placeholder", ecQuery.getString(3), ecQuery.getBoolean(4)
                });
            }

            ecQuery.close();
            content.put("envcontent", envcontent);
            content.put("envcontent_statement", this.generateBulkInsertStatement(
                "cp2_environment_content", envcontent.size(),
                "id", "created", "updated", "content_uuid", "environment_id", "enabled"
            ));

            this.contentCache.put(contentid, content);
        }

        contentQuery.close();
    }

    /**
     * Migrates content data.
     *
     * @param contentid
     *  The id of the content to migrate
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     *
     * @return
     *  The UUID for the newly migrated content
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected String migrateContentData(String contentid, String orgid)
        throws DatabaseException, SQLException {

        String contentuuid = this.generateUUID();
        Map<String, Object> content = this.contentCache.get(contentid);

        if (content == null) {
            return null;
        }

        Object[] info = (Object[]) content.get("info");
        info[0] = contentuuid;
        info[4] = orgid;

        this.executeUpdate(
            "INSERT INTO cp2_content " +
            "  (uuid, content_id, created, updated, owner_id, contenturl, gpgurl, label, " +
            "  metadataexpire, name, releasever, requiredtags, type, vendor, arches) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            info
        );

        // Migrate environment content...
        PreparedStatement statement = (PreparedStatement) content.get("envcontent_statement");
        int index = 0;

        if (statement != null) {
            statement.clearParameters();
            for (Object[] params : (List<Object[]>) content.get("envcontent")) {
                this.setParameter(statement, ++index, this.generateUUID());
                this.setParameter(statement, ++index, params[1]);
                this.setParameter(statement, ++index, params[2]);
                this.setParameter(statement, ++index, contentuuid);
                this.setParameter(statement, ++index, params[4]);
                this.setParameter(statement, ++index, params[5]);
            }
            statement.executeUpdate();
        }

        // Migrate modified products
        statement = (PreparedStatement) content.get("products_statement");
        index = 0;

        if (statement != null) {
            statement.clearParameters();
            for (Object[] params : (List<Object[]>) content.get("products")) {
                this.setParameter(statement, ++index, contentuuid);
                this.setParameter(statement, ++index, params[1]);
            }
            statement.executeUpdate();
        }

        return contentuuid;
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

        ResultSet productids = this.executeQuery(
            "SELECT p.productid AS product_id " +
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "    AND NOT NULLIF(p.productid, '') IS NULL " +
            "UNION " +
            "SELECT p.derivedproductid " +
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "    AND NOT NULLIF(p.derivedproductid, '') IS NULL " +
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

            if (this.productsMigrated.get(productid) != null) {
                this.logger.warn(String.format(
                    "    Skipping migration for already-migrated product: %s", productid
                ));

                continue;
            }
            else {
                this.logger.info(String.format("    Migrating product: %s", productid));

                String productuuid = this.generateUUID();

                int count = this.executeUpdate(
                    "INSERT INTO cp2_products " +
                    "  (uuid, created, updated, updated_upstream, multiplier, product_id, name) " +
                    "SELECT ?, created, updated, ?, multiplier, ?, name " +
                    "FROM cp_product " +
                    "WHERE product_id = ?",
                    productuuid, this.migrationDate, productid
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

            // Migrate product attributes
            PreparedStatement statement = (PreparedStatement) product.get("attributes_statement");
            int index = 0;

            if (statement != null) {
                statement.clearParameters();
                for (Object[] params : (List<Object[]>) product.get("attributes")) {
                    this.setParameter(statement, ++index, this.generateUUID());
                    this.setParameter(statement, ++index, params[1]);
                    this.setParameter(statement, ++index, params[2]);
                    this.setParameter(statement, ++index, params[3]);
                    this.setParameter(statement, ++index, params[4]);
                    this.setParameter(statement, ++index, productuuid);
                }
                statement.executeUpdate();
            }

            // Migrate product certificates
            statement = (PreparedStatement) product.get("certificates_statement");
            index = 0;

            if (statement != null) {
                statement.clearParameters();
                for (Object[] params : (List<Object[]>) product.get("certificates")) {
                    this.setParameter(statement, ++index, this.generateUUID());
                    this.setParameter(statement, ++index, params[1]);
                    this.setParameter(statement, ++index, params[2]);
                    this.setParameter(statement, ++index, params[3]);
                    this.setParameter(statement, ++index, params[4]);
                    this.setParameter(statement, ++index, productuuid);
                }
                statement.executeUpdate();
            }

            // Migrate content used by product
            statement = (PreparedStatement) product.get("content_statement");
            index = 0;

            if (statement != null) {
                statement.clearParameters();
                for (Object[] params : (List<Object[]>) product.get("content")) {
                    String contentuuid = contentMigrated.get((String) params[1]);

                    if (contentuuid == null) {
                        contentuuid = this.migrateContentData((String) params[1], orgid);

                        if (contentuuid == null) {
                            this.logger.error(
                                "      Content referenced by product which does not exist " +
                                "(product: %s, content: %s)",
                                productid, params[1]
                            );

                            continue;
                        }
                    }

                    this.setParameter(statement, ++index, productuuid);
                    this.setParameter(statement, ++index, contentuuid);
                    this.setParameter(statement, ++index, params[2]);
                    this.setParameter(statement, ++index, params[3]);
                    this.setParameter(statement, ++index, params[4]);

                    contentMigrated.put((String) params[1], contentuuid);
                }
                statement.executeUpdate();
            }

            // Migrate dependent products
            statement = (PreparedStatement) product.get("dependents_statement");
            index = 0;

            if (statement != null) {
                statement.clearParameters();
                for (Object[] params : (List<Object[]>) product.get("dependents")) {
                    this.setParameter(statement, ++index, productuuid);
                    this.setParameter(statement, ++index, params[1]);
                }
                statement.executeUpdate();
            }
        }

        productids.close();
    }

    private void migrateGlobalProductData() throws DatabaseException, SQLException {
        this.logger.info("Migrating global product data...");

        this.executeUpdate(
            "INSERT INTO cp2_pool_provided_products " +
            "SELECT pool.id, prod.uuid " +
            "FROM cp_pool pool " +
            "INNER JOIN cp_pool_products pp ON pool.id = pp.pool_id " +
            "INNER JOIN cp2_products prod ON " +
            "  (pp.product_id = prod.product_id AND pool.owner_id = prod.owner_id) " +
            "WHERE pp.dtype = 'provided'"
        );

        this.executeUpdate(
            "INSERT INTO cp2_pool_derived_products " +
            "SELECT pool.id, prod.uuid " +
            "FROM cp_pool pool " +
            "INNER JOIN cp_pool_products pp ON pool.id = pp.pool_id " +
            "INNER JOIN cp2_products prod ON " +
            "  (pp.product_id = prod.product_id AND pool.owner_id = prod.owner_id) " +
            "WHERE pp.dtype = 'derived'"
        );
    }

    /**
     * Migrates activation key data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate activation key data
     */
    private void migrateActivationKeyData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating activation key data...");

        this.executeUpdate(
            "INSERT INTO cp2_activation_key_products(key_id, product_uuid) " +
            "SELECT AK.id, (SELECT uuid FROM cp2_products " +
            "  WHERE owner_id = ? AND product_id = AKP.product_id) " +
            "FROM cp_activation_key AK " +
            "  JOIN cp_activationkey_product AKP ON AKP.key_id = AK.id " +
            "WHERE AK.owner_id = ?",
            orgid, orgid
        );
    }

    /**
     * Migrates pool data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate pool data
     */
    private void migratePoolData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating pool data...");

        ResultSet pools = this.executeQuery("SELECT id FROM cp_pool WHERE owner_id = ?", orgid);

        while (pools.next()) {
            String poolid = pools.getString(1);

            // Migrate pool source subscription info
            ResultSet sourcesub = this.executeQuery(
                "SELECT id, subscriptionid, subscriptionsubkey, pool_id, created, updated " +
                "FROM cp_pool_source_sub WHERE pool_id = ?",
                poolid
            );

            while (sourcesub.next()) {
                this.executeUpdate(
                    "INSERT INTO cp2_pool_source_sub " +
                    "  (id, subscription_id, subscription_sub_key, pool_id, created, updated)" +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), sourcesub.getString(2), sourcesub.getString(3), poolid,
                    sourcesub.getTimestamp(5), sourcesub.getTimestamp(6)
                );
            }

            sourcesub.close();
        }

        pools.close();
    }

}

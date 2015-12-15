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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * The PerOrgProductsUpgradeTaskA performs the post-db upgrade data migration to the cpo_* tables.
 * This task does not perform any actions that would modify the cp_* tables.
 */
public class PerOrgProductsUpgradeTaskA extends LiquibaseCustomTask {


    private Map<String, Map<String, Object>> productCache;
    private Map<String, Map<String, Object>> contentCache;


    public PerOrgProductsUpgradeTaskA(Database database, CustomTaskLogger logger) {
        super(database, logger);

        this.productCache = new HashMap<String, Map<String, Object>>();
        this.contentCache = new HashMap<String, Map<String, Object>>();
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

            // Prefetch products and content...
            this.prefetchProducts();
            this.prefetchContent();

            // Get org count
            ResultSet result = this.executeQuery("SELECT count(id) FROM cp_owner");
            result.next();
            int count = result.getInt(1);
            result.close();

            // Migrate orgs
            ResultSet orgids = this.executeQuery("SELECT id, account FROM cp_owner");
            for (int index = 1; orgids.next(); ++index) {
                String orgid = orgids.getString(1);
                String account = orgids.getString(2);

                this.logger.info(
                    "Migrating data for org %s (%s) (%d of %d)", account, orgid, index, count
                );

                this.migrateProductData(orgid);
                this.migrateActivationKeyData(orgid);
                this.migratePoolData(orgid);
            }

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

            this.contentCache.put(contentid, content);
        }

        contentQuery.close();
    }

    /**
     * Retrieves a result set to pull product IDs for a given org
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    protected ResultSet getProductIds(String orgid) throws DatabaseException, SQLException {
        return this.executeQuery(
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
    protected String migrateContentData(String contentid, String orgid) throws DatabaseException, SQLException {
        String contentuuid = this.generateUUID();
        Map<String, Object> content = this.contentCache.get(contentid);

        if (content == null) {
            return null;
        }

        Object[] info = (Object[]) content.get("info");
        info[0] = contentuuid;
        info[4] = orgid;

        this.executeUpdate(
            "INSERT INTO cpo_content " +
            "  (uuid, content_id, created, updated, owner_id, contenturl, gpgurl, label, " +
            "  metadataexpire, name, releasever, requiredtags, type, vendor, arches) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            info
        );

        // Migrate environment content...
        List<Object[]> envcontent = (List<Object[]>) content.get("envcontent");

        // TODO:
        // Convert this to a bulk insert. Oracle makes this suck (naturally), but if detection is
        // feasible, it should be doable.
        for (Object[] params : envcontent) {
            params[0] = this.generateUUID();
            params[3] = contentuuid;

            this.executeUpdate(
                "INSERT INTO cpo_environment_content " +
                "  (id, created, updated, content_uuid, environment_id, enabled) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                params
            );
        }

        // Migrate modified products
        // Note: For some reason, these are actual product IDs, not UUIDs.
        // TODO: This should also be a bulk insert
        for (Object[] params : (List<Object[]>) content.get("products")) {
            params[0] = contentuuid;

            this.executeUpdate(
                "INSERT INTO cpo_content_modified_products (content_uuid, element) VALUES (?, ?)",
                params
            );
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
        Map<String, String> productsMigrated = new HashMap<String, String>();
        Map<String, String> contentMigrated = new HashMap<String, String>();
        int migrated = 0;
        int updated = 0;

        this.logger.info("  Migrating product data...");

        ResultSet productids = this.getProductIds(orgid);
        while (productids.next()) {
            String productid = productids.getString(1);

            if (productsMigrated.get(productid) != null) {
                this.logger.warn(String.format(
                    "    Skipping migration for already-migrated product: %s", productid
                ));

                continue;
            }

            this.logger.info(String.format("    Migrating product: %s", productid));

            Map<String, Object> product = this.productCache.get(productid);
            String productuuid = this.generateUUID();

            if (product == null) {
                this.logger.error(String.format(
                    "    Product referenced by org which does not exist: %s", productid
                ));

                continue;
            }

            // Insert product info
            Object[] info = (Object[]) product.get("info");
            info[0] = productuuid;
            info[4] = orgid;

            this.executeUpdate(
                "INSERT INTO cpo_products " +
                "  (uuid, created, updated, multiplier, owner_id, product_id, name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                info
            );

            // Migrate product attributes
            List<Object[]> attributes = (List<Object[]>) product.get("attributes");

            for (Object[] params : attributes) {
                params[0] = this.generateUUID();
                params[5] = productuuid;

                // TODO: Convert this to a bulk insert
                this.executeUpdate(
                    "INSERT INTO cpo_product_attributes " +
                    "  (id, created, updated, name, value, product_uuid) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    params
                );
            }

            // Migrate product certificates
            List<Object[]> certificates = (List<Object[]>) product.get("certificates");

            for (Object[] params : certificates) {
                params[0] = this.generateUUID();
                params[5] = productuuid;

                // TODO: Another bulk insert here
                this.executeUpdate(
                    "INSERT INTO cpo_product_certificates " +
                    "  (id, created, updated, cert, privatekey, product_uuid) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    params
                );
            }

            // Migrate content used by product
            List<Object[]> content = (List<Object[]>) product.get("content");

            for (Object[] params : content) {
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

                // TODO: Yet another bulk insert here
                this.executeUpdate(
                    "INSERT INTO cpo_product_content " +
                    "  (product_uuid, content_uuid, enabled, created, updated) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    productuuid, contentuuid, params[2], params[3], params[4]
                );

                contentMigrated.put((String) params[1], contentuuid);
            }

            // Migrate dependent products
            List<Object[]> dependents = (List<Object[]>) product.get("dependents");

            for (Object[] params : dependents) {
                params[0] = productuuid;

                // TODO: And another bulk insert
                this.executeUpdate(
                    "INSERT INTO cpo_product_dependent_products " +
                    "  (product_uuid, element) " +
                    "VALUES (?, ?)",
                    params
                );
            }

            // These two are particularly painful, as we don't have a pool id available, so we're
            // almost forced to do insert-selects
            this.executeUpdate(
                "INSERT INTO cpo_pool_provided_products " +
                "SELECT pool_id, ? " +
                "FROM cp_pool_products pp, cp_pool p WHERE pp.pool_id = p.id " +
                "    AND p.owner_id = ? AND pp.product_id = ? AND pp.dtype = 'provided' ",
                productuuid, orgid, productid
            );

            this.executeUpdate(
                "INSERT INTO cpo_pool_derived_products " +
                "SELECT pool_id, ? " +
                "FROM cp_pool_products pp, cp_pool p WHERE pp.pool_id = p.id " +
                "     AND p.owner_id = ? AND pp.product_id = ? AND pp.dtype = 'derived' ",
                productuuid, orgid, productid
            );
        }

        productids.close();
        this.logger.info(String.format("  Done. %d products migrated, %d updated", migrated, updated));
    }

    /**
     * Migrates activation key data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate activation key data
     */
    private void migrateActivationKeyData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("Migrating activation key data for org " + orgid);

        this.executeUpdate(
            "INSERT INTO cpo_activation_key_products(key_id, product_uuid) " +
            "SELECT AK.id, (SELECT uuid FROM cpo_products " +
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
        this.logger.info("Migrating pool data for org " + orgid);

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
                    "INSERT INTO cpo_pool_source_sub " +
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

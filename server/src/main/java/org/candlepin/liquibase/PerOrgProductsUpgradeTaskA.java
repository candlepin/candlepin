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
import java.util.Map;



/**
 * The PerOrgProductsUpgradeTaskA performs the post-db upgrade data migration to the cpo_* tables.
 * This task does not perform any actions that would modify the cp_* tables.
 */
public class PerOrgProductsUpgradeTaskA extends LiquibaseCustomTask {

    public PerOrgProductsUpgradeTaskA(Database database) {
        super(database);
    }

    public PerOrgProductsUpgradeTaskA(Database database, CustomTaskLogger logger) {
        super(database, logger);
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

                this.logger.info(String.format(
                    "Migrating data for org %s (%s) (%d of %d)",
                    account, orgid, index, count
                ));

                this.migrateProductData(orgid);
                this.migrateContentData(orgid);
                this.migrateActivationKeyData(orgid);
                this.migratePoolData(orgid);

                this.connection.commit();
            }

            orgids.close();
        }
        finally {
            // Restore original autocommit state
            this.connection.setAutoCommit(autocommit);
        }
    }

    /**
     * Retrieves a result set to pull product IDs for a given org
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    protected ResultSet getProductIds(String orgid) throws DatabaseException, SQLException {
        return this.executeQuery(
            "SELECT " +
            "  cp_product.id, cpo_products.uuid, cp_product.created, cp_product.updated, " +
            "  cp_product.multiplier, cp_product.name " +
            "FROM " +
            "  (SELECT p.productid AS product_id " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.productid, '') IS NULL " +
            "  UNION " +
            "  SELECT p.derivedproductid " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.derivedproductid, '') IS NULL " +
            "  UNION " +
            "  SELECT pp.product_id " +
            "    FROM cp_pool p " +
            "    JOIN cp_pool_products pp " +
            "      ON p.id = pp.pool_id " +
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
            "    JOIN cp_subscription s " +
            "      ON s.id = sp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sp.product_id, '') IS NULL " +
            "  UNION " +
            "  SELECT sdp.product_id " +
            "    FROM cp_sub_derivedprods sdp " +
            "    JOIN cp_subscription s " +
            "      ON s.id = sdp.subscription_id " +
            "    WHERE s.owner_id = ? " +
            "      AND NOT NULLIF(sdp.product_id, '') IS NULL) AS plist " +
            "  INNER JOIN cp_product " +
            "    ON cp_product.id = plist.product_id " +
            "  LEFT JOIN cpo_products " +
            "    ON (cpo_products.product_id = cp_product.id AND cpo_products.owner_id = ?)" +
            "WHERE " +
            "  cpo_products.uuid IS NULL " +
            "  OR cp_product.created > cpo_products.created " +
            "  OR cp_product.updated > cpo_products.updated ",
            orgid, orgid, orgid, orgid, orgid, orgid, orgid, orgid
        );
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
        int migrated = 0;
        int updated = 0;

        this.logger.info("  Migrating product data...");

        ResultSet productids = this.getProductIds(orgid);
        while (productids.next()) {
            String productid = productids.getString(1);
            String productuuid = productids.getString(2);

            if (productsMigrated.get(productid) != null) {
                this.logger.warn(String.format(
                    "    Skipping migration for already-migrated product: %s", productid
                ));
            }

            if (productuuid != null) {
                this.logger.info(String.format("    Updating product: %s (UUID: %s)", productid, productuuid));

                // Product changed -- need to update it
                int changed = this.executeUpdate(
                    "UPDATE cpo_products p " +
                    "SET p.created = ?, p.updated = ?, p.multiplier = ?, p.name = ? ",
                    productids.getTimestamp(3), productids.getTimestamp(4), productids.getInt(5),
                    productids.getString(6)
                );

                if (changed != 1) {
                    throw new DatabaseException(String.format(
                        "Unable to update product: %s (UUID: %s)", productid, productuuid
                    ));
                }

                ++updated;

                // Need to update the product here. We're going to be lazy and delete all of the
                // existing product data, and then re-insert it. Allows us to do this in two queries
                // instead of three, more complex, queries for each related set.

                // We can just do the deletes here, then fall into the migration code below for the
                // remainder.
                this.executeUpdate(
                    "DELETE FROM cpo_pool_provided_products WHERE product_uuid = ?", productuuid
                );

                this.executeUpdate(
                    "DELETE FROM cpo_pool_derived_products WHERE product_uuid = ?", productuuid
                );

                this.executeUpdate(
                    "DELETE FROM cpo_product_dependent_products WHERE product_uuid = ?", productuuid
                );

                this.executeUpdate(
                    "DELETE FROM cpo_product_attributes WHERE product_uuid = ?", productuuid
                );

                this.executeUpdate(
                    "DELETE FROM cpo_product_certificates WHERE product_uuid = ?", productuuid
                );
            }
            else {
                this.logger.info(String.format("    Migrating product: %s", productid));

                // New product entirely
                productuuid = this.generateUUID();

                int changed = this.executeUpdate(
                    "INSERT INTO cpo_products VALUES (?, ?, ?, ?, ?, ?, ?)",
                    productuuid, productids.getTimestamp(3), productids.getTimestamp(4),
                    productids.getInt(5), orgid, productid, productids.getString(6)
                );

                if (changed != 1) {
                    throw new DatabaseException(String.format(
                        "Unable to migrate product: %s (org: %s)", productid, orgid
                    ));
                }

                ++migrated;
            }

            // Update product details
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

            this.executeUpdate(
                "INSERT INTO cpo_product_dependent_products " +
                "SELECT ?, element " +
                "FROM cp_product_dependent_products WHERE cp_product_id = ?",
                productuuid, productid
            );

            ResultSet attributes = this.executeQuery(
                "SELECT id, created, updated, name, value, product_id " +
                "FROM cp_product_attribute WHERE product_id = ?",
                productid
            );

            while (attributes.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_product_attributes" +
                    "  (id, created, updated, name, value, product_uuid) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), attributes.getTimestamp(2), attributes.getTimestamp(3),
                    attributes.getString(4), attributes.getString(5), productuuid
                );
            }

            attributes.close();

            ResultSet certificates = this.executeQuery(
                "SELECT id, created, updated, cert, privatekey, product_id " +
                "FROM cp_product_certificate WHERE product_id = ?",
                productid
            );

            while (certificates.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_product_certificates" +
                    "  (id, created, updated, cert, privatekey, product_uuid) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), certificates.getTimestamp(2), certificates.getTimestamp(3),
                    certificates.getBytes(4), certificates.getBytes(5), productuuid
                );
            }

            certificates.close();

            // Add product to cache now that it's been migrated
            productsMigrated.put(productid, productuuid);
        }

        productids.close();
        this.logger.info(String.format("  Done. %d products migrated, %d updated", migrated, updated));
    }

    /**
     * Migrates content data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate product data
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateContentData(String orgid) throws DatabaseException, SQLException {
        Map<String, String> contentMigrated = new HashMap<String, String>();
        int migrated = 0;
        int updated = 0;

        this.logger.info("  Migrating content data...");

        // Migrate content used by this product
        ResultSet contentids = this.executeQuery(
            "SELECT " +
            "  DISTINCT c.id, cpo_content.uuid, c.created, c.updated, c.contenturl, c.gpgurl, " +
            "  c.label, c.metadataexpire, c.name, c.releasever, c.requiredtags, c.type, " +
            "  c.vendor, c.arches " +
            "FROM cp_content c " +
            "INNER JOIN cp_product_content pc ON c.id = pc.content_id " +
            "INNER JOIN cpo_products ON pc.product_id = cpo_products.product_id " +
            "LEFT JOIN cpo_content ON (c.id = cpo_content.content_id AND cpo_content.owner_id = ?)" +
            "WHERE " +
            "  cpo_content.uuid IS NULL " +
            "  OR c.created > cpo_content.created " +
            "  OR c.updated > cpo_content.updated ",
            orgid
        );

        while (contentids.next()) {
            String contentid = contentids.getString(1);
            String contentuuid = contentids.getString(2);

            if (contentMigrated.get(contentid) != null) {
                this.logger.warn(String.format(
                    "    Skipping migration for already-migrated content: %s", contentid
                ));
            }

            if (contentuuid != null) {
                this.logger.info(String.format("  Updating content: %s (UUID: %s)", contentid, contentuuid));

                // Content changed -- need to update it
                int changed = this.executeUpdate(
                    "UPDATE cpo_content c " +
                    "SET c.created = ?, c.updated = ?, c.contenturl = ?, c.gpgurl = ?, c.label = ?, " +
                    "c.metadataexpire = ?, c.name = ?, c.releasever = ?, c.requiredtags = ?, " +
                    "c.type = ?, c.vendor = ?, c.arches = ? " +
                    "WHERE c.uuid = ?",
                    contentids.getTimestamp(3), contentids.getTimestamp(4), contentids.getString(5),
                    contentids.getString(6), contentids.getString(7), contentids.getLong(8),
                    contentids.getString(9), contentids.getString(10), contentids.getString(11),
                    contentids.getString(12), contentids.getString(13), contentids.getString(14),
                    contentuuid
                );

                if (changed < 1) {
                    throw new DatabaseException(String.format(
                        "Unable to update content: %s (UUID: %s)", contentid, contentuuid
                    ));
                }

                ++updated;

                // Delete data related to this content so we can re-import it below
                this.executeUpdate(
                    "DELETE FROM cpo_content_modified_products WHERE content_uuid = ?", contentuuid
                );

                this.executeUpdate(
                    "DELETE FROM cpo_environment_content WHERE content_uuid = ?", contentuuid
                );
            }
            else {
                this.logger.info(String.format("    Migrating content: %s", contentid));

                // New product entirely
                contentuuid = this.generateUUID();

                int changed = this.executeUpdate(
                    "INSERT INTO cpo_content(uuid, content_id, created, updated, owner_id, " +
                    "contenturl, gpgurl, label, metadataexpire, name, releasever, requiredtags, " +
                    "type, vendor, arches) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                    contentuuid, contentid, contentids.getTimestamp(3), contentids.getTimestamp(4),
                    orgid, contentids.getString(5), contentids.getString(6), contentids.getString(7),
                    contentids.getLong(8), contentids.getString(9), contentids.getString(10),
                    contentids.getString(11), contentids.getString(12), contentids.getString(13),
                    contentids.getString(14)
                );

                if (changed < 1) {
                    throw new DatabaseException(String.format(
                        "Unable to migrate content: %s (org: %s)", contentid, orgid
                    ));
                }

                ++migrated;
            }

            this.executeUpdate(
                "INSERT INTO cpo_content_modified_products " +
                "SELECT ?, element " +
                "FROM cp_content_modified_products " +
                "WHERE cp_content_id = ?",
                contentuuid, contentid
            );

            ResultSet content = this.executeQuery(
                "SELECT id, created, updated, contentid, enabled, environment_id " +
                "FROM cp_env_content WHERE contentid = ?",
                contentid
            );

            while (content.next()) {
                this.executeUpdate(
                    "INSERT INTO cpo_environment_content" +
                    "  (id, created, updated, content_uuid, enabled, environment_id) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), content.getTimestamp(2), content.getTimestamp(3),
                    contentuuid, content.getBoolean(5), content.getString(6)
                );
            }

            content.close();
        }

        contentids.close();
        this.logger.info(String.format("  Done. %d content migrated, %d updated", migrated, updated));


        // Update product=>content references
        this.logger.info("  Updating product-content maps...");
        migrated = 0;
        updated = 0;

        ResultSet contentmap = this.executeQuery(
            "SELECT cpo_products.uuid, cpo_content.uuid, pc.enabled, pc.created, pc.updated " +
            "FROM cp_product_content pc " +
            "INNER JOIN cpo_products ON (pc.product_id = cpo_products.product_id " +
            "  AND cpo_products.owner_id = ?) " +
            "INNER JOIN cpo_content ON (pc.content_id = cpo_content.content_id " +
            "  AND cpo_content.owner_id = ?) " +
            "LEFT JOIN cpo_product_content pco ON (pco.product_uuid = cpo_products.uuid " +
            "  AND pco.content_uuid = cpo_content.uuid) " +
            "WHERE pco.product_uuid IS NOT NULL " +
            "  AND (pc.created > pco.created OR pc.updated > pco.updated)",
            orgid, orgid
        );

        // This is painful. If there's a better way to do updates, fix this
        while (contentmap.next()) {
            updated += this.executeUpdate(
                "UPDATE cpo_product_content " +
                "SET enabled = ?, created = ?, updated = ? " +
                "WHERE product_uuid = ? AND content_uuid = ?",
                contentmap.getBoolean(3), contentmap.getTimestamp(4), contentmap.getTimestamp(5),
                contentmap.getString(1), contentmap.getString(2)
            );
        }

        contentmap.close();

        migrated += this.executeUpdate(
            "INSERT INTO cpo_product_content " +
            "SELECT cpo_products.uuid, cpo_content.uuid, pc.enabled, pc.created, pc.updated " +
            "FROM cp_product_content pc " +
            "INNER JOIN cpo_products ON (pc.product_id = cpo_products.product_id " +
            "  AND cpo_products.owner_id = ?) " +
            "INNER JOIN cpo_content ON (pc.content_id = cpo_content.content_id " +
            "  AND cpo_content.owner_id = ?) " +
            "LEFT JOIN cpo_product_content pco ON (pco.product_uuid = cpo_products.uuid " +
            "  AND pco.content_uuid = cpo_content.uuid) " +
            "WHERE pco.product_uuid IS NULL ",
            orgid, orgid
        );

        this.logger.info(String.format(
            "  Done. %d content mappings migrated, %d updated", migrated, updated
        ));
    }

    /**
     * Migrates activation key data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate activation key data
     */
    protected void migrateActivationKeyData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating activation key data...");

        // We don't need to worry about updates here, since there are no timestamps or other such
        // things to change on the join table. We'll just insert items which are missing.
        int migrated = this.executeUpdate(
            "INSERT INTO cpo_activation_key_products(key_id, product_uuid) " +
            "SELECT akp.id, p.uuid " +
            "FROM cp_activationkey_product akp  " +
            "INNER JOIN cp_activation_key ak ON (akp.key_id = ak.id AND ak.owner_id = ?) " +
            "INNER JOIN cpo_products p ON (akp.product_id = p.product_id AND p.owner_id = ?) " +
            "LEFT JOIN cpo_activation_key_products akpo ON " +
            "  (akpo.key_id = akp.id AND akpo.product_uuid = p.uuid) " +
            "WHERE akpo.key_id IS NULL ",
            orgid, orgid
        );

        this.logger.info(String.format("  Done. %d activation key mappings migrated", migrated));
    }

    /**
     * Migrates pool data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate pool data
     */
    protected void migratePoolData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating source subscription data...");
        int migrated = 0;
        int updated = 0;

        ResultSet sourcesubs = this.executeQuery(
            "SELECT p.id, sso.id, ss.subscriptionid, ss.subscriptionsubkey, ss.created, ss.updated " +
            "FROM cp_pool p " +
            "INNER JOIN cp_pool_source_sub ss ON (ss.pool_id = p.id) " +
            "LEFT JOIN cpo_pool_source_sub sso ON (p.id = sso.pool_id) " +
            "WHERE p.owner_id = ? " +
            "  AND ( " +
            "    sso.id IS NULL " +
            "    OR ( " +
            "      ss.created > sso.created " +
            "      OR ss.updated > sso.updated " +
            "    ) " +
            "  )",
            orgid
        );

        while (sourcesubs.next()) {
            String poolid = sourcesubs.getString(1);
            String ssid = sourcesubs.getString(2);

            if (ssid == null) {
                migrated += this.executeUpdate(
                    "INSERT INTO cpo_pool_source_sub " +
                    "  (id, subscription_id, subscription_sub_key, pool_id, created, updated)" +
                    "VALUES(?, ?, ?, ?, ?, ?)",
                    this.generateUUID(), sourcesubs.getString(3), sourcesubs.getString(4), poolid,
                    sourcesubs.getTimestamp(5), sourcesubs.getTimestamp(6)
                );
            }
            else {
                updated += this.executeUpdate(
                    "UPDATE cpo_pool_source_sub " +
                    "SET subscription_id = ?, subscription_sub_key = ?, created = ?, updated = ? " +
                    "WHERE id = ?",
                    sourcesubs.getString(3), sourcesubs.getString(4), sourcesubs.getTimestamp(5),
                    sourcesubs.getTimestamp(6), ssid
                );
            }
        }

        sourcesubs.close();
        this.logger.info(String.format(
            "  Done. %d source subscriptions migrated, %d updated", migrated, updated
        ));
    }

}

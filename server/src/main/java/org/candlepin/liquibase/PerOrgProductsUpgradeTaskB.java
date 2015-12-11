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
 * The PerOrgProductsUpgradeTaskB performs the post-db upgrade on the pre-existing cp_* tables. This
 * task also checks if any changes have been made to product or content information since the
 */
public class PerOrgProductsUpgradeTaskB extends PerOrgProductsUpgradeTaskA {

    public PerOrgProductsUpgradeTaskB(Database database) {
        super(database);
    }

    public PerOrgProductsUpgradeTaskB(Database database, CustomTaskLogger logger) {
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
                this.migrateSubscriptionData(orgid);
            }

            orgids.close();

            this.updatePoolObjectReferences();

            this.connection.commit();
        }
        finally {
            // Restore original autocommit state
            this.connection.setAutoCommit(autocommit);
        }
    }

    protected ResultSet getProductIds(String orgid) throws DatabaseException, SQLException {
        return this.executeQuery(
            "SELECT " +
            "  cp_product.id, cpo_products.uuid, cp_product.created, cp_product.updated, " +
            "  cp_product.multiplier, cp_product.name " +
            "FROM " +
            "  (SELECT p.product_id_old AS product_id " +
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
     * Migrates subscription data. Must be called per-org.
     *
     * @param orgid
     *  The id of the owner/organization for which to migrate subscription data
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void migrateSubscriptionData(String orgid) throws DatabaseException, SQLException {
        this.logger.info("  Migrating subscription information...");
        int migrated = 0;
        int updated = 0;

        ResultSet subscriptiondata = this.executeQuery(
            "SELECT id, certificate_id, cdn_id, upstream_entitlement_id, upstream_consumer_id, " +
            "  upstream_pool_id " +
            "FROM cp_subscription WHERE owner_id = ?",
            orgid
        );

        while (subscriptiondata.next()) {
            String subid = subscriptiondata.getString(1);

            String upstreamEntitlementId = subscriptiondata.getString(4);
            String upstreamConsumerId = subscriptiondata.getString(5);
            String upstreamPoolId = subscriptiondata.getString(6);

            // If the subscription is lacking upstream information, it's likely a custom sub. We'll
            // need to remove the source sub information from its corresponding pool (if it exists)
            if (upstreamEntitlementId == null || upstreamConsumerId == null || upstreamPoolId == null) {
                int count = this.executeUpdate(
                    "DELETE FROM cp_pool_source_sub WHERE subscriptionid = ?", subid
                );

                // If we didn't delete anything, the pools haven't been refreshed after the sub was
                // added, so we'll need to migrate it ourselves.
                if (count == 0) {
                    migrated += this.executeUpdate(
                        "INSERT INTO cp_pool (" +
                        "  id, created, updated, activesubscription, accountnumber, contractnumber, " +
                        "  enddate, quantity, startdate, owner_id, ordernumber, type, product_uuid, " +
                        "  cdn_id, certificate_id, version) " +
                        "SELECT ?, S.created, S.updated, ?, " +
                        "  S.accountnumber, S.contractnumber, S.enddate, S.quantity, S.startdate, " +
                        "  S.owner_id, S.ordernumber, 'NORMAL', " +
                        "  (SELECT uuid FROM cpo_products " +
                        "    WHERE owner_id = S.owner_id AND product_id = S.product_id), " +
                        "  S.cdn_id, S.certificate_id, 0 " +
                        "FROM cp_subscription S WHERE id = ?",
                        this.generateUUID(), true, subid
                    );
                }
            }

            // ...otherwise we need to migrate upstream information to the master pool
            else {
                // Update any master pools which make use of this subscription information
                updated += this.executeUpdate(
                    "UPDATE cp_pool SET " +
                    "  certificate_id = ?, " +
                    "  cdn_id = ?, " +
                    "  upstream_entitlement_id = ?, " +
                    "  upstream_consumer_id = ?, " +
                    "  upstream_pool_id = ? " +
                    "WHERE cp_pool.id IN (" +
                    "  SELECT SS.pool_id FROM cp_pool_source_sub SS " +
                    "  WHERE SS.subscriptionid = ? AND SS.subscriptionsubkey = 'master'" +
                    ")",
                    subscriptiondata.getString(2), subscriptiondata.getString(3),
                    upstreamEntitlementId, upstreamConsumerId, upstreamPoolId, subid
                );
            }
        }

        subscriptiondata.close();
        this.logger.info(String.format("  Done. %d subscriptions migrated, %d updated", migrated, updated));
    }

    /**
     * Updates pools to point to the newly migrated objects
     */
    protected void updatePoolObjectReferences() throws DatabaseException, SQLException {
        this.logger.info("Updating pool object references...");

        int updated = this.executeUpdate(
            "UPDATE cp_pool p " +
            "SET product_uuid = (SELECT uuid FROM cpo_products prod " +
            "  WHERE prod.product_id = p.product_id_old AND prod.owner_id = p.owner_id), " +
            "derived_product_uuid = (SELECT uuid FROM cpo_products prod " +
            "  WHERE prod.product_id = p.derived_product_id_old AND prod.owner_id = p.owner_id)"
        );

        this.logger.info(String.format("Done. %d pools updated", updated));
    }
}

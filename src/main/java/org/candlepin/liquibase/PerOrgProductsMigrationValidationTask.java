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

import liquibase.database.Database;
import liquibase.exception.DatabaseException;

import java.sql.ResultSet;
import java.sql.SQLException;



/**
 * The PerOrgProductsMigrationValidationTask performs pre-migration validation on the current state of
 * the database before migrating to per-org products.
 */
public class PerOrgProductsMigrationValidationTask extends LiquibaseCustomTask {

    public PerOrgProductsMigrationValidationTask(Database database, CustomTaskLogger logger) {
        super(database, logger);
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
            "FROM (SELECT NULLIF(p.productid, '') AS product_id, p.id AS pool_id, " +
            "  NULL AS subscription_id " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "  UNION " +
            "  SELECT p.derivedproductid, p.id, NULL " +
            "    FROM cp_pool p " +
            "    WHERE p.owner_id = ? " +
            "      AND NOT NULLIF(p.derivedproductid, '') IS NULL " +
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

        // Impl Note:
        // We don't need to check activation key products, since the migration just deletes these if they
        // exist.

        return passed;
    }

    /**
     * Executes this task
     *
     * @throws DatabaseException
     *  if an error occurs while performing a database operation
     *
     * @throws SQLException
     *  if an error occurs while executing an SQL statement
     */
    public void execute() throws DatabaseException, SQLException {
        // Fetch our org count for prettier log messages
        ResultSet countQuery = this.executeQuery("SELECT count(id) FROM cp_owner");
        countQuery.next();
        int count = countQuery.getInt(1);
        countQuery.close();

        boolean validated = true;
        ResultSet orgids = this.executeQuery("SELECT id, account FROM cp_owner");
        for (int index = 1; orgids.next(); ++index) {
            String orgid = orgids.getString(1);
            String account = orgids.getString(2);

            this.logger.info("Validating data for org %s (%s) (%d of %d)", account, orgid, index, count);

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
    }

}

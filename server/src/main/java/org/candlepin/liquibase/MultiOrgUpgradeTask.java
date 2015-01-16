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

import liquibase.database.jvm.JdbcConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The MultiOrgUpgradeTask performs the post-db upgrade data migration to the cp_org_* tables.
 */
public class MultiOrgUpgradeTask {

    private JdbcConnection connection;
    private CustomTaskLogger logger;

    private Map<String, PreparedStatement> preparedStatements;


    public MultiOrgUpgradeTask(JdbcConnection connection) {
        this(connection, new SystemOutLogger());
    }

    public MultiOrgUpgradeTask(JdbcConnection connection, CustomTaskLogger logger) {
        if (connection == null) {
            throw new IllegalArgumentException("connection is null");
        }

        if (logger == null) {
            throw new IllegalArgumentException("logger is null");
        }

        this.connection = connection;
        this.logger = logger;

        this.preparedStatements = new HashMap<String, PreparedStatement>();
    }


    /**
     * Executes the given SQL query statement immediately (non-transactionally).
     *
     * @param sql
     *  The SQL to execute. The given SQL may be parameterized.
     *
     * @param argv
     *  The arguments to use when executing the given query.
     *
     * @return
     *  A ResultSet instance representing the result of the query.
     */
    protected ResultSet executeQuery(String sql, Object... argv) {

        boolean autocommit = this.connection.getAutoCommit();
        this.connection.setAutoCommit(true);

        PreparedStatement statement = this.preparedStatements.get(sql);
        if (statement == null) {
            statement = this.connection.prepareStatement(sql);
            this.preparedStatements.put(sql, statement);
        }

        statement.clearParameters();

        for (int i = 0; i < argv.length; ++i) {
            if (argv[i] != null) {
                statement.setObject(i + 1, argv[i]);
            } else {
                // Impl note:
                // Oracle has trouble with setNull. See the comments on this SO question for details:
                // http://stackoverflow.com/questions/11793483/setobject-method-of-preparedstatement
                statement.setNull(i + 1, Types.VARCHAR);
            }
        }

        ResultSet result = statement.executeQuery();

        this.connection.setAutoCommit(autocommit);
        return result;
    }


    /**
     * Executes a query to retreive all known organizations from the database.
     *
     * @return
     *  A ResultSet instance representing the result of the query.
     */
    protected ResultSet getOrgIDs() {
        String sql = "SELECT id FROM cp_owner;";

        return this.executeQuery(sql);
    }

    /**
     * Executes a query to retrieve from the database all known products for the specified
     * organization.
     *
     * @param orgid
     *  The ID of the organization for which to retrieve products.
     *
     * @return
     *  A ResultSet instance representing the result of the query.
     */
    protected ResultSet getProductIDs(String orgid) {
        String sql =
            "SELECT p.productid "+
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "    AND p.productid IS NOT NULL " +
            "    AND p.productid != '' " +
            "UNION " +
            "SELECT p.derivedproductid " +
            "  FROM cp_pool p " +
            "  WHERE p.owner_id = ? " +
            "  AND p.derivedproductid IS NOT NULL " +
            "  AND p.derivedproductid != '' " +
            "UNION " +
            "SELECT pp.product_id " +
            "  FROM cp_pool p " +
            "  JOIN cp_pool_products pp " +
            "    ON p.id = pp.pool_id " +
            "  WHERE p.owner_id = ? " +
            "    AND pp.product_id IS NOT NULL " +
            "    AND pp.product_id != '';";

        return this.executeQuery(sql, orgid, orgid, orgid);
    }

    /**
     * Executes a query to retrieve from the database all known content for the specified product.
     *
     * @param productid
     *  The ID of the product for which to retrieve content.
     *
     * @return
     *  A ResultSet instance representing the result of the query.
     */
    protected ResultSet getContentIDs(String productid) {
        String sql = "SELECT content_id FROM cp_product_content WHERE product_id = ?;";

        return this.executeQuery(sql, productid);
    }






    public void execute() throws DatabaseException {
        // Store the connection's auto commit setting, so we may temporarily clobber it.

        Map<String, Boolean> orgContent = new HashMap<String, Boolean>();
        boolean autocommit;

        ResultSet orgids = this.getOrgIDs();
        for (orgids.first(); !orgids.isAfterLast(); orgis.next()) {
            String orgid = getString(1);
            orgContent.clear();

            ResultSet productids = this.getProductIDs(orgid);
            for (productids.first(); !productids.isAfterLast(); productids.next()) {
                String productid = productids.getString(1);

                autocommit = this.connection.getAutoCommit();
                this.connection.setAutoCommit(false);

                // Generate new UUID
                // Migration information from pre-existing tables to cp_org_* tables
                // Update new product columns on existing tables

                this.connection.commit();
                this.connection.setAutoCommit(autocommit);

                // Update product's content
                ResultSet contentids = this.getContentIDs(productid);
                for (contentids.first(); !contentids.isAfterLast(); contentids.next()) {
                    String contentid = contentids.getString(1);

                    if (orgContent.get(contentid) == null) {
                        orgContent.put(contentid);

                        autocommit = this.connection.getAutoCommit();
                        this.connection.setAutoCommit(false);

                        // update cp_org_content:
                        // update content tables

                        this.connection.commit();
                        this.connection.setAutoCommit(autocommit);
                    }

                    // update product => content links
                }

                contentids.close();
            }

            productids.close();
        }

        orgids.close();


    }



}

/*
Upgrade task pseudo code:
    - Get list of existing org IDs
        SELECT id FROM cp_owner;

    - For each org:
        - Get list of products via pool:
            SELECT p.productid FROM cp_pool p WHERE p.owner_id = <current org id> AND p.productid IS NOT NULL AND p.productid != ''
            UNION
            SELECT p.derivedproductid FROM cp_pool p WHERE p.owner_id = <current org id> AND p.derivedproductid IS NOT NULL AND p.derivedproductid != ''
            UNION
            SELECT DISTINCT pp.product_id
              FROM cp_pool p
              JOIN cp_pool_products pp
                ON p.id = pp.pool_id
              WHERE p.owner_id = <current org id>
                AND pp.product_id IS NOT NULL
                AND pp.product_id != ''

        - For each product id:
            - Generate new UUID:
                product uuid = java.util.UUID.randomUUID().toString().replace('-', '')

            - Migrate information from pre-existing tables to new cp_org_* tables:
                - cp_org_activationkey_product:
                    INSERT INTO cp_org_activationkey_product
                    SELECT id, created, updated, key_id, <product uuid>
                    FROM cp_activationkey_product WHERE product_id = <rh product id>

                - cp_org_branding:
                    INSERT INTO cp_org_branding
                    SELECT id, created, updated, <product uuid>, type, name
                    FROM cp_branding WHERE productid = <rh product id>

                - cp_org_installed_products:
                    INSERT INTO cp_org_installed_products
                    SELECT id, created, updated, <product uuid>, consumer_id, product_version, product_arch
                    FROM cp_installed_products WHERE id = (
                        SELECT cip.id
                        FROM cp_installed_products cip JOIN cp_consumer cc ON cip.consumer_id = cc.id
                        WHERE cc.owner_id = <current org id> AND cip.product_id = <rh product id>
                    )

                - cp_org_product:
                    INSERT INTO cp_org_product
                    SELECT <product uuid>, created, updated, multiplier, name
                    FROM cp_product WHERE id = <rh product id>

                - cp_org_product_attribute:
                    INSERT INTO cp_org_product_attribute
                    SELECT id, created, updated, name, value, <product uuid>
                    FROM cp_product_attribute WHERE product_id = <rh product id>

                - cp_org_product_certificate:
                    INSERT INTO cp_org_product_certificate
                    SELECT id, created, updated, cert, privatekey, <product uuid>
                    FROM cp_product_certificate WHERE product_id = <rh product id>

                - cp_org_product_dependent_products:
                    INSERT INTO cp_org_product_dependent_products
                    SELECT <product uuid>, element
                    FROM cp_product_dependent_products

            - Get list of content for current product:
                SELECT content_id FROM cp_product_content WHERE product_id = <rh product id>

                For each content:
                    - if the content has not been added for this org:
                        (WHERE NOT EXIST (SELECT id FROM cp_org_content WHERE id = <current content id> AND owner_id = <current org id>) ?)
                        (Perhaps a hashmap in the Java would be quicker)

                        - update cp_org_content:
                            INSERT INTO cp_org_content
                            SELECT id, created, updated, <current org id>, contenturl, gpgurl, label, metadataexpire, name, releasever, requiredtags, type, vendor, arches
                            FROM cp_content

                        - update content tables
                            - cp_org_content_modified_products:
                                INSERT INTO cp_org_modified_products
                                SELECT <current content_id>, element
                                FROM cp_content_modified_products
                                WHERE cp_content_id = <current content id>

                            - cp_org_environment_content:
                                INSERT INTO cp_org_environment_content
                                SELECT id, created, updated, <current content id>, enabled, environment_id
                                FROM cp_env_content
                                WHERE content_id = <current_content_id>

                    - update product => content links
                        INSERT INTO cp_org_product_content
                        SELECT <product uuid>, <current content id>, enabled, created, updated
                        FROM cp_product_content WHERE product_id = <rh product id> AND content_id = <current content id>

            - Update new product columns on existing tables:
                UPDATE cp_pool SET product_id = <generated product id> WHERE product_id_old = <RH product id> AND owner_id = <current org id>
                UPDATE cp_pool SET derived_product_id = <generated product id> WHERE derived_product_id_old = <RH product id> AND owner_id = <current org id>
*/
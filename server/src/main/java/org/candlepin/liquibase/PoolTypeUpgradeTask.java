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
import liquibase.exception.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;



/**
 * The PoolTypeUpgradeTask performs the post-db upgrade data migration to the cpo_* tables.
 */
public class PoolTypeUpgradeTask {

    private JdbcConnection connection;
    private CustomTaskLogger logger;

    private Map<String, PreparedStatement> preparedStatements;


    public PoolTypeUpgradeTask(JdbcConnection connection) {
        this(connection, new SystemOutLogger());
    }

    public PoolTypeUpgradeTask(JdbcConnection connection, CustomTaskLogger logger) {
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


    protected PreparedStatement prepareStatement(String sql, Object... argv)
        throws DatabaseException, SQLException {

        PreparedStatement statement = this.preparedStatements.get(sql);
        if (statement == null) {
            statement = this.connection.prepareStatement(sql);
            this.preparedStatements.put(sql, statement);
        }

        statement.clearParameters();

        for (int i = 0; i < argv.length; ++i) {
            if (argv[i] != null) {
                statement.setObject(i + 1, argv[i]);
            }
            else {
                // Impl note:
                // Oracle has trouble with setNull. See the comments on this SO question for details:
                // http://stackoverflow.com/questions/11793483/setobject-method-of-preparedstatement
                statement.setNull(i + 1, Types.VARCHAR);
            }
        }

        return statement;
    }

    /**
     * Executes the given SQL query.
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
    protected ResultSet executeQuery(String sql, Object... argv) throws DatabaseException, SQLException {
        PreparedStatement statement = this.prepareStatement(sql, argv);
        return statement.executeQuery();
    }

    /**
     * Executes the given SQL update/insert.
     *
     * @param sql
     *  The SQL to execute. The given SQL may be parameterized.
     *
     * @param argv
     *  The arguments to use when executing the given update.
     *
     * @return
     *  The number of rows affected by the update.
     */
    protected int executeUpdate(String sql, Object... argv) throws DatabaseException, SQLException {
        PreparedStatement statement = this.prepareStatement(sql, argv);
        return statement.executeUpdate();
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
        this.connection.setAutoCommit(false);

        this.executeUpdate(
            "UPDATE cp_pool SET type = 'ENTITLEMENT_DERIVED' WHERE cp_pool.id IN (" +
            "  SELECT P.id " +
            "  FROM cp_pool P " +
            "    INNER JOIN cp_pool_attribute PA ON (P.id = PA.pool_id AND PA.name = 'pool_derived') " +
            "  WHERE " +
            "    PA.value = 'true' " +
            "    AND (P.sourceentitlement_id IS NOT NULL AND p.sourceentitlement_id != '')" +
            ")"
        );

        this.executeUpdate(
            "UPDATE cp_pool SET type = 'STACK_DERIVED' WHERE cp_pool.id IN (" +
            "  SELECT P.id " +
            "  FROM cp_pool P " +
            "    INNER JOIN cp_pool_attribute PA ON (P.id = PA.pool_id AND PA.name = 'pool_derived') " +
            "    INNER JOIN cp_pool_source_stack SS ON p.id = SS.derivedpool_id " +
            "  WHERE " +
            "    PA.value = 'true' " +
            "    AND (P.sourceentitlement_id IS NULL OR p.sourceentitlement_id = '')" +
            ")"
        );

        this.executeUpdate(
            "UPDATE cp_pool SET type = 'BONUS' WHERE cp_pool.id IN (" +
            "  SELECT P.id " +
            "  FROM cp_pool P " +
            "    INNER JOIN cp_pool_attribute PA ON (P.id = PA.pool_id AND PA.name = 'pool_derived') " +
            "    LEFT JOIN cp_pool_source_stack SS ON p.id = SS.derivedpool_id " +
            "  WHERE" +
            "    PA.value = 'true' " +
            "    AND (P.sourceentitlement_id IS NULL OR p.sourceentitlement_id = '')" +
            "    AND SS.id IS NULL" +
            ")"
        );

        this.executeUpdate(
            "UPDATE cp_pool SET type = 'UNMAPPED_GUEST' WHERE cp_pool.id IN (" +
            "  SELECT P.id " +
            "  FROM cp_pool P " +
            "    LEFT JOIN cp_pool_attribute PA1 ON (P.id = PA1.pool_id AND PA1.name = 'pool_derived') " +
            "    INNER JOIN cp_pool_attribute PA2 ON (P.id = PA2.pool_id AND PA2.name = 'unmapped_guest') " +
            "  WHERE PA1.id IS NULL AND PA2.value = 'true'" +
            ")"
        );

        this.executeUpdate(
            "UPDATE cp_pool SET type = 'NORMAL' WHERE cp_pool.id IN (" +
            "  SELECT P.id " +
            "  FROM cp_pool P " +
            "    LEFT JOIN cp_pool_attribute PA1 ON (P.id = PA1.pool_id AND PA1.name = 'pool_derived') " +
            "    LEFT JOIN cp_pool_attribute PA2 ON (P.id = PA2.pool_id AND PA2.name = 'unmapped_guest') " +
            "  WHERE PA1.id IS NULL AND PA2.id IS NULL" +
            ")"
        );

        // Commit & restore original autocommit state
        this.connection.commit();
        this.connection.setAutoCommit(autocommit);
    }

}

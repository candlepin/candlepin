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
 * The LiquibaseCustomTask class provides some common utility functions for performing queries or
 * generating UUIDs for new objects.
 */
public abstract class LiquibaseCustomTask {

    protected Database database;
    protected JdbcConnection connection;
    protected CustomTaskLogger logger;

    private Map<String, PreparedStatement> preparedStatements;
    private int nullType;

    protected LiquibaseCustomTask(Database database, CustomTaskLogger logger) {
        if (database == null) {
            throw new IllegalArgumentException("database is null");
        }

        if (logger == null) {
            throw new IllegalArgumentException("logger is null");
        }

        if (!(database.getConnection() instanceof JdbcConnection)) {
            throw new RuntimeException("database connection is not a JDBC connection");
        }

        this.database = database;
        this.connection = (JdbcConnection) database.getConnection();
        this.logger = logger;

        // Check which type we need to use for nulls (courtesy of Oracle's moody adapter)
        // See the comments on this SO question for details:
        // http://stackoverflow.com/questions/11793483/setobject-method-of-preparedstatement
        this.nullType =
            this.database.getDatabaseProductName().matches(".*(?i:oracle).*") ? Types.VARCHAR : Types.NULL;

        this.preparedStatements = new HashMap<String, PreparedStatement>();
    }

    /**
     * Sets the parameter at the specified index to the given value. This method attempts to perform
     * safe assignment of parameters across all supported platforms.
     *
     * @param statement
     *  the statement on which to set a parameter
     *
     * @param index
     *  the index of the parameter to set
     *
     * @param value
     *  the value to set
     *
     * @throws NullPointerException
     *  if statement is null
     *
     * @return
     *  the PreparedStatement being updated
     */
    protected PreparedStatement setParameter(PreparedStatement statement, int index, Object value)
        throws DatabaseException, SQLException {

        if (value != null) {
            statement.setObject(index, value);
        }
        else {
            statement.setNull(index, this.nullType);
        }

        return statement;
    }

    /**
     * Fills the parameters of a prepared statement with the given arguments
     *
     * @param statement
     *  the statement to fill
     *
     * @param argv
     *  the collection of arguments with which to fill the statement's parameters
     *
     * @throws NullPointerException
     *  if statement is null
     *
     * @return
     *  the provided PreparedStatement
     */
    protected PreparedStatement fillStatementParameters(PreparedStatement statement, Object... argv)
        throws DatabaseException, SQLException {

        statement.clearParameters();

        if (argv != null) {
            for (int i = 0; i < argv.length; ++i) {
                this.setParameter(statement, i + 1, argv[i]);
            }
        }

        return statement;
    }

    /**
     * Prepares a statement and populates it with the specified arguments, pulling from cache when
     * possible.
     *
     * @param sql
     *  The SQL to execute. The given SQL may be parameterized.
     *
     * @param argv
     *  The arguments to use when executing the given query.
     *
     * @return
     *  a PreparedStatement instance representing the specified SQL statement
     */
    protected PreparedStatement prepareStatement(String sql, Object... argv)
        throws DatabaseException, SQLException {

        PreparedStatement statement = this.preparedStatements.get(sql);
        if (statement == null) {
            statement = this.connection.prepareStatement(sql);
            this.preparedStatements.put(sql, statement);
        }

        return this.fillStatementParameters(statement, argv);
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
     * Generates a 32-character UUID to use with object creation/migration.
     * <p/>
     * The UUID is generated by creating a "standard" UUID and removing the hyphens. The UUID may be
     * standardized by reinserting the hyphens later, if necessary.
     *
     * @return
     *  a 32-character UUID
     */
    protected String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Executes this liquibase upgrade task.
     *
     * @throws DatabaseException
     *  if an error occurs while performing a database operation
     *
     * @throws SQLException
     *  if an error occurs while executing an SQL statement
     */
    public abstract void execute() throws DatabaseException, SQLException;
}

/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.database;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Class for creating and maintaining a connection to the database.
 */
public class DatabaseConnectionManager {
    private static Logger log = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private Configuration config;
    private Connection connection;

    /**
     * Creates a database connection based on the defined configuration values.
     *
     * @throws LiquibaseException
     *  if unable to establish a connection to the database
     *
     * @throws ReflectiveOperationException
     *  if unable to load the database driver
     *
     * @param config
     *  used to retrieve the database configuration values needed to establish a connection
     */
    public DatabaseConnectionManager(Configuration config)
        throws LiquibaseException, ReflectiveOperationException {
        this.config = Objects.requireNonNull(config);

        loadDatabaseDriver();
        this.connection = createConnection();
    }

    /**
     * @return the established database connection
     */
    public Connection getConnection() {
        return this.connection;
    }

    /**
     * Creates a {@link Database} object based on the database configuration.
     *
     * @throws LiquibaseException
     *  if unable to create the {@link Database} from the database connection
     *
     * @return the database created from the database connection
     */
    public Database getDatabase() throws LiquibaseException {
        JdbcConnection jdbcConnection = new JdbcConnection(this.connection);

        return DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(jdbcConnection);
    }

    /**
     * Creates a database connection based on database configuration values. If a connection is not initially
     * created, further attempts will be made to create a connection. If a connection cannot be created after
     * the configured max attempts, then a {@link LiquibaseException} will be thrown.
     *
     * @throws LiquibaseException
     *  if unable to create a database connection after max retry attempts
     *
     * @return
     *  the database connection
     */
    private Connection createConnection() throws LiquibaseException {
        int attemptNumber = 1;
        int maxAttempts = config.getInt(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS);

        while (attemptNumber <= maxAttempts) {
            log.info("Attempt {} out of {} to connect to the database.", attemptNumber, maxAttempts);
            try {
                return DriverManager.getConnection(
                    config.getString(ConfigProperties.DB_URL),
                    config.getString(ConfigProperties.DB_USERNAME),
                    config.getString(ConfigProperties.DB_PASSWORD));
            }
            catch (SQLException e) {
                log.error(e.getMessage(), e);
            }

            waitForConnectionRetryDelay();

            attemptNumber++;
        }

        String msg = "Failed to establish database connection after maximum retry attempts.";
        throw new LiquibaseException(msg);
    }

    /**
     * Waits a configured amount of time between database connection attempts.
     *
     * @throws LiquibaseException
     *  if the thread has been interrupted
     */
    private void waitForConnectionRetryDelay() throws LiquibaseException {
        int retryInterval = config.getInt(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL);
        try {
            // Convert retry interval from seconds to milliseconds
            Thread.sleep(Long.valueOf(retryInterval) * 1000);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LiquibaseException(e);
        }
    }

    protected void loadDatabaseDriver() throws ReflectiveOperationException {
        String driverClass = config.getString(ConfigProperties.DB_DRIVER_CLASS);
        if (driverClass == null) {
            String msg = "No driver class specified for the database connection. Please specify it in " +
                "'candlepin.conf' under 'jpa.config.hibernate.connection.driver_class' config or as ENV " +
                "variable.";
            log.error(msg);
            throw new RuntimeException(msg);
        }
        Class.forName(driverClass)
            .getDeclaredConstructor()
            .newInstance();
    }
}


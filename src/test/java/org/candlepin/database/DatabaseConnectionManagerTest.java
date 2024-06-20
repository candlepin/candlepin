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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.test.TestUtil;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnectionManagerTest {

    private DevConfig config;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();
    }

    @Test
    public void testConstructorWithNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new DatabaseConnectionManager(null));
    }

    @Test
    public void testGetConnectionWithValidConnection() throws Exception {
        String url = TestUtil.randomString();
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();
        config.setProperty(ConfigProperties.DB_URL, url);
        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, "1");
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            Connection mockConnection = Mockito.mock(Connection.class);
            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenReturn(mockConnection);

            DatabaseConnectionManager conManager = new DatabaseConnectionManager(config) {
                @Override
                protected void loadDatabaseDriver() {
                    // Intentionally left blank
                }
            };

            Connection actual = conManager.getConnection();

            assertThat(actual)
                .isEqualTo(mockConnection);

            manager.verify(() -> DriverManager.getConnection(url, username, password));
        }
    }

    @Test
    public void testGetConnectionWithUnsuccesfulConnection() throws Exception {
        String url = TestUtil.randomString();
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();
        config.setProperty(ConfigProperties.DB_URL, url);
        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        int maxRetryAttempts = 3;
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, String.valueOf(maxRetryAttempts));
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenThrow(SQLException.class);

            assertThrows(LiquibaseException.class, () -> new DatabaseConnectionManager(config) {
                @Override
                protected void loadDatabaseDriver() {
                    // Intentionally left blank
                }
            });

            manager.verify(() -> DriverManager.getConnection(url, username, password),
                times(maxRetryAttempts));
        }
    }

    @Test
    public void testGetConnectionWithEventualValidConnection() throws Exception {
        String url = TestUtil.randomString();
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();
        config.setProperty(ConfigProperties.DB_URL, url);
        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        int maxRetryAttempts = 3;
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, String.valueOf(maxRetryAttempts));
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            // Fail to connect the first two times and connect on the third and final attempt
            Connection mockConnection = Mockito.mock(Connection.class);
            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenThrow(SQLException.class, SQLException.class)
                .thenReturn(mockConnection);

            DatabaseConnectionManager conManager = new DatabaseConnectionManager(config) {
                @Override
                protected void loadDatabaseDriver() {
                    // Intentionally left blank
                }
            };

            Connection actual = conManager.getConnection();

            assertThat(actual)
                .isEqualTo(mockConnection);

            manager.verify(() -> DriverManager.getConnection(url, username, password),
                times(maxRetryAttempts));
        }
    }

    @Test
    public void testGetDatabase() throws Exception {
        String url = TestUtil.randomString();
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();
        config.setProperty(ConfigProperties.DB_URL, url);
        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, "1");
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            Connection mockConnection = Mockito.mock(Connection.class);
            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenReturn(mockConnection);

            DatabaseFactory mockFactory = mock(DatabaseFactory.class);
            Database mockDatabase = mock(Database.class);
            doReturn(mockDatabase).when(mockFactory)
                .findCorrectDatabaseImplementation(any(JdbcConnection.class));
            dbFactory.when(DatabaseFactory::getInstance).thenReturn(mockFactory);

            DatabaseConnectionManager conManager = new DatabaseConnectionManager(config) {
                @Override
                protected void loadDatabaseDriver() {
                    // Intentionally left blank
                }
            };

            Database actual = conManager.getDatabase();

            assertThat(actual)
                .isEqualTo(mockDatabase);
        }
    }

    @Test
    void testMissingDbDriverClass() {
        assertThrows(RuntimeException.class, () -> new DatabaseConnectionManager(config));
    }
}

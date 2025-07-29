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
package org.candlepin.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.database.MigrationManagementLevel;
import org.candlepin.test.TestUtil;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;


public class LiquibaseConnectionGeneratorTest {

    /**
     * A null driver to use in place of an actual jdbc driver
     */
    private static class NullJdbcDriver implements Driver {

        public static final Driver INSTANCE = new NullJdbcDriver();

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:null:");
        }

        @Override
        public Connection connect(String url, Properties props) throws SQLException {
            if (!this.acceptsURL(url)) {
                return null;
            }

            return mock(Connection.class);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties props) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private DevConfig config;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();

        this.config.setProperty(ConfigProperties.DB_DRIVER_CLASS, NullJdbcDriver.class.getName());
        this.config.setProperty(ConfigProperties.DB_URL, "jdbc:null:" + TestUtil.randomString());
    }

    @BeforeAll
    public static void beforeAll() {
        try {
            DriverManager.registerDriver(NullJdbcDriver.INSTANCE);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    public static void afterAll() {
        try {
            DriverManager.deregisterDriver(NullJdbcDriver.INSTANCE);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testConstructorWithNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new LiquibaseConnectionGenerator(null));
    }

    @Test
    public void testGetConnectionWithValidConnection() throws Exception {
        String url = this.config.getString(ConfigProperties.DB_URL);
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();

        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, "1");
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            DatabaseFactory mockFactory = mock(DatabaseFactory.class);
            dbFactory.when(DatabaseFactory::getInstance).thenReturn(mockFactory);

            Connection mockConnection = Mockito.mock(Connection.class);
            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenReturn(mockConnection);

            Database mockDatabase = mock(Database.class);
            ArgumentCaptor<JdbcConnection> captor = ArgumentCaptor.forClass(JdbcConnection.class);
            doReturn(mockDatabase).when(mockFactory)
                .findCorrectDatabaseImplementation(captor.capture());

            LiquibaseConnectionGenerator connGenerator = new LiquibaseConnectionGenerator(this.config);

            Database database = connGenerator.getDatabase();
            assertNotNull(database);

            manager.verify(() -> DriverManager.getConnection(url, username, password));

            // Verify the underlying connection is the one we created
            JdbcConnection jdbcConnection = captor.getValue();
            assertEquals(mockConnection, jdbcConnection.getWrappedConnection());
        }
    }

    @Test
    public void testGetConnectionWithUnsuccesfulConnection() throws Exception {
        String url = this.config.getString(ConfigProperties.DB_URL);
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();

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

            LiquibaseConnectionGenerator generator = new LiquibaseConnectionGenerator(this.config);
            assertThrows(LiquibaseException.class, () -> generator.getDatabase());

            manager.verify(() -> DriverManager.getConnection(url, username, password),
                times(maxRetryAttempts));
        }
    }

    @Test
    public void testGetConnectionWithEventualValidConnection() throws Exception {
        String url = this.config.getString(ConfigProperties.DB_URL);
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();
        int maxRetryAttempts = 3;

        config.setProperty(ConfigProperties.DB_USERNAME, username);
        config.setProperty(ConfigProperties.DB_PASSWORD, password);
        config.setProperty(ConfigProperties.DB_CONNECTION_RETRY_INTERVAL, "1");
        config.setProperty(ConfigProperties.DB_MAX_CONNECTION_ATTEMPTS, String.valueOf(maxRetryAttempts));
        config.setProperty(ConfigProperties.DB_MANAGE_ON_START, MigrationManagementLevel.HALT.getName());

        try (MockedStatic<DriverManager> manager = Mockito.mockStatic(DriverManager.class);
            MockedStatic<DatabaseFactory> dbFactory = Mockito.mockStatic(DatabaseFactory.class)) {

            DatabaseFactory mockFactory = mock(DatabaseFactory.class);
            dbFactory.when(DatabaseFactory::getInstance).thenReturn(mockFactory);

            // Fail to connect the first two times and connect on the third and final attempt
            Connection mockConnection = Mockito.mock(Connection.class);
            manager.when(() -> DriverManager.getConnection(url, username, password))
                .thenThrow(SQLException.class, SQLException.class)
                .thenReturn(mockConnection);

            Database mockDatabase = mock(Database.class);
            ArgumentCaptor<JdbcConnection> captor = ArgumentCaptor.forClass(JdbcConnection.class);
            doReturn(mockDatabase).when(mockFactory)
                .findCorrectDatabaseImplementation(captor.capture());

            LiquibaseConnectionGenerator conManager = new LiquibaseConnectionGenerator(config);

            Database database = conManager.getDatabase();
            assertNotNull(database);

            manager.verify(() -> DriverManager.getConnection(url, username, password),
                times(maxRetryAttempts));

            // Verify the underlying connection is the one we created after a delay
            JdbcConnection jdbcConnection = captor.getValue();
            assertEquals(mockConnection, jdbcConnection.getWrappedConnection());
        }
    }

    @Test
    public void testGetDatabase() throws Exception {
        String url = this.config.getString(ConfigProperties.DB_URL);
        String username = TestUtil.randomString();
        String password = TestUtil.randomString();

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

            LiquibaseConnectionGenerator conManager = new LiquibaseConnectionGenerator(config);

            Database actual = conManager.getDatabase();

            assertThat(actual)
                .isEqualTo(mockDatabase);
        }
    }

    @Test
    void testMissingDbDriverClass() {
        this.config.setProperty(ConfigProperties.DB_DRIVER_CLASS, TestUtil.randomString("random_driver-"));

        assertThrows(ReflectiveOperationException.class, () -> new LiquibaseConnectionGenerator(this.config));
    }
}

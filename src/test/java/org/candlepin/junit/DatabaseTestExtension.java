/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.junit;

import org.candlepin.test.DatabaseTestFixture;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;


/**
 * JUnit 5 extension that provides per-class database isolation for tests. Each test class
 * gets its own in-memory HSQLDB instance, enabling parallel execution of database tests.
 *
 * <p>Two modes are supported:
 * <ul>
 *   <li><strong>Full mode</strong> (default, via {@code @ExtendWith}): runs Liquibase migrations,
 *       creates a Guice injector, and integrates with {@link DatabaseTestFixture}.</li>
 *   <li><strong>Lightweight mode</strong> (via {@link #lightweight(String)} or
 *       {@link #lightweight(String, Map)}): uses {@code hbm2ddl.auto=create-drop} from the
 *       given persistence unit, no Liquibase or Guice. Suitable for isolated Hibernate tests.</li>
 * </ul>
 */
public class DatabaseTestExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final String CHANGELOG_FILE = "db/changelog/changelog-update.xml";
    private static final String TRUNCATE_SQL = "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";
    private static final String DROP_SQL = "DROP SCHEMA IF EXISTS %s CASCADE";
    private static final String SHUTDOWN_CMD = "SHUTDOWN";
    private static final AtomicLong DB_COUNTER = new AtomicLong(0);

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(DatabaseTestExtension.class);

    private final String persistenceUnit;
    private final Map<String, String> extraProperties;
    private String jdbcUrl;
    private EntityManagerFactory lightweightEmf;
    private EntityManager lightweightEm;

    public DatabaseTestExtension() {
        this.persistenceUnit = null;
        this.extraProperties = Map.of();
    }

    private DatabaseTestExtension(String persistenceUnit, Map<String, String> extraProperties) {
        this.persistenceUnit = persistenceUnit;
        this.extraProperties = Map.copyOf(extraProperties);
    }

    /**
     * Creates a lightweight extension that uses the given persistence unit with
     * {@code hbm2ddl.auto=create-drop}, without Liquibase or Guice.
     *
     * @param persistenceUnit
     *     the persistence unit name defined in {@code persistence.xml}
     * @return a configured extension instance for use with {@code @RegisterExtension}
     */
    public static DatabaseTestExtension lightweight(String persistenceUnit) {
        return new DatabaseTestExtension(persistenceUnit, Map.of());
    }

    /**
     * Creates a lightweight extension with additional Hibernate properties merged
     * on top of the persistence unit defaults.
     *
     * @param persistenceUnit
     *     the persistence unit name defined in {@code persistence.xml}
     * @param extraProperties
     *     additional Hibernate properties (e.g. interceptor configuration)
     * @return a configured extension instance for use with {@code @RegisterExtension}
     */
    public static DatabaseTestExtension lightweight(String persistenceUnit,
        Map<String, String> extraProperties) {

        return new DatabaseTestExtension(persistenceUnit, extraProperties);
    }

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    /**
     * Returns the {@link EntityManager} for lightweight mode. Only valid after
     * {@code beforeEach} has run.
     *
     * @return the current entity manager
     * @throws IllegalStateException
     *     if called outside lightweight mode
     */
    public EntityManager getEntityManager() {
        if (this.persistenceUnit == null) {
            throw new IllegalStateException(
                "getEntityManager() is only available in lightweight mode");
        }

        return this.lightweightEm;
    }

    private boolean isLightweight() {
        return this.persistenceUnit != null;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String className = context.getRequiredTestClass().getSimpleName();
        long id = DB_COUNTER.incrementAndGet();
        String dbName = String.format("cp-test-%s-%d", className, id);
        this.jdbcUrl = String.format("jdbc:hsqldb:mem:%s;sql.enforce_strict_size=true;shutdown=true;",
            dbName);

        if (this.isLightweight()) {
            Map<String, String> props = new HashMap<>(this.extraProperties);
            props.put("hibernate.connection.url", this.jdbcUrl);
            this.lightweightEmf = Persistence.createEntityManagerFactory(this.persistenceUnit, props);
            return;
        }

        Connection jdbcConnection = DriverManager.getConnection(this.jdbcUrl, "sa", "");
        JdbcConnection liquibaseConnection = new JdbcConnection(jdbcConnection);
        Database database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(liquibaseConnection);

        executeUpdate(liquibaseConnection, "CREATE SCHEMA LIQUIBASE");
        database.setLiquibaseSchemaName("LIQUIBASE");

        Liquibase liquibase = new Liquibase(
            CHANGELOG_FILE, new ClassLoaderResourceAccessor(), database);
        liquibase.update("test");

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("connection", liquibaseConnection);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (this.isLightweight()) {
            this.lightweightEm = this.lightweightEmf.createEntityManager();
            return;
        }

        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof DatabaseTestFixture fixture) {
            fixture.setJdbcUrl(this.jdbcUrl);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (this.isLightweight()) {
            if (this.lightweightEm != null && this.lightweightEm.isOpen()) {
                if (this.lightweightEm.getTransaction().isActive()) {
                    this.lightweightEm.getTransaction().rollback();
                }
                this.lightweightEm.close();
            }

            Connection connection = DriverManager.getConnection(this.jdbcUrl, "sa", "");
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(String.format(TRUNCATE_SQL, "PUBLIC"));
            }
            finally {
                connection.close();
            }
            return;
        }

        ExtensionContext classContext = getClassContext(context);
        ExtensionContext.Store store = classContext.getStore(NAMESPACE);
        JdbcConnection connection = (JdbcConnection) store.get("connection");

        executeUpdate(connection, String.format(TRUNCATE_SQL, "PUBLIC"));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (this.isLightweight()) {
            if (this.lightweightEmf != null && this.lightweightEmf.isOpen()) {
                this.lightweightEmf.close();
            }
            return;
        }

        DatabaseTestFixture.cleanupParentInjector(this.jdbcUrl);

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        JdbcConnection connection = (JdbcConnection) store.get("connection");

        if (connection != null) {
            executeUpdate(connection, String.format(DROP_SQL, "PUBLIC"));
            executeUpdate(connection, String.format(DROP_SQL, "LIQUIBASE"));
            executeUpdate(connection, SHUTDOWN_CMD);
        }
    }

    private static void executeUpdate(JdbcConnection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to execute SQL: " + sql, e);
        }
    }

    private ExtensionContext getClassContext(ExtensionContext context) {
        return context.getParent()
            .filter(parent -> parent.getTestClass().isPresent())
            .orElse(context);
    }
}

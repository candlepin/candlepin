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

import org.candlepin.TestingModules;
import org.candlepin.pki.CryptoManager;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistFilter;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;


/**
 * JUnit 5 extension that provides per-class database isolation for tests extending
 * {@link DatabaseTestFixture}. Each test class gets its own in-memory HSQLDB instance
 * with Liquibase migrations applied, enabling parallel execution of database tests.
 */
public class CandlepinTestExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final String CHANGELOG_FILE = "db/changelog/changelog-update.xml";
    private static final String TRUNCATE_SQL = "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";
    private static final String DROP_SQL = "DROP SCHEMA IF EXISTS %s CASCADE";
    private static final String SHUTDOWN_CMD = "SHUTDOWN";

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(CandlepinTestExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String className = context.getRequiredTestClass().getSimpleName();
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String dbName = "cp-test-" + className + "-" + uniqueId;
        String jdbcUrl = "jdbc:hsqldb:mem:" + dbName +
            ";sql.enforce_strict_size=true;shutdown=true;";

        Connection jdbcConnection = DriverManager.getConnection(jdbcUrl, "sa", "");
        JdbcConnection liquibaseConnection = new JdbcConnection(jdbcConnection);
        Database database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(liquibaseConnection);

        executeUpdate(liquibaseConnection, "CREATE SCHEMA LIQUIBASE");
        database.setLiquibaseSchemaName("LIQUIBASE");

        Liquibase liquibase = new Liquibase(
            CHANGELOG_FILE, new ClassLoaderResourceAccessor(), database);
        liquibase.update("test");

        Map<String, String> jpaProperties = Map.of(
            "hibernate.connection.url", jdbcUrl);

        Injector parentInjector = Guice.createInjector(
            new TestingModules.JpaModule(jpaProperties));
        insertValidationEventListeners(parentInjector);

        CryptoManager cryptoManager = CryptoUtil.getCryptoManager();

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("parentInjector", parentInjector);
        store.put("cryptoManager", cryptoManager);
        store.put("connection", liquibaseConnection);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof DatabaseTestFixture fixture) {
            ExtensionContext classContext = getClassContext(context);
            ExtensionContext.Store store = classContext.getStore(NAMESPACE);

            Injector parentInjector = (Injector) store.get("parentInjector");
            CryptoManager cryptoManager = (CryptoManager) store.get("cryptoManager");

            fixture.setParentInjector(parentInjector);
            fixture.setCryptoManager(cryptoManager);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ExtensionContext classContext = getClassContext(context);
        ExtensionContext.Store store = classContext.getStore(NAMESPACE);
        JdbcConnection connection = (JdbcConnection) store.get("connection");

        executeUpdate(connection, String.format(TRUNCATE_SQL, "PUBLIC"));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        Injector parentInjector = (Injector) store.get("parentInjector");
        JdbcConnection connection = (JdbcConnection) store.get("connection");

        if (parentInjector != null) {
            parentInjector.getInstance(PersistFilter.class).destroy();

            EntityManager manager = parentInjector.getInstance(EntityManager.class);
            if (manager.isOpen()) {
                manager.close();
            }

            EntityManagerFactory emf = parentInjector.getInstance(EntityManagerFactory.class);
            if (emf.isOpen()) {
                emf.close();
            }
        }

        if (connection != null) {
            executeUpdate(connection, String.format(DROP_SQL, "PUBLIC"));
            executeUpdate(connection, String.format(DROP_SQL, "LIQUIBASE"));
            executeUpdate(connection, SHUTDOWN_CMD);
        }
    }

    private static void insertValidationEventListeners(Injector injector) {
        Provider<EntityManagerFactory> emfProvider = injector.getProvider(EntityManagerFactory.class);
        SessionFactoryImpl sessionFactoryImpl = (SessionFactoryImpl) emfProvider.get();
        EventListenerRegistry registry = sessionFactoryImpl
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);

        Provider<BeanValidationEventListener> listenerProvider = injector
            .getProvider(BeanValidationEventListener.class);

        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }

    private static void executeUpdate(JdbcConnection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExtensionContext getClassContext(ExtensionContext context) {
        return context.getParent()
            .filter(parent -> parent.getTestClass().isPresent())
            .orElse(context);
    }
}

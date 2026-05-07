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

import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;


/**
 * JUnit 5 extension that provides per-class database isolation with full Liquibase
 * migrations and Guice injector integration via {@link DatabaseTestFixture}.
 *
 * <p>Usage: apply via {@code @ExtendWith(DatabaseTestExtension.class)} on
 * {@link DatabaseTestFixture} subclasses, or via {@code @RegisterExtension} for
 * direct use.
 */
public class DatabaseTestExtension extends AbstractDatabaseTestExtension {

    private static final String CHANGELOG_FILE = "db/changelog/changelog-update.xml";
    private static final String DROP_SQL = "DROP SCHEMA IF EXISTS %s CASCADE";
    private static final String SHUTDOWN_CMD = "SHUTDOWN";

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(DatabaseTestExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        this.initializeJdbcUrl(context);

        Connection jdbcConnection = DriverManager.getConnection(this.getJdbcUrl(), "sa", "");
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
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof DatabaseTestFixture fixture) {
            fixture.setJdbcUrl(this.getJdbcUrl());
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
        DatabaseTestFixture.cleanupParentInjector(this.getJdbcUrl());

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        JdbcConnection connection = (JdbcConnection) store.get("connection");

        if (connection != null) {
            executeUpdate(connection, String.format(DROP_SQL, "PUBLIC"));
            executeUpdate(connection, String.format(DROP_SQL, "LIQUIBASE"));
            executeUpdate(connection, SHUTDOWN_CMD);
            closeConnection(connection);
        }
    }

    private static void closeConnection(JdbcConnection connection) {
        try {
            connection.close();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to close liquibase connection", e);
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

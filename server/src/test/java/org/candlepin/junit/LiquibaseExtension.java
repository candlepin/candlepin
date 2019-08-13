/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.jpa.boot.internal.PersistenceXmlParser;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;




public class LiquibaseExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback {
    private static final String TRUNCATE_SQL =
        "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";

    private static final String DROP_SQL =
        "DROP SCHEMA IF EXISTS %s CASCADE";

    private Liquibase liquibase;
    private ResourceAccessor accessor;
    private Database database;
    private JdbcConnection connection;

    public LiquibaseExtension(String changelogFile) {
        try {
            String connectionUrl = getJdbcUrl("testing");
            Connection jdbcConnection = DriverManager.getConnection(connectionUrl, "sa", "");
            this.connection = new JdbcConnection(jdbcConnection);
            this.database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(this.connection);
            this.accessor = new ClassLoaderResourceAccessor();
            this.liquibase = new Liquibase(changelogFile, this.accessor, this.database);

            this.dropLiquibaseSchema();
            this.dropPublicSchema();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public LiquibaseExtension() {
        this("db/changelog/changelog-testing.xml");
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        createLiquibaseSchema();
        runUpdate();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        dropPublicSchema();
        dropLiquibaseSchema();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        truncatePublicSchema();
    }

    private String getJdbcUrl(String persistenceUnit) {
        for (ParsedPersistenceXmlDescriptor unit :
            PersistenceXmlParser.locatePersistenceUnits(Collections.emptyMap())) {
            if (unit.getName().equals("testing")) {
                return unit.getProperties().getProperty("hibernate.connection.url");
            }
        }
        throw new RuntimeException("Couldn't locate persistence unit " + persistenceUnit + " in your " +
            "persistence.xml!");
    }

    public void runUpdate() throws LiquibaseException {
        liquibase.update("test");
    }

    public void dropPublicSchema() {
        this.executeUpdate(String.format(DROP_SQL, "PUBLIC"));
    }

    public void dropLiquibaseSchema() {
        this.executeUpdate(String.format(DROP_SQL, "LIQUIBASE"));
    }

    public void truncatePublicSchema() {
        this.executeUpdate(String.format(TRUNCATE_SQL, "PUBLIC"));
    }

    public void truncateLiquibaseSchema() {
        this.executeUpdate(String.format(TRUNCATE_SQL, "LIQUIBASE"));
    }

    public void createLiquibaseSchema() {
        this.executeUpdate("CREATE SCHEMA LIQUIBASE");
        database.setLiquibaseSchemaName("LIQUIBASE");
    }

    private void executeUpdate(String sql) {
        try {
            Statement statement = this.connection.createStatement();
            statement.executeUpdate(sql);

            statement.close();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

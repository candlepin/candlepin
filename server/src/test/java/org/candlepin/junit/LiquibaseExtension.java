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
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawSqlStatement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;

public class LiquibaseExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback {
    private static final String TRUNCATE_SQL =
        "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";

    private static final String DROP_SQL =
        "DROP SCHEMA %s CASCADE";

    private Liquibase liquibase;
    private ResourceAccessor accessor;
    private Database database;

    public LiquibaseExtension(String changelogFile) {
        try {
            String connectionUrl = getJdbcUrl("testing");
            Connection jdbcConnection = DriverManager.getConnection(connectionUrl, "sa", "");
            DatabaseConnection conn = new JdbcConnection(jdbcConnection);
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn);
            accessor = new ClassLoaderResourceAccessor();
            liquibase = new Liquibase(changelogFile, accessor, database);
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
        exec(String.format(DROP_SQL, "PUBLIC"));
    }

    public void dropLiquibaseSchema() {
        exec(String.format(DROP_SQL, "LIQUIBASE"));
    }

    public void truncatePublicSchema() {
        exec(String.format(TRUNCATE_SQL, "PUBLIC"));
    }

    public void truncateLiquibaseSchema() {
        exec(String.format(TRUNCATE_SQL, "LIQUIBASE"));
    }

    public void createLiquibaseSchema() {
        exec("CREATE SCHEMA LIQUIBASE");
        database.setLiquibaseSchemaName("LIQUIBASE");
    }

    private void exec(String sql) {
        SqlStatement s = new RawSqlStatement(sql);
        try {
            database.execute(new SqlStatement[] { s }, Collections.<SqlVisitor>emptyList());
        }
        catch (LiquibaseException e) {
            throw new RuntimeException(e);
        }
    }
}

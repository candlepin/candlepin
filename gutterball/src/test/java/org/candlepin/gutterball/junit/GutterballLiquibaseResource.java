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
package org.candlepin.gutterball.junit;

import org.hibernate.ejb.Ejb3Configuration;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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

/**
 * This Rule builds an HSQL Database based on the changelog file it is constructed with.
 * In order to speed up the test runs, this class attempts to only run the DDL at the beginning
 * of a test suite and simply truncates the schema before each test.  Therefore,
 * you should tag this rule as both a ClassRule and a Rule (requires JUnit >= 4.12) so that
 * it will be invoked when the test suite begins and before every test.  Additionally, the
 * changelog run should not contain any DML statements as those would be lost after the
 * tables are truncated.
 */
public class GutterballLiquibaseResource extends ExternalResource {
    private Liquibase liquibase;
    private ResourceAccessor accessor;
    private Database database;

    private static final String TRUNCATE_SQL =
        "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";

    private static final String DROP_SQL =
        "DROP SCHEMA %s CASCADE";

    public GutterballLiquibaseResource() {
        this("db/changelog/changelog.xml");
    }

    public GutterballLiquibaseResource(String changelogFile) {
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

    @Override
    public Statement apply(final Statement base, Description description) {
        if (description.getChildren().isEmpty()) {
            // Test level clean up
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    /* Strictly speaking, calling runUpdate() here would be more correct.
                     * However, runUpdate() is a pretty expensive operation and imposes a
                     * significant performance hit.  If the changelog for testing is ever
                     * modified to run DML statements, we'll have to revisit the issue.
                     * Currently the changelog only runs DDL not DML so we can get away with
                     * truncating the table after each test and not re-running runUpdate().
                     */
                    try {
                        base.evaluate();
                    }
                    finally {
                        truncatePublicSchema();
                    }

                }
            };
        }
        else {
            return super.apply(base, description);
        }
    }

    @SuppressWarnings("deprecation")
    private String getJdbcUrl(String persistenceUnit) {
        /* JPA basically makes it impossible to get configuration information out of persistence.xml
         * and the only non-deprecated Hibernate class (Configuration) wants to use hibernate.cfg.xml
         * so without resorting to XML parsing, this is about the best we can do.
         */
        Ejb3Configuration ejbConf = new Ejb3Configuration();
        ejbConf.configure(persistenceUnit, Collections.EMPTY_MAP);
        return (String) ejbConf.getProperties().get("hibernate.connection.url");
    }

    @Override
    protected void before() throws Throwable {
        createLiquibaseSchema();
        runUpdate();
    }

    @Override
    protected void after() {
        dropPublicSchema();
        dropLiquibaseSchema();
    }

    public void runUpdate() throws LiquibaseException {
        liquibase.update("test");
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

        // HSQLDB does not support mediumtext, so we fake it here.
        exec("CREATE TYPE MEDIUMTEXT AS VARCHAR(100000)");
    }
}

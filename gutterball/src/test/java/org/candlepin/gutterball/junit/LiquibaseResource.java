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

import org.junit.rules.ExternalResource;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;


public class LiquibaseResource extends ExternalResource {
    private Liquibase liquibase;
    private ResourceAccessor accessor;
    private Database database;
    private String changelogFile;

    public LiquibaseResource() {
        this("db/changelog/changelog.xml");
    }

    public LiquibaseResource(String changelogFile) {
        this(changelogFile, "jdbc:hsqldb:mem:unit-testing-jpa");
    }

    public LiquibaseResource(String changelogFile, String connectionUrl) {
        this.changelogFile = changelogFile;

        try {
            Connection jdbcConnection = DriverManager.getConnection(connectionUrl, "sa", "");
            DatabaseConnection conn = new JdbcConnection(jdbcConnection);
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn);
            accessor = new ClassLoaderResourceAccessor();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void before() throws Throwable {
        liquibase = new Liquibase(changelogFile, accessor, database);
        liquibase.update("test");
    }

    @Override
    protected void after() {
        try {
            liquibase.dropAll();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        finally {
            liquibase = null;
        }
    }
}

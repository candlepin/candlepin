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

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;


public class GutterballLiquibaseResource extends ExternalResource {
    private Liquibase liquibase;
    private ResourceAccessor accessor;
    private Database database;
    private String changelogFile;

    public GutterballLiquibaseResource() {
        this("db/changelog/changelog.xml");
    }

    public GutterballLiquibaseResource(String changelogFile) {
        this.changelogFile = changelogFile;

        try {
            String connectionUrl = getJdbcUrl("testing");
            Connection jdbcConnection = DriverManager.getConnection(connectionUrl, "sa", "");
            DatabaseConnection conn = new JdbcConnection(jdbcConnection);
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn);
            accessor = new ClassLoaderResourceAccessor();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
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

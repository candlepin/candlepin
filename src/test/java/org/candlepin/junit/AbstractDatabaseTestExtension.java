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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.atomic.AtomicLong;


/**
 * Base class for JUnit 5 extensions that provide per-class database isolation.
 * Each test class gets its own in-memory HSQLDB instance, enabling parallel execution.
 *
 * <p>Subclasses implement the lifecycle callbacks to set up their specific database
 * initialization strategy (e.g. Liquibase migrations vs {@code hbm2ddl.auto}).
 */
public abstract class AbstractDatabaseTestExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    protected static final String TRUNCATE_SQL =
        "TRUNCATE SCHEMA %s RESTART IDENTITY AND COMMIT NO CHECK";
    private static final AtomicLong DB_COUNTER = new AtomicLong(0);

    private String jdbcUrl;

    public String getJdbcUrl() {
        return this.jdbcUrl;
    }

    /**
     * Generates a unique JDBC URL for an in-memory HSQLDB instance and stores it
     * in this extension. Must be called from {@code beforeAll}.
     *
     * @param context
     *     the extension context, used to derive the test class name
     */
    protected void initializeJdbcUrl(ExtensionContext context) {
        String className = context.getRequiredTestClass().getSimpleName();
        long id = DB_COUNTER.incrementAndGet();
        String dbName = String.format("cp-test-%s-%d", className, id);
        this.jdbcUrl = String.format(
            "jdbc:hsqldb:mem:%s;sql.enforce_strict_size=true;shutdown=true;", dbName);
    }
}

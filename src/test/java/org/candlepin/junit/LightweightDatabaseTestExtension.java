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

import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;


/**
 * JUnit 5 extension that provides per-class database isolation using
 * {@code hbm2ddl.auto=create-drop} from a given persistence unit, without
 * Liquibase or Guice. Suitable for isolated Hibernate tests.
 *
 * <p>Usage: register via {@code @RegisterExtension} with the desired persistence unit:
 * <pre>{@code
 * @RegisterExtension
 * static LightweightDatabaseTestExtension db =
 *     new LightweightDatabaseTestExtension("myPersistenceUnit");
 * }</pre>
 */
public class LightweightDatabaseTestExtension extends AbstractDatabaseTestExtension {

    private final String persistenceUnit;
    private final Map<String, String> extraProperties;
    private EntityManagerFactory emf;
    private EntityManager em;

    /**
     * Creates a lightweight extension that uses the given persistence unit with
     * {@code hbm2ddl.auto=create-drop}.
     *
     * @param persistenceUnit
     *     the persistence unit name defined in {@code persistence.xml}
     */
    public LightweightDatabaseTestExtension(String persistenceUnit) {
        this(persistenceUnit, Map.of());
    }

    /**
     * Creates a lightweight extension with additional Hibernate properties merged
     * on top of the persistence unit defaults.
     *
     * @param persistenceUnit
     *     the persistence unit name defined in {@code persistence.xml}
     * @param extraProperties
     *     additional Hibernate properties (e.g. interceptor configuration)
     */
    public LightweightDatabaseTestExtension(String persistenceUnit,
        Map<String, String> extraProperties) {

        this.persistenceUnit = persistenceUnit;
        this.extraProperties = Map.copyOf(extraProperties);
    }

    /**
     * Returns the {@link EntityManager} for this extension. Only valid after
     * {@code beforeEach} has run.
     *
     * @return the current entity manager
     */
    public EntityManager getEntityManager() {
        return this.em;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        this.initializeJdbcUrl(context);

        Map<String, String> props = new HashMap<>(this.extraProperties);
        props.put("hibernate.connection.url", this.getJdbcUrl());
        this.emf = Persistence.createEntityManagerFactory(this.persistenceUnit, props);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        this.em = this.emf.createEntityManager();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (this.em != null && this.em.isOpen()) {
            if (this.em.getTransaction().isActive()) {
                this.em.getTransaction().rollback();
            }
            this.em.close();
        }

        Connection connection = DriverManager.getConnection(this.getJdbcUrl(), "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(String.format(TRUNCATE_SQL, "PUBLIC"));
        }
        finally {
            connection.close();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (this.emf != null && this.emf.isOpen()) {
            this.emf.close();
        }
    }
}

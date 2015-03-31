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

package org.candlepin.gutterball;

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.junit.GutterballLiquibaseResource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {
    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected Injector injector;

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    @Rule
    public static GutterballLiquibaseResource liquibase = new GutterballLiquibaseResource();

    protected ComplianceSnapshotCurator complianceSnapshotCurator;
    protected ConsumerStateCurator consumerStateCurator;

    @Before
    public void init() {
        MapConfiguration config = new MapConfiguration();
        GutterballTestingModule testingModule = new GutterballTestingModule(config);
        injector = Guice.createInjector(testingModule);

        // FIXME Shouldn't have to do this.
        injector.getInstance(EntityManagerFactory.class);
        emf = injector.getProvider(EntityManagerFactory.class).get();
        em = injector.getProvider(EntityManager.class).get();

        complianceSnapshotCurator = injector.getInstance(ComplianceSnapshotCurator.class);
        consumerStateCurator = injector.getInstance(ConsumerStateCurator.class);
    }

    @After
    public void shutdown() {
        injector.getInstance(PersistFilter.class).destroy();
        if (em.isOpen()) {
            em.close();
        }
        if (emf.isOpen()) {
            emf.close();
        }
    }

    /**
     * Helper to open a new db transaction. Pretty simple for now, but may
     * require additional logic and error handling down the road.
     */
    protected void beginTransaction() {
        em.getTransaction().begin();
    }

    /**
     * Helper to commit the current db transaction. Pretty simple for now, but
     * may require additional logic and error handling down the road.

     */
    protected void commitTransaction() {
        em.getTransaction().commit();
    }


}

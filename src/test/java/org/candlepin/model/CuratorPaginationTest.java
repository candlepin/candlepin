/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;



/**
 * This class is used to test the pagination capabilities of the AbstractHibernateCurator. Ideally,
 * this class would create its own entity object and curator for that entity object. However,
 * Hibernate makes it difficult (if not impossible) to auto-detect entities that reside on a
 * classpath different from the one containing persistence.xml. The solution would therefore be to
 * create an EntityManagerFactory with a custom Ejb3Configuration and use addAnnotatedClass to add
 * the test entity object. Unfortunately, since our entity manager is injected by Guice, that would
 * require significant work to create a Guice module to create the EntityManagerFactory using our
 * custom Ejb3Configuration. Ultimately, the most expedient thing to do is to reuse an entity object
 * that Hibernate will pick up automatically.
 */
public class CuratorPaginationTest extends DatabaseTestFixture {

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        for (int i = 0; i < 10; i++) {
            Owner o = new Owner();
            o.setDisplayName(String.valueOf(i));
            o.setKey(String.valueOf(i));
            ownerCurator.create(o);
        }
    }

    private List<Owner> createOwners(int owners) {
        List<Owner> ownerList = new ArrayList<>();
        for (int i = 0; i < owners; i++) {
            Owner o = new Owner();
            o.setDisplayName(String.valueOf(i));
            o.setKey(String.valueOf(i));
            ownerList.add(o);
        }
        return ownerList;
    }

    @Test
    public void testTakeSubList() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        List<Owner> ownerList = createOwners(20);

        List<Owner> results = ownerCurator.takeSubList(req, ownerList);
        assertEquals(10, results.size());
    }

    @Test
    public void testTakeSubListWhenResultsTooSmall() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        List<Owner> ownerList = createOwners(2);
        List<Owner> results = ownerCurator.takeSubList(req, ownerList);
        assertEquals(2, results.size());
    }

    @Test
    public void testTakeSubListWhenRequestOutOfBounds() {
        PageRequest req = new PageRequest();
        req.setPage(5);
        req.setPerPage(10);

        List<Owner> ownerList = createOwners(10);

        List<Owner> results = ownerCurator.takeSubList(req, ownerList);
        assertEquals(0, results.size());
    }
}

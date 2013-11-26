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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.candlepin.model.Owner;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to test the pagination capabilities of the AbstractHibernateCurator.
 * Ideally, this class would create its own entity object and curator for that
 * entity object.  However, Hibernate makes it difficult (if not impossible) to
 * auto-detect entities that reside on a classpath different from the one containing
 * persistence.xml.  The solution would therefore be to create an EntityManagerFactory with
 * a custom Ejb3Configuration and use addAnnotatedClass to add the test entity object.
 * Unfortunately, since our entity manager is injected by Guice, that would require
 * significant work to create a Guice module to create the EntityManagerFactory using
 * our custom Ejb3Configuration.  Ultimately, the most expedient thing to do is
 * to reuse an entity object that Hibernate will pick up automatically.
 */
public class CuratorPaginationTest extends DatabaseTestFixture {

    private Session session;

    @Before
    public void setUp() {
        for (int i = 0; i < 10; i++) {
            Owner o = new Owner();
            o.setDisplayName(String.valueOf(i));
            o.setKey(String.valueOf(i));
            ownerCurator.create(o);
        }

        session = (Session) entityManager().getDelegate();
    }

    @Test
    public void testPaging() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("key");
        pageRequest.setOrder(PageRequest.Order.ASCENDING);
        pageRequest.setPage(3);
        pageRequest.setPerPage(2);

        Page<List<Owner>> p = ownerCurator.listAll(pageRequest);
        assertEquals(Integer.valueOf(10), p.getMaxRecords());

        List<Owner> ownerList = p.getPageData();
        assertEquals(2, ownerList.size());

        // Page 1 is (0, 1); page 2 is (2, 3); page 3 is (4, 5)
        assertEquals("4", ownerList.get(0).getKey());
        assertEquals("5", ownerList.get(1).getKey());

        PageRequest pageRequest2 = p.getPageRequest();
        assertEquals(pageRequest, pageRequest2);
    }

    @Test
    public void testNoPaging() {
        Page<List<Owner>> p = ownerCurator.listAll(null);
        List<Owner> ownerList = p.getPageData();
        assertEquals(10, ownerList.size());
    }

    @Test
    public void testPagingWithCriteria() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("key");
        pageRequest.setOrder(PageRequest.Order.ASCENDING);
        pageRequest.setPage(1);
        pageRequest.setPerPage(2);

        Criteria criteria = session.createCriteria(Owner.class).
            add(Restrictions.gt("key", "5"));

        Page<List<Owner>> p = ownerCurator.listByCriteria(criteria, pageRequest);
        assertEquals(Integer.valueOf(4), p.getMaxRecords());

        List<Owner> ownerList = p.getPageData();
        assertEquals(2, ownerList.size());
        assertEquals("6", ownerList.get(0).getKey());

        PageRequest pageRequest2 = p.getPageRequest();
        assertEquals(pageRequest, pageRequest2);
    }

    @Test
    public void testNoPagingWithCriteria() {
        Criteria criteria = session.createCriteria(Owner.class).
            add(Restrictions.gt("key", "5"));

        Page<List<Owner>> p = ownerCurator.listByCriteria(criteria, null);
        List<Owner> ownerList = p.getPageData();
        assertEquals(4, ownerList.size());
    }

    @Test
    public void testReturnsAllResultsWhenNotPaging() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("key");
        pageRequest.setOrder(PageRequest.Order.ASCENDING);
        assertFalse(pageRequest.isPaging());

        Page<List<Owner>> p = ownerCurator.listAll(pageRequest);
        assertEquals(Integer.valueOf(10), p.getMaxRecords());

        List<Owner> ownerList = p.getPageData();
        assertEquals(10, ownerList.size());
    }

    @Test
    public void testReturnsAllResultsWhenPostFilteringByCriteria() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("key");
        pageRequest.setOrder(PageRequest.Order.ASCENDING);
        pageRequest.setPage(1);
        pageRequest.setPerPage(2);

        Criteria criteria = session.createCriteria(Owner.class).
            add(Restrictions.gt("key", "5"));

        /* Since we are telling listByCriteria that we are doing post-filtering
         * it should return us all results, but ordered and sorted by what we
         * provide
         */
        Page<List<Owner>> p = ownerCurator.listByCriteria(criteria, pageRequest, true);
        assertEquals(Integer.valueOf(4), p.getMaxRecords());

        List<Owner> ownerList = p.getPageData();
        assertEquals(4, ownerList.size());
        assertEquals("6", ownerList.get(0).getKey());

        PageRequest pageRequest2 = p.getPageRequest();
        assertEquals(pageRequest, pageRequest2);
    }

    @Test
    public void testReturnsAllResultsWhenPostFiltering() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setSortBy("key");
        pageRequest.setOrder(PageRequest.Order.ASCENDING);
        pageRequest.setPage(1);
        pageRequest.setPerPage(2);

        /* Since we are telling listByCriteria that we are doing post-filtering
         * it should return us all results, but ordered and sorted by what we
         * provide
         */
        Page<List<Owner>> p = ownerCurator.listAll(pageRequest, true);
        assertEquals(Integer.valueOf(10), p.getMaxRecords());

        List<Owner> ownerList = p.getPageData();
        assertEquals(10, ownerList.size());
        assertEquals("0", ownerList.get(0).getKey());

        PageRequest pageRequest2 = p.getPageRequest();
        assertEquals(pageRequest, pageRequest2);
    }

    private List<Owner> createOwners(int owners) {
        List<Owner> ownerList = new ArrayList<Owner>();
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

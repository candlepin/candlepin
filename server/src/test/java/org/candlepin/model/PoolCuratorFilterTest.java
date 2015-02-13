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
package org.candlepin.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import javax.inject.Inject;


public class PoolCuratorFilterTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;

    private Owner owner;
    private PageRequest req = new PageRequest();
    private Pool searchPool;
    private Pool hidePool;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        searchPool = createSearchPools();
    }

    private Pool createSearchPools() {
        Product searchProduct = new Product("awesomeos-server",
                "Awesome OS Server Premium", owner);
        searchProduct.addAttribute(new ProductAttribute("support_level",
                "CustomSupportLevel"));
        productCurator.create(searchProduct);
        Pool searchPool = createPoolAndSub(owner, searchProduct, 100L,
                TestUtil.createDate(2005, 3, 2), TestUtil.createDate(2050, 3, 2));
        searchPool.addProvidedProduct(TestUtil.createProvidedProduct("101111",
                "Server Bits"));
        searchPool.addProvidedProduct(TestUtil.createProvidedProduct("202222",
                "Containers In This One"));
        searchPool.setContractNumber("mycontract");
        searchPool.setOrderNumber("myorder");
        searchPool.setAttribute("hello", "true");

        poolCurator.create(searchPool);

        // Create another we don't intend to see in the results:
        Product hideProduct = TestUtil.createProduct();
        productCurator.create(hideProduct);
        hidePool = createPoolAndSub(owner, hideProduct, 100L,
                TestUtil.createDate(2005, 3, 2), TestUtil.createDate(2050, 3, 2));
        hidePool.addProvidedProduct(TestUtil.createProvidedProduct("101",
                "Workstation Bits"));
        poolCurator.create(hidePool);

        return searchPool;
    }

    private void searchTest(PoolFilterBuilder filters, int expectedResults, String ... expectedIds) {
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, null, null, false, filters,
            req, false);
        List<Pool> results = page.getPageData();
        assertEquals(expectedResults, results.size());
        for (String id : expectedIds) {
            boolean found = false;
            for (Pool p : results) {
                if (p.getId().equals(id)) {
                    found = true;
                    break;
                }
            }
            assertTrue("Missing expected pool: " + id, found);
        }
    }

    private void searchTest(String searchFor, int expectedResults, String ... expectedIds) {
        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addMatchesFilter(searchFor);
        searchTest(filters, expectedResults, expectedIds);
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameExactMatch() throws Exception {
        searchTest("Awesome OS Server Premium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredForLiteralWildcardCharacters() {
        searchPool.setContractNumber("got_con%tract_");
        poolCurator.merge(searchPool);
        searchTest("got_con%tract_", 1, searchPool.getId());
        searchTest("got_con%tract_*", 1, searchPool.getId());
        searchTest("got_c%ct_", 0, new String [] {});
        searchTest("got_con%tra_t_", 0, new String [] {});
    }

    @Test
    public void availablePoolsObscureFiltering() {
        searchTest("*", 2, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuName() throws Exception {
        searchTest("Awesome OS Server Premium", 1, searchPool.getId());
        searchTest("Server", 0, new String [] {});
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameWildcard() throws Exception {
        searchTest("*Ser*emium", 1, searchPool.getId());
        searchTest("*sER*emIum", 1, searchPool.getId()); // ignore case
        searchTest("*Ser*emium?", 0, new String [] {});
        searchTest("*Ser*emiu?", 1, searchPool.getId());
        searchTest("*Ser*emiumaroni", 0, new String [] {});
        searchTest("*Ser*emium*", 1, searchPool.getId());
        searchTest("*Ser**emium", 1, searchPool.getId());
    }

    @Test
    public void negationOfAKeyValueFilter() throws Exception {
        PoolFilterBuilder filter = new PoolFilterBuilder();
        filter.addAttributeFilter("hello", "true");
        searchTest(filter, 1, searchPool.getId());

        filter = new PoolFilterBuilder();
        filter.addAttributeFilter("hello", "!true");
        searchTest(filter, 1, hidePool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySkuNameSingleCharWildcard() throws Exception {
        searchTest("Awesome OS Ser?er P?emium", 1, searchPool.getId());
        searchTest("*Ser??? P?emium", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySku() throws Exception {
        searchTest("*os-ser*", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByProvidedProducts() throws Exception {
        searchTest("Server Bits", 1, searchPool.getId());
        searchTest("*erv???Bi?s", 1, searchPool.getId());
        searchTest("202222", 1, searchPool.getId());
        searchTest("2?2222", 1, searchPool.getId());
        searchTest("2*2222", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByContractNumber() throws Exception {
        searchTest("mycontract", 1, searchPool.getId());
        searchTest("my*cont??ct", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByOrderNumber() throws Exception {
        searchTest("myorder", 1, searchPool.getId());
        searchTest("my*ord??", 1, searchPool.getId());
    }

    @Test
    public void availablePoolsCanBeFilteredBySupportLevel() throws Exception {
        searchTest("CustomSupportLevel", 1, searchPool.getId());
        searchTest("*Cus*port*", 1, searchPool.getId());
        searchTest("*Cus???Su??ortLevel*", 1, searchPool.getId());
        searchTest("*Self-Service*", 0, new String [] {});
    }
}

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

import org.candlepin.cache.CandlepinCache;
import org.candlepin.test.CacheTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.cache.Cache;
import javax.inject.Inject;


/**
 * Test of ProductCurator with in-memory JCache
 * @author fnguyen
 *
 */
public class ProductCuratorCacheTest extends CacheTestFixture {
    private static Logger log = LoggerFactory.getLogger(ProductCuratorCacheTest.class);
    private Product p1;
    private Product p2;
    private Cache<String, Product> productCache;

    @Inject private CandlepinCache candlepinCache;

    @Before
    public void setUp() {
        p1 = TestUtil.createProduct();
        productCurator.create(p1);
        p2 = TestUtil.createProduct();
        productCurator.create(p2);

        productCache = candlepinCache.getProductCache();
    }


    @Test
    public void productGetsCached() {
        Assert.assertFalse(productCache.containsKey(p1.getUuid()));
        Set<String> uuids = Collections.singleton(p1.getUuid());
        Set<Product> products = productCurator.getProductsByUuidCached(uuids);
        Assert.assertTrue(productCache.containsKey(p1.getUuid()));
        Assert.assertEquals(1, products.size());
    }

    @Test
    public void canMixCachedAndUncachedUuids() {
        Assert.assertFalse(productCache.containsKey(p1.getUuid()));
        Assert.assertFalse(productCache.containsKey(p2.getUuid()));
        productCurator.getProductsByUuidCached(Collections.singleton(p1.getUuid()));
        Assert.assertTrue(productCache.containsKey(p1.getUuid()));

        Set<String> uuids = new HashSet<String>(Arrays.asList(p1.getUuid(), p2.getUuid()));

        Set<Product> products = productCurator.getProductsByUuidCached(uuids);

        Assert.assertTrue(productCache.containsKey(p1.getUuid()));
        Assert.assertTrue(productCache.containsKey(p2.getUuid()));
        Assert.assertEquals(2, products.size());
    }

    @Test
    public void emptyUuidSet() {
        Set<Product> products = productCurator.getProductsByUuidCached(new HashSet<String>());
        Assert.assertEquals(new HashSet<String>(), products);
    }
}

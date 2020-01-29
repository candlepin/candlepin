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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.jackson.DynamicPropertyFilter;
import org.candlepin.common.jackson.HateoasBeanPropertyFilter;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.jackson.PoolEventFilter;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import javax.inject.Inject;
import javax.persistence.PersistenceException;



public class PoolTest extends DatabaseTestFixture {

    public static final String POOL_JSON_BASE = "{\"id\": \"5\", \"owner\": {}, \"activeSubscription\": tr" +
        "ue, \"subscriptionId\": \"3\", \"subscriptionSubKey\": null, \"sourceStackId\": null, \"sourceCon" +
        "sumer\": {\"id\": \"5\", \"uuid\": \"\", \"name\": \"10023\", \"username\": null, \"entitlementSt" +
        "atus\": \"valid\", \"serviceLevel\": \"\", \"releaseVer\": {\"releaseVer\": null }, \"type\": {\"" +
        "id\": \"1004\", \"label\": \"hypervisor\", \"manifest\": false }, \"owner\": {}, \"environment\":" +
        " {\"owner\": {}, \"name\": \"Library\", \"description\": null, \"id\": \"2\", \"environmentConten" +
        "t\": [{\"id\": \"023947982374\", \"contentId\": \"166\", \"enabled\": null }, {\"id\": \"11\", \"" +
        "contentId\": \"168\", \"enabled\": null }, {\"id\": \"192837123\", \"contentId\": \"867\", \"enab" +
        "led\": null } ] }, \"entitlementCount\": 1, \"facts\": {}, \"lastCheckin\": 1381236857266, \"inst" +
        "alledProducts\": [], \"canActivate\": false, \"guestIds\": [], \"capabilities\": [], \"autoheal\"" +
        ": true, \"href\": \"\\/consumers\\/4\"}, \"quantity\": -1, \"startDate\": 1377057600000, \"endDat" +
        "e\": 1471751999000, \"productId\": \"MYSKU\", \"providedProducts\": [], \"restrictedToUsername\":" +
        " null, \"contractNumber\": \"2\", \"accountNumber\": \"1\", \"orderNumber\": null, \"consumed\": " +
        "1, \"exported\": 0, \"productName\": \"Awesome OS Enterprise Server\", \"calculatedAttributes\": " +
        "null, \"type\": \"ENTITLEMENT_DERIVED\", \"href\": \"\\/pools\\/5\", \"stacked\": false, \"stackI" +
        "d\": null, \"product_list\": []";

    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private CandlepinPoolManager poolManager;

    private ObjectMapper mapper;

    private Pool pool;
    private Product prod1;
    private Product prod2;
    private Owner owner;
    private Consumer consumer;
    private Subscription subscription;

    @BeforeEach
    public void createObjects() {
        this.mapper = new ObjectMapper();
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider = filterProvider.addFilter("PoolFilter", new PoolEventFilter());
        filterProvider = filterProvider.addFilter("OwnerFilter", new HateoasBeanPropertyFilter());
        filterProvider.setDefaultFilter(new DynamicPropertyFilter());
        this.mapper.setFilters(filterProvider);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        beginTransaction();

        try {
            owner = new Owner("testowner");
            ownerCurator.create(owner);

            prod1 = this.createProduct(owner);
            prod2 = this.createProduct(owner);

            prod1.setProvidedProducts(Arrays.asList(prod2));

            pool = TestUtil.createPool(owner, prod1, 1000);
            subscription = TestUtil.createSubscription(owner, prod1);
            subscription.setId(Util.generateDbUUID());

            pool.setSourceSubscription(new SourceSubscription(subscription.getId(), "master"));
            poolCurator.create(pool);
            owner = pool.getOwner();

            consumer = this.createConsumer(owner);

            productCurator.create(prod1);
            poolCurator.create(pool);

            commitTransaction();
        }
        catch (RuntimeException e) {
            rollbackTransaction();
            throw e;
        }
    }

    @Test
    public void testCreate() {
        Pool lookedUp = this.getEntityManager().find(Pool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod1.getId(), lookedUp.getProductId());
    }

    @Test
    public void testCreateWithDerivedProvidedProducts() {
        Product derivedProd = this.createProduct(owner);
        Product derivedProvidedProduct = this.createProduct(owner);

        Pool p = TestUtil.createPool(owner, prod1, 1000);
        p.getProduct().setProvidedProducts(Arrays.asList(prod2));

        derivedProd.setProvidedProducts(Arrays.asList(derivedProvidedProduct));
        p.setDerivedProduct(derivedProd);

        poolCurator.create(p);

        Pool lookedUp = this.getEntityManager().find(Pool.class, p.getId());
        assertEquals(1, lookedUp.getProduct().getProvidedProducts().size());
        assertEquals(prod2.getId(), lookedUp.getProduct().getProvidedProducts().iterator().next().getId());
        assertEquals(1, lookedUp.getDerivedProduct().getProvidedProducts().size());
        assertEquals(derivedProvidedProduct.getId(),
            lookedUp.getDerivedProduct().getProvidedProducts().iterator().next().getId());
    }

    @Test
    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPool(
            owner, prod1, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testIsOverflowing() {
        Pool duplicatePool = createPool(
            owner, prod1, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        assertFalse(duplicatePool.isOverflowing());
    }

    @Test
    public void testQuantityAdjust() {
        Pool p = new Pool();
        p.setQuantity(10L);
        Long q = p.adjustQuantity(2L);
        assertEquals((Long) 12L, (Long) q);

        q = p.adjustQuantity(-2L);
        assertEquals((Long) 8L, (Long) q);
    }

    @Test
    public void testQuantityAdjustNonNegative() {
        Pool p = new Pool();
        p.setQuantity(0L);
        Long q = p.adjustQuantity(-2L);
        assertEquals((Long) 0L, (Long) q);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = this.createProduct(owner);

        Pool unlimitedPool = createPool(
            owner, newProduct, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable(1));
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Long numAvailEntitlements = 1L;
        Product newProduct = this.createProduct(owner);

        Pool consumerPool = createPool(owner, newProduct, numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));

        consumerPool = poolCurator.create(consumerPool);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(consumerPool.getId(), 1);
        poolManager.entitleByPools(consumer, pQs);

        consumerPool = poolCurator.get(consumerPool.getId());
        assertFalse(consumerPool.entitlementsAvailable(1));
        assertEquals(1, consumerPool.getEntitlements().size());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Long numAvailEntitlements = 1L;

        Product newProduct = this.createProduct(owner);

        Pool consumerPool = createPool(
            owner,
            newProduct,
            numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30)
        );

        poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(consumerPool.getId(), 1);
        poolManager.entitleByPools(consumer, pQs);

        assertEquals(1, consumerCurator.get(consumer.getId()).getEntitlements().size());
    }

    // test subscription product changed exception

    @Test
    public void testLookupPoolsProvidingProduct() {

        Product childProduct = this.createProduct("2", "product-2", owner);

        Product parentProduct = TestUtil.createProduct("1", "product-1");
        parentProduct.setProvidedProducts(Arrays.asList(childProduct));

        parentProduct = this.createProduct(parentProduct, owner);
        Pool pool = TestUtil.createPool(owner, parentProduct, 5);
        poolCurator.create(pool);


        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            null, owner, childProduct.getId(), null
        );
        assertEquals(1, results.size());
        assertEquals(pool.getId(), results.get(0).getId());
    }

    /**
     * After creating a new pool object, test is made to determine whether
     * the created and updated values are present and not null.
     */
    @Test
    public void testCreationTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        poolCurator.create(pool);
        assertNotNull(pool.getCreated());
    }

    @Test
    public void testInitialUpdateTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        pool = poolCurator.create(pool);
        assertNotNull(pool.getUpdated());
    }

    /**
     * After updating an existing pool object, test is made to determine whether
     * the updated value has changed
     */
    @Test
    public void testSubsequentUpdateTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        pool = poolCurator.create(pool);

        // set updated to 10 minutes ago
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        pool.setUpdated(calendar.getTime());

        Date updated = (Date) pool.getUpdated().clone();
        pool.setQuantity(23L);
        pool = poolCurator.merge(pool);

        assertFalse(updated.getTime() == pool.getUpdated().getTime());
    }

    @Test
    public void testProvidedProductImmutability() {
        Product parentProduct = TestUtil.createProduct("1", "product-1");
        Product providedProduct = this.createProduct("provided", "Child 1", owner);
        parentProduct.setProvidedProducts(Arrays.asList(providedProduct));

        Product childProduct1 = this.createProduct("child1", "child1", owner);

        parentProduct = this.createProduct(parentProduct, owner);
        Pool pool = TestUtil.createPool(owner, parentProduct, 5);
        poolCurator.create(pool);
        pool = poolCurator.get(pool.getId());
        assertEquals(1, pool.getProduct().getProvidedProducts().size());

        // provided products are immutable set.
        pool.getProduct().addProvidedProduct(childProduct1);
        Pool finalPool = pool;
        Assertions.assertThrows(PersistenceException.class, () ->poolCurator.merge(finalPool));
    }

    // sunny test - real rules not invoked here. Can only be sure the counts are recorded.
    // Rule tests already exist for quantity filter.
    // Will use spec tests to see if quantity rules are followed in this scenario.
    @Test
    public void testEntitlementQuantityChange() throws EntitlementRefusedException {
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(pool.getId(), 3);
        List<Entitlement> entitlements = poolManager.entitleByPools(consumer, pQs);

        Entitlement ent = entitlements.get(0);
        assertTrue(ent.getQuantity() == 3);
        poolManager.adjustEntitlementQuantity(consumer, ent, 5);
        Entitlement ent2 = entitlementCurator.get(ent.getId());
        assertTrue(ent2.getQuantity() == 5);
        Pool pool2 = poolCurator.get(pool.getId());
        assertTrue(pool2.getConsumed() == 5);
        assertTrue(pool2.getEntitlements().size() == 1);
    }

    @Test
    public void testPoolType() {
        pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        assertEquals(PoolType.BONUS, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        assertEquals(PoolType.ENTITLEMENT_DERIVED, pool.getType());

        pool.setSourceEntitlement(null);
        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.STACK_DERIVED, pool.getType());

        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        pool.setSourceStack(null);
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.removeAttribute(Pool.Attributes.DERIVED_POOL);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceEntitlement(null);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.NORMAL, pool.getType());
    }

    @Test
    public void testSetSubIdFromValue() {
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be unchanged
        assertEquals("master", pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdNullRemoval() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubIdNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId("");
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyFromValue() {
        pool.setSubscriptionSubKey("testkey");
        assertEquals("testkey", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be unchanged
        assertEquals(subscription.getId(), pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionSubKey("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyNullRemoval() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey("");
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testDeserializePoolAttributesJsonV1() throws Exception {
        String attributes = "\"attributes\": [" +
            "    {" +
            "        \"name\" : \"attrib-1\"," +
            "        \"value\" : \"value-1\"," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }," +
            "    {" +
            "        \"name\" : \"attrib-2\"," +
            "        \"value\" : \"value-2\"," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }," +
            "    {" +
            "        \"name\" : 3," +
            "        \"value\" : 3," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }" +
            "]," +
            "\"productAttributes\": [" +
            "    {" +
            "        \"name\" : \"prod_attrib-1\"," +
            "        \"value\" : \"prod_value-1\"," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }," +
            "    {" +
            "        \"name\" : \"prod_attrib-2\"," +
            "        \"value\" : \"prod_value-2\"," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }," +
            "    {" +
            "        \"name\" : 3," +
            "        \"value\" : 3," +
            "        \"entityVersion\" : 1498458083," +
            "        \"created\" : \"2016-09-07T15:08:13+0000\"," +
            "        \"updated\" : \"2016-09-07T15:08:13+0000\"" +
            "    }" +
            "]";

        Map<String, String> expectedAttrib = new HashMap<>();
        expectedAttrib.put("attrib-1", "value-1");
        expectedAttrib.put("attrib-2", "value-2");
        expectedAttrib.put("3", "3");

        Map<String, String> expectedProdAttrib = new HashMap<>();
        expectedProdAttrib.put("prod_attrib-1", "prod_value-1");
        expectedProdAttrib.put("prod_attrib-2", "prod_value-2");
        expectedProdAttrib.put("3", "3");

        Pool pool = this.mapper.readValue(POOL_JSON_BASE + "," + attributes + "}", Pool.class);

        assertEquals(expectedAttrib, pool.getAttributes());
        assertEquals(expectedProdAttrib, pool.getProductAttributes());
    }

    @Test
    public void testDeserializePoolAttributesJsonV2() throws Exception {
        String attributes = "\"attributes\": {" +
            "    \"attrib-1\": \"value-1\"," +
            "    \"attrib-2\": \"value-2\"," +
            "    \"attrib-3\": 3" +
            "}," +
            "\"productAttributes\": {" +
            "    \"prod_attrib-1\": \"prod_value-1\"," +
            "    \"prod_attrib-2\": \"prod_value-2\"," +
            "    \"prod_attrib-3\": 3" +
            "}";

        Map<String, String> expectedAttrib = new HashMap<>();
        expectedAttrib.put("attrib-1", "value-1");
        expectedAttrib.put("attrib-2", "value-2");
        expectedAttrib.put("attrib-3", "3");

        Map<String, String> expectedProdAttrib = new HashMap<>();
        expectedProdAttrib.put("prod_attrib-1", "prod_value-1");
        expectedProdAttrib.put("prod_attrib-2", "prod_value-2");
        expectedProdAttrib.put("prod_attrib-3", "3");

        Pool pool = this.mapper.readValue(POOL_JSON_BASE + "," + attributes + "}", Pool.class);

        assertEquals(expectedAttrib, pool.getAttributes());
        assertEquals(expectedProdAttrib, pool.getProductAttributes());
    }

    @Test
    public void testSerializePoolAttributes() throws Exception {
        String expectedHeader = "\"attributes\":[{";
        String expectedValue1 = "\"name\":\"attrib-1\",\"value\":\"value-1\"";
        String expectedValue2 = "\"name\":\"attrib-2\",\"value\":\"value-2\"";
        String expectedValue3 = "\"name\":\"attrib-3\",\"value\":\"3\"";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "3");

        this.pool.setAttributes(attributes);

        String output = this.mapper.writeValueAsString(pool);

        // Since the attributes are stored as a map, we can't guarantee any specific printed order.
        // To deal with this, we separate the value and each header, then verify them individually.
        assertTrue(output.contains(expectedHeader));
        assertTrue(output.contains(expectedValue1));
        assertTrue(output.contains(expectedValue2));
        assertTrue(output.contains(expectedValue3));
    }
}

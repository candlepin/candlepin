/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.BadRequestException;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.ForbiddenException;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * ConsumerResourceTest
 */
public class ConsumerResourceTest extends DatabaseTestFixture {
    
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer name";
    
    private ConsumerType standardSystemType;
    private Consumer consumer;
    private Product product;
    private Pool pool;
    private Pool fullPool;
    
    private ConsumerResource consumerResource;
    private Owner owner;

    @Before
    public void setUp() {

        consumerResource = injector.getInstance(ConsumerResource.class);
        standardSystemType = consumerTypeCurator.create(
                new ConsumerType("standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);
        
        ConsumerType type = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(type);
        
        consumer = TestUtil.createConsumer(type, owner);
        consumerCurator.create(consumer);
        
        product = TestUtil.createProduct();
        productCurator.create(product);
        
        pool = createPoolAndSub(owner, product.getId(), new Long(10),
            TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        poolCurator.create(pool);

        fullPool = createPoolAndSub(owner, product.getId(), new Long(10),
            TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        fullPool.setConsumed(new Long(10));
        poolCurator.create(fullPool);
    }
    
    // TODO: Test no such consumer type.
    
//    @Test
//    public void testDelete() {
//        Consumer c = TestUtil.createConsumer();
//        String uuid = c.getUuid();
//        ConsumerResource capi = new ConsumerResource();
//        assertNotNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//        capi.delete(uuid);
//        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//    }

    @Test
    public void testCreateConsumer() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, null, standardSystemType);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);

        Consumer submitted  = consumerResource.create(toSubmit);
        
        assertNotNull(submitted);
        assertNotNull(submitted);
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, submitted.getMetadataField(METADATA_NAME));
    }
    
    @Test
    public void testCreateConsumerWithUUID() {
        String uuid = "Jar Jar Binks";
        Consumer toSubmit = new Consumer(CONSUMER_NAME, null, standardSystemType);
        toSubmit.setUuid(uuid);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);        

        Consumer submitted  = consumerResource.create(toSubmit);
        
        assertNotNull(submitted);
        assertNotNull(submitted);
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertNotNull(consumerCurator.lookupByUuid(uuid));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, submitted.getMetadataField(METADATA_NAME));   
        assertEquals("The Uuids do not match", uuid, submitted.getUuid());
        
        //The second post should fail because of constraint failures
        try {
            consumerResource.create(toSubmit);
        } 
        catch (BadRequestException e) {
            // Good
            return;
        }
        fail("No exception was thrown");
    }    
    
    @Ignore // TODO: implement 'delete' functionality
    public void testDeleteResource() {
        Consumer created = consumerCurator.create(new Consumer(CONSUMER_NAME,
                owner, standardSystemType));
        //consumerResource.delete(created.getUuid());
        
        assertNull(consumerCurator.find(created.getId()));
    }
    
    @Test
    public void testEntitle() throws Exception {
        //Entitlement result = 
        consumerResource.bind(
            consumer.getUuid(), null, null, product.getLabel());
        
        consumer = consumerCurator.lookupByUuid(consumer.getUuid());
        assertEquals(1, consumer.getEntitlements().size());
        
        pool = poolCurator.find(pool.getId());
        assertEquals(new Long(1), pool.getConsumed());
    }
    
    @Test(expected = RuntimeException.class)
    public void testMaxMembership() {
        // 10 entitlements available, lets try to entitle 11 consumers.
        for (int i = 0; i < pool.getQuantity(); i++) {
            Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
            consumerCurator.create(c);
            consumerResource.bind(c.getUuid(), null, null, product.getLabel());
        }
        
        // Now for the 11th:
        Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
        consumerCurator.create(c);
        consumerResource.bind(c.getUuid(), null, null, product.getLabel());
    }
    
    @Test(expected = RuntimeException.class)
    public void testEntitlementsHaveExpired() {
        dateSource.currentDate(TestDateUtil.date(2030, 1, 13));
        consumerResource.bind(consumer.getUuid(), null, null,
            product.getLabel());
    }
    
    @Test
    public void testBindByPool() throws Exception {
        List<Entitlement> resultList =
            consumerResource.bind(
                consumer.getUuid(), pool.getId(), null, null);

        consumer = consumerCurator.lookupByUuid(consumer.getUuid());
        assertEquals(1, consumer.getEntitlements().size());

        pool = poolCurator.find(pool.getId());
        assertEquals(new Long(1), pool.getConsumed());
        for (Entitlement ent : resultList) {
            assertEquals(pool.getId(), ent.getPool().getId());
        }
    }

    @Test(expected = ForbiddenException.class)
    public void testBindByPoolNoFreeEntitlements() throws Exception {
        consumerResource.bind(
            consumer.getUuid(), fullPool.getId(), null, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        consumerResource.bind(
            consumer.getUuid(), pool.getId(), null, product.getId());
    }

    @Test(expected = BadRequestException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        consumerResource.bind(
            "notarealuuid", pool.getId(), null, null);
    }
}

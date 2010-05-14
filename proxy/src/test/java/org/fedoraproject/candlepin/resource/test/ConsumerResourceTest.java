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

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.CertificateSerialDto;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * ConsumerResourceTest
 */
public class ConsumerResourceTest extends DatabaseTestFixture {
    
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer name";
    private static final String NON_EXISTENT_CONSUMER = "i don't exist";
    
    private ConsumerType standardSystemType;
    private Consumer consumer;
    private Product product;
    private Pool pool;
    private Pool fullPool;
    
    private ConsumerResource consumerResource;
    private Principal principal;
    private Owner owner;

    @Before
    public void setUp() {

        principal = injector.getInstance(Principal.class);
        consumerResource = injector.getInstance(ConsumerResource.class);
        standardSystemType = consumerTypeCurator.create(
                new ConsumerType("standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);
        
        consumer = TestUtil.createConsumer(standardSystemType, owner);
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
    
    @Test
    public void testGetCertSerials() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        List<CertificateSerialDto> serials = consumerResource.
            getEntitlementCertificateSerials(consumer.getUuid());
        assertEquals(1, serials.size());
    }

    @Test
    public void testGetCerts() {
       
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        List<EntitlementCertificate> serials = consumerResource.
            getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());
    }

    @Test
    public void testGetSerialFiltering() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        List<EntitlementCertificate> serials = consumerResource.
            getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(4, serials.size());

        BigInteger serial1 = serials.get(0).getSerial();
        BigInteger serial2 = serials.get(3).getSerial();

        String serialsToFilter =  serial1.toString() + "," + serial2.toString();

        serials = consumerResource.getEntitlementCertificates(consumer.getUuid(),
            serialsToFilter);
        assertEquals(2, serials.size());
        assertEquals(serial1, serials.get(0).getSerial());
        assertEquals(serial2, serials.get(1).getSerial());
    }

    @Test
    public void testCreateConsumer() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, null, standardSystemType);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);

        Consumer submitted  = consumerResource.create(toSubmit, 
            new UserPrincipal("someuser", owner, Collections.singletonList(Role.OWNER_ADMIN)));
        
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
        assertNull(toSubmit.getId());
        toSubmit.setUuid(uuid);
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);        

        Consumer submitted  = consumerResource.create(toSubmit, 
            new UserPrincipal("someuser", owner, Collections.singletonList(Role.OWNER_ADMIN)));
        assertNull(toSubmit.getId());
        assertNotNull(submitted);
        assertNotNull(submitted);
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertNotNull(consumerCurator.lookupByUuid(uuid));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, submitted.getMetadataField(METADATA_NAME));   
        assertEquals("The Uuids do not match", uuid, submitted.getUuid());
        
        //The second post should fail because of constraint failures
        try {
            consumerResource.create(toSubmit, principal);
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
        assertEquals(1, resultList.size());
        assertEquals(pool.getId(), resultList.get(0).getPool().getId());
        assertEquals(1, entCertCurator.listForEntitlement(resultList.get(0)).size());
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

    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        consumerResource.bind(
            "notarealuuid", pool.getId(), null, null);
    }

    @Test
    public void testRegisterWithConsumerId() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, null, standardSystemType);
        toSubmit.setUuid("1023131");
        toSubmit.getFacts().put(METADATA_NAME, METADATA_VALUE);

        Consumer submitted  = consumerResource.create(toSubmit, 
            new UserPrincipal("someuser", owner, Collections.singletonList(Role.OWNER_ADMIN)));

        assertNotNull(submitted);
        assertEquals(toSubmit.getUuid(), submitted.getUuid());
        assertNotNull(consumerCurator.find(submitted.getId()));
        assertEquals(standardSystemType.getLabel(), submitted.getType().getLabel());
        assertEquals(METADATA_VALUE, submitted.getMetadataField(METADATA_NAME));

        // now pass in consumer type with null id just like the client would
        ConsumerType type = new ConsumerType(standardSystemType.getLabel());
        assertNull(type.getId());
        Consumer nulltypeid = new Consumer(CONSUMER_NAME, null, type);
        submitted = consumerResource.create(nulltypeid, 
            new UserPrincipal("someuser", owner, Collections.singletonList(Role.OWNER_ADMIN)));
        assertNotNull(submitted);
        assertEquals(nulltypeid.getUuid(), submitted.getUuid());
        assertNotNull(submitted.getType().getId());
    }
    
    @Test
    public void unbindBySerialWithExistingCertificateShouldPass() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        List<EntitlementCertificate> serials = consumerResource.
            getEntitlementCertificates(consumer.getUuid(), null);
        assertEquals(1, serials.size());

        consumerResource.unbindBySerial(consumer.getUuid(),
            serials.get(0).getSerial().longValue());
        assertEquals(0, consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }
    
    @Test(expected = NotFoundException.class)
    public void unbindByInvalidSerialShouldFail() {
        consumerResource.unbindBySerial(consumer.getUuid(), new Long("1234"));
    }
    
    @Test(expected = NotFoundException.class)
    public void unbindBySerialWithInvalidUuidShouldFail() {
        consumerResource.unbindBySerial(NON_EXISTENT_CONSUMER, new Long("1234"));
    }
    
    @Test
    public void testCannotGetAnotherConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        assertEquals(0, 
            consumerResource.getEntitlementCertificates(consumer.getUuid(), null).size());
    }

    @Test
    public void testCanGetOwnedConsumersCerts() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        
        setupPrincipal(new ConsumerPrincipal(consumer));
        
        assertEquals(3, 
            consumerResource.getEntitlementCertificates(consumer.getUuid(), null).size());
    }
    
    @Test(expected = ForbiddenException.class)
    public void canNotDeleteConsumerOtherThanSelf() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        consumerResource.deleteConsumer(consumer.getUuid());
    }
    
    @Test
    public void consumerCanDeleteSelf() {
        setupPrincipal(new ConsumerPrincipal(consumer));
        consumerResource.deleteConsumer(consumer.getUuid());
        assertEquals(null, consumerCurator.lookupByUuid(consumer.getUuid()));
    }
    
    @Test
    public void testCanGetConsumersCerts() {
        securityInterceptor.enable();
        crudInterceptor.enable();
        setupPrincipal(owner, Role.OWNER_ADMIN);
        
        assertEquals(0, consumerResource.getEntitlementCertificates(
            consumer.getUuid(), null).size());
    }
    
    @Test
    public void testCannotGetAnotherOwnersConsumersCerts() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        
        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);
        
        securityInterceptor.enable();
        crudInterceptor.enable();
        setupPrincipal(evilOwner, Role.OWNER_ADMIN);
        
        assertEquals(0, consumerResource.getEntitlementCertificates(
            consumer.getUuid(), null).size());
    }
    
    @Test(expected = ForbiddenException.class)
    public void testConsumerCannotListAllConsumers() {
        securityInterceptor.enable();
        crudInterceptor.enable();
        setupPrincipal(new ConsumerPrincipal(consumer));
        
        consumerResource.list();
    }
    
    @Test
    public void consumerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        
        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        assertEquals(3, 
            consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }
    
    @Test
    public void consumerShouldNotSeeAnotherConsumersEntitlements() {
        Consumer evilConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(evilConsumer);
        
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(evilConsumer.getUuid(), pool.getId(), null, null);
        
        setupPrincipal(new ConsumerPrincipal(evilConsumer));
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        assertEquals(0, 
            consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }
    
    @Test
    public void ownerShouldNotSeeOtherOwnerEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        
        Owner evilOwner = ownerCurator.create(new Owner("another-owner"));
        ownerCurator.create(evilOwner);
        
        securityInterceptor.enable();
        crudInterceptor.enable();
        setupPrincipal(evilOwner, Role.OWNER_ADMIN);
        
        assertEquals(0, consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }
    
    @Test
    public void ownerShouldSeeOwnEntitlements() {
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        consumerResource.bind(consumer.getUuid(), pool.getId(), null, null);
        
        securityInterceptor.enable();
        crudInterceptor.enable();
        setupPrincipal(owner, Role.OWNER_ADMIN);
        
        assertEquals(3, consumerResource.listEntitlements(consumer.getUuid(), null).size());
    }
}

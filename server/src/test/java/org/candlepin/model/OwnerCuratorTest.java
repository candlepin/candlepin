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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;



/**
 *
 */
public class OwnerCuratorTest extends DatabaseTestFixture {

    @Test
    public void basicImport() {
        Owner owner = new Owner("testing");
        owner.setId("testing-primary-key");

        this.ownerCurator.replicate(owner);

        assertEquals("testing", this.ownerCurator.get("testing-primary-key").getKey());
    }

    @Test
    public void primaryKeyCollision() {
        Owner owner = new Owner("dude");
        owner = this.ownerCurator.create(owner);

        Owner newOwner = new Owner("someoneElse");
        newOwner.setId(owner.getId());

        this.ownerCurator.replicate(newOwner);
        assertThrows(RollbackException.class, () -> this.commitTransaction());
    }

    @Test
    public void upstreamUuidConstraint() {
        UpstreamConsumer uc = new UpstreamConsumer("sameuuid");

        Owner owner1 = new Owner("owner1");
        owner1.setUpstreamConsumer(uc);
        Owner owner2 = new Owner("owner2");
        owner2.setUpstreamConsumer(uc);

        assertThrows(PersistenceException.class, () -> ownerCurator.create(owner1));
    }

    private void associateProductToOwner(Owner o, Product p) {
        Pool pool = TestUtil.createPool(o, p, 5);
        poolCurator.create(pool);

        Consumer c = createConsumer(o);
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(o, c, pool, cert);
        entitlementCurator.create(ent);
    }

    @Test
    public void testGetMultipleOwnersByMultipleActiveProducts() {
        Owner owner = createOwner();
        Owner owner2 = createOwner();

        Product provided = this.createProduct(owner);
        Product product = TestUtil.createProduct("productId1", "productName1");
        product.setProvidedProducts(Arrays.asList(provided));
        Product finalProduct1 = this.createProduct(product, owner);

        Product provided2 = this.createProduct(owner2);
        Product product2 = TestUtil.createProduct("productId1", "productName1");
        product2.setProvidedProducts(Arrays.asList(provided));
        Product finalProduct2 = this.createProduct(product2, owner);

        associateProductToOwner(owner, finalProduct1);
        associateProductToOwner(owner2, finalProduct2);

        List<String> productIds = new ArrayList<>();
        productIds.add(provided.getId());
        productIds.add(provided2.getId());
        List<Owner> results = ownerCurator.getOwnersByActiveProduct(productIds).list();

        assertEquals(2, results.size());
    }

    @Test
    public void testGetOwnerByActiveProduct() {
        Owner owner = createOwner();

        Product provided = this.createProduct(owner);
        Product product = TestUtil.createProduct("productId1", "productName1");
        product.setProvidedProducts(Arrays.asList(provided));
        Product finalProduct1 = this.createProduct(product, owner);

        associateProductToOwner(owner, finalProduct1);

        List<String> productIds = new ArrayList<>();
        productIds.add(provided.getId());
        List<Owner> results = ownerCurator.getOwnersByActiveProduct(productIds).list();

        assertEquals(1, results.size());
        assertEquals(owner, results.get(0));
    }

    @Test
    public void testGetOwnersByActiveProductWithExpiredEntitlements() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product provided = TestUtil.createProduct();

        product.addProvidedProduct(provided);

        provided = this.createProduct(provided, owner);
        product = this.createProduct(product, owner);

        // Create pool with end date in the past.
        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(5L)
            .setStartDate(TestUtil.createDate(2009, 11, 30))
            .setEndDate(TestUtil.createDate(2010, 11, 30))
            .setContractNumber("SUB234598S")
            .setAccountNumber("ACC123")
            .setOrderNumber("ORD222");

        poolCurator.create(pool);

        Consumer consumer = createConsumer(owner);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(ent);

        List<String> productIds = new ArrayList<>();
        productIds.add(provided.getId());
        List<Owner> results = ownerCurator.getOwnersByActiveProduct(productIds).list();

        assertTrue(results.isEmpty());
    }

    @Test
    public void getByUpstreamUuid() {
        Owner owner = new Owner("owner1");
        // setup some data
        owner = ownerCurator.create(owner);
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        consumerTypeCurator.create(type);
        UpstreamConsumer uc = new UpstreamConsumer("test-upstream-consumer", owner, type, "someuuid");
        owner.setUpstreamConsumer(uc);
        ownerCurator.merge(owner);

        // ok let's see if this works
        Owner found = ownerCurator.getByUpstreamUuid("someuuid");

        // verify all is well in the world
        assertNotNull(found);
        assertEquals(owner.getId(), found.getId());
    }

    @Test
    public void getConsumerUuids() {
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(type);

        Owner owner = new Owner("owner");
        Owner otherOwner = new Owner("other owner");

        ownerCurator.create(owner);
        ownerCurator.create(otherOwner);

        Consumer c1 = new Consumer("name1", "uname1", owner, type);
        Consumer c2 = new Consumer("name2", "uname2", owner, type);
        Consumer c3 = new Consumer("name3", "uname3", otherOwner, type);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);

        List<String> result = ownerCurator.getConsumerUuids(owner).list();
        assertEquals(2, result.size());
        assertTrue(result.contains(c1.getUuid()));
        assertTrue(result.contains(c2.getUuid()));
        assertFalse(result.contains(c3.getUuid()));
    }

    private List<Owner> setupDBForLookupOwnersForProductTests() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Owner owner3 = this.createOwner("owner3");

        Product prod1o1 = this.createProduct("p1", "p1", owner1);
        Product prod1o2 = this.createProduct("p1", "p1", owner2);
        Product prod1o3 = this.createProduct("p1", "p1", owner3);

        Product prod2o1 = this.createProduct("p2", "p2", owner1);
        Product prod2o2 = this.createProduct("p2", "p2", owner2);

        Product prod3o2 = this.createProduct("p3", "p3", owner2);
        Product prod3o3 = this.createProduct("p3", "p3", owner3);

        Product prod4 = TestUtil.createProduct("p4", "p4");
        Product prod4d = TestUtil.createProduct("p4d", "p4d");
        Product prod5 = TestUtil.createProduct("p5", "p5");
        Product prod5d = TestUtil.createProduct("p5d", "p5d");
        Product prod6 = TestUtil.createProduct("p6", "p6");
        Product prod6d = TestUtil.createProduct("p6d", "p6d");

        prod4.setDerivedProduct(prod4d);
        prod4.addProvidedProduct(prod1o1);
        prod4d.addProvidedProduct(prod2o1);
        prod4d = this.createProduct(prod4d, owner1);
        prod4 = this.createProduct(prod4, owner1);

        prod5.setDerivedProduct(prod5d);
        prod5.addProvidedProduct(prod1o2);
        prod5.addProvidedProduct(prod2o2);
        prod5d.addProvidedProduct(prod3o2);
        prod5d = this.createProduct(prod5d, owner2);
        prod5 = this.createProduct(prod5, owner2);

        prod6.setDerivedProduct(prod6d);
        prod6.addProvidedProduct(prod1o3);
        prod6d.addProvidedProduct(prod3o3);
        prod6d = this.createProduct(prod6d, owner3);
        prod6 = this.createProduct(prod6, owner3);

        Pool pool1 = new Pool()
            .setOwner(owner1)
            .setProduct(prod4)
            .setStartDate(TestUtil.createDate(2000, 1, 1))
            .setEndDate(TestUtil.createDate(3000, 1, 1))
            .setQuantity(5L);

        Pool pool2 = new Pool()
            .setOwner(owner2)
            .setProduct(prod5)
            .setStartDate(TestUtil.createDate(1000, 1, 1))
            .setEndDate(TestUtil.createDate(2000, 1, 1))
            .setQuantity(5L);

        Pool pool3 = new Pool()
            .setOwner(owner3)
            .setProduct(prod6)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setQuantity(5L);

        this.poolCurator.create(pool1);
        this.poolCurator.create(pool2);
        this.poolCurator.create(pool3);

        return Arrays.asList(owner1, owner2, owner3);
    }

    @Test
    public void testGetOwnersWithProducts() {
        List<Owner> owners = this.setupDBForLookupOwnersForProductTests();
        Owner owner1 = owners.get(0);
        Owner owner2 = owners.get(1);
        Owner owner3 = owners.get(2);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("p4")).list();
        assertEquals(Arrays.asList(owner1), owners);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("p5d")).list();
        assertEquals(Arrays.asList(owner2), owners);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("p1")).list();
        assertEquals(Arrays.asList(owner1, owner2, owner3), owners);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("p3")).list();
        assertEquals(Arrays.asList(owner2, owner3), owners);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("p4", "p6")).list();
        assertEquals(Arrays.asList(owner1, owner3), owners);

        owners = this.ownerCurator.getOwnersWithProducts(Arrays.asList("nope")).list();
        assertEquals(0, owners.size());
    }

    @Test
    public void testLockAndLoadByKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(ownerKey);

        assertNotNull(actual);
        assertEquals(ownerKey, actual.getKey());
    }

    @Test
    public void testLockAndLoadByKeyDoesntLoadBadKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(ownerKey + "-bad");

        assertNull(actual);
    }

    @Test
    public void testLockAndLoadByKeyDoesntLoadNullKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(null);

        assertNull(actual);
    }

    @Test
    public void fetchesOwnerContentAccess() {
        String expected = "entitlement";
        Owner owner = this.createOwner("test_key");
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        OwnerContentAccess actual = this.ownerCurator.getOwnerContentAccess(owner.getKey());

        assertEquals(expected, actual.getContentAccessMode());
        assertEquals(expected, actual.getContentAccessModeList());
    }

    @Test
    public void throwsWhenOwnerMissing() {
        String unknownOwnerKey = "test_key";

        Assertions.assertThrows(OwnerNotFoundException.class,
            () -> this.ownerCurator.getOwnerContentAccess(unknownOwnerKey));
    }

}

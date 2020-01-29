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

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        Product product = this.createProduct(owner);
        Product provided = this.createProduct(owner);

        Set<Product> providedProducts = new HashSet<>();
        providedProducts.add(provided);

        // Create pool with end date in the past.
        Pool pool = new Pool(
            owner,
            product,
            providedProducts,
            Long.valueOf(5),
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2010, 11, 30),
            "SUB234598S",
            "ACC123",
            "ORD222"
        );

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

        Product prod4 = this.createProduct("p4", "p4", owner1);
        Product prod4d = this.createProduct("p4d", "p4d", owner1);
        Product prod5 = this.createProduct("p5", "p5", owner2);
        Product prod5d = this.createProduct("p5d", "p5d", owner2);
        Product prod6 = this.createProduct("p6", "p6", owner3);
        Product prod6d = this.createProduct("p6d", "p6d", owner3);

        Pool pool1 = new Pool();
        pool1.setOwner(owner1);
        pool1.setProduct(prod4);
        pool1.setDerivedProduct(prod4d);
        prod4.setProvidedProducts(new HashSet<>(Arrays.asList(prod1o1)));
        prod4d.setProvidedProducts(new HashSet<>(Arrays.asList(prod2o1)));
        pool1.setStartDate(TestUtil.createDate(2000, 1, 1));
        pool1.setEndDate(TestUtil.createDate(3000, 1, 1));
        pool1.setQuantity(5L);

        Pool pool2 = new Pool();
        pool2.setOwner(owner2);
        pool2.setProduct(prod5);
        pool2.setDerivedProduct(prod5d);
        prod5.setProvidedProducts(new HashSet<>(Arrays.asList(prod1o2, prod2o2)));
        prod5d.setProvidedProducts(new HashSet<>(Arrays.asList(prod3o2)));
        pool2.setStartDate(TestUtil.createDate(1000, 1, 1));
        pool2.setEndDate(TestUtil.createDate(2000, 1, 1));
        pool2.setQuantity(5L);

        Pool pool3 = new Pool();
        pool3.setOwner(owner3);
        pool3.setProduct(prod6);
        pool3.setDerivedProduct(prod6d);
        prod6.setProvidedProducts(new HashSet<>(Arrays.asList(prod1o3)));
        prod6d.setProvidedProducts(new HashSet<>(Arrays.asList(prod3o3)));
        pool3.setStartDate(new Date());
        pool3.setEndDate(new Date());
        pool3.setQuantity(5L);

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
}

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

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
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

        Assert.assertEquals("testing",
                this.ownerCurator.find("testing-primary-key").getKey());
    }

    @Test(expected = RollbackException.class)
    public void primaryKeyCollision() {
        Owner owner = new Owner("dude");
        owner = this.ownerCurator.create(owner);

        Owner newOwner = new Owner("someoneElse");
        newOwner.setId(owner.getId());

        this.ownerCurator.replicate(newOwner);
    }

    @Test(expected = PersistenceException.class)
    public void upstreamUuidConstraint() {
        Owner owner1 = new Owner("owner1");
        owner1.setUpstreamUuid("sameuuid");
        Owner owner2 = new Owner("owner2");
        owner2.setUpstreamUuid("sameuuid");

        ownerCurator.create(owner1);
        ownerCurator.create(owner2);
    }

    private void associateProductToOwner(Owner o, Product p, Product provided) {
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(
            provided.getId(), "Test Provided Product");
        providedProducts.add(providedProduct);

        Pool pool = TestUtil.createPool(o, p, providedProducts, 5);
        providedProduct.setPool(pool);
        poolCurator.create(pool);

        Consumer c = createConsumer(o);
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(o, c, pool, cert);
        entitlementCurator.create(ent);
    }

    @Test
    public void testLookupMultipleOwnersByMultipleProducts() {
        Owner owner = createOwner();
        Owner owner2 = createOwner();

        Product product = TestUtil.createProduct();
        Product provided = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product provided2 = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(provided);
        productCurator.create(product2);
        productCurator.create(provided2);

        associateProductToOwner(owner, product, provided);
        associateProductToOwner(owner2, product2, provided2);

        List<String> productIds = new ArrayList<String>();
        productIds.add(provided.getId());
        productIds.add(provided2.getId());
        List<Owner> results = ownerCurator.lookupOwnersByActiveProduct(productIds);

        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testLookupOwnerByProduct() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product provided = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(provided);

        associateProductToOwner(owner, product, provided);

        List<String> productIds = new ArrayList<String>();
        productIds.add(provided.getId());
        List<Owner> results = ownerCurator.lookupOwnersByActiveProduct(productIds);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals(owner, results.get(0));
    }

    @Test
    public void testLookupOwnersByProductWithExpiredEntitlements() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product provided = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(provided);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(
            provided.getId(), "Test Provided Product");
        providedProducts.add(providedProduct);

        // Create pool with end date in the past.
        Pool pool = new Pool(owner, product.getId(), product.getName(),
            providedProducts, Long.valueOf(5), TestUtil.createDate(2009,
                11, 30), TestUtil.createDate(2010, 11, 30), "SUB234598S",
            "ACC123");

        providedProduct.setPool(pool);
        poolCurator.create(pool);

        Consumer consumer = createConsumer(owner);
        consumerCurator.create(consumer);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(ent);

        List<String> productIds = new ArrayList<String>();
        productIds.add(provided.getId());
        List<Owner> results = ownerCurator.lookupOwnersByActiveProduct(productIds);

        Assert.assertTrue(results.isEmpty());
    }
}

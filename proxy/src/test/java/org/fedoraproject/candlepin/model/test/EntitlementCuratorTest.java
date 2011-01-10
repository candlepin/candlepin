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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    private Owner owner;
    private Consumer consumer;
    private Calendar cal;
    private Date overlappingDate;
    private Date futureDate;
    private Date pastDate;

    @Before
    public void setUp() {
        cal = Calendar.getInstance();

        owner = createOwner();
        ownerCurator.create(owner);

        consumer = createConsumer(owner);
        consumerCurator.create(consumer);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool firstPool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, null, firstPool,
            firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct();
        productCurator.create(product1);

        Pool secondPool = createPoolAndSub(owner, product1, 1L,
            dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, null, secondPool,
            secondCertificate);
        entitlementCurator.create(secondEntitlement);
        
        overlappingDate = createDate(2010, 2, 1);
        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);
    }
    
    private Date createDate(int year, int month, int day) {
        cal.set(year, month + 1, day);
        return cal.getTime();
    }
    
    private Entitlement setupListProvidingEntitlement() {
        Product parentProduct = TestUtil.createProduct();
        Product providedProduct1 = TestUtil.createProduct();
        Product providedProduct2 = TestUtil.createProduct();
        productCurator.create(parentProduct);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
        
        Date startDate = createDate(2010, 1, 1); 
        Date endDate = createDate(2011, 1, 1);
        Pool testPool = createPoolAndSub(owner, parentProduct, 1L,
            startDate, endDate);
        
        // Add some provided products to this pool:
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct p1 = new ProvidedProduct(providedProduct1.getId(), 
            providedProduct1.getName());
        ProvidedProduct p2 = new ProvidedProduct(providedProduct2.getId(), 
            providedProduct2.getName());
        p1.setPool(testPool);
        p2.setPool(testPool);
        providedProducts.add(p1);
        providedProducts.add(p2);
        testPool.setProvidedProducts(providedProducts);
        poolCurator.create(testPool);
        
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        
        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);
        
        System.out.println("#############");
        System.out.println(ent.getStartDate());
        System.out.println(ent.getEndDate());

        return ent;
    }
    
    @Test
    public void listProviding() {
        Entitlement ent = setupListProvidingEntitlement();
        // Test a successful query:
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
                ent.getPool().getProductId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }
    
    @Test
    public void listProvidingNoResults() {
        Entitlement ent = setupListProvidingEntitlement();
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
            "nosuchproductid", ent.getStartDate(), ent.getEndDate());
        assertEquals(0, results.size());
    }
    
    @Test
    public void listProvidingStartDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
            ent.getPool().getProductId(), overlappingDate, futureDate);
        assertEquals(1, results.size());

    }

    @Test
    public void listProvidingEndDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
            ent.getPool().getProductId(), pastDate, overlappingDate);
        assertEquals(1, results.size());
    }
    
    @Test
    public void listProvidingTotalOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
            ent.getPool().getProductId(), pastDate, futureDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingNoOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        List<Entitlement> results = entitlementCurator.listProviding(consumer, 
            ent.getPool().getProductId(), pastDate, pastDate);
        assertEquals(0, results.size());
    }

    @Test
    public void shouldReturnCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(secondCertificate.getSerial().getId());
        assertEquals(secondEntitlement, e);
    }

    @Test
    public void shouldReturnInCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(firstCertificate.getSerial().getId());
        assertNotSame(secondEntitlement, e);
    }

}

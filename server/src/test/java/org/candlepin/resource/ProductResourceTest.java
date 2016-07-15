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
package org.candlepin.resource;


import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.resteasy.IterableStreamingOutputFactory;
import org.candlepin.resteasy.IterableStreamingOutput;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.core.Response;



/**
 * ProductResourceTest
 */
public class ProductResourceTest extends DatabaseTestFixture {
    @Inject private ProductCertificateCurator productCertificateCurator;
    @Inject private ContentCurator contentCurator;
    @Inject private ProductResource productResource;
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private Configuration config;
    @Inject private I18n i18n;
    @Inject private IterableStreamingOutputFactory isoFactory;

    private ProductData buildTestProductDTO() {
        ProductData dto = TestUtil.createProductDTO("test_product");

        dto.setAttribute("version", "1.0");
        dto.setAttribute("variant", "server");
        dto.setAttribute("type", "SVC");
        dto.setAttribute("arch", "ALL");

        return dto;
    }

    private Product buildTestProduct() {
        Product entity = TestUtil.createProduct("test_product");

        entity.setAttribute("version", "1.0");
        entity.setAttribute("variant", "server");
        entity.setAttribute("type", "SVC");
        entity.setAttribute("arch", "ALL");

        return entity;
    }

    @Test(expected = BadRequestException.class)
    public void testCreateProductResource() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductData productData = this.buildTestProductDTO();

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), productData.getId()));

        ProductData result = productResource.createProduct(productData);
        Product entity = this.ownerProductCurator.getProductById(owner.getKey(), productData.getId());

        assertNotNull(entity);
        assertFalse(entity.isChangedBy(result));
    }

    @Test(expected = BadRequestException.class)
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductData productData = this.buildTestProductDTO();
        ContentData contentData = TestUtil.createContentDTO();
        productData.addContent(contentData, true);

        assertNull(this.ownerProductCurator.getProductById(owner.getKey(), productData.getId()));

        ProductData result = productResource.createProduct(productData);
        Product entity = this.ownerProductCurator.getProductById(owner.getKey(), productData.getId());

        assertNotNull(entity);
        assertFalse(entity.isChangedBy(result));

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(contentData, result.getProductContent().iterator().next().getContent());
    }

    @Test(expected = BadRequestException.class)
    public void testDeleteProductWithSubscriptions() {
        ProductCurator pc = mock(ProductCurator.class);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        ProductResource pr = new ProductResource(pc, null, null, config, i18n, this.isoFactory);
        Owner o = mock(Owner.class);
        Product p = mock(Product.class);
        // when(pc.lookupById(eq(o), eq("10"))).thenReturn(p);
        Set<Subscription> subs = new HashSet<Subscription>();
        Subscription s = mock(Subscription.class);
        subs.add(s);
        when(pc.productHasSubscriptions(eq(p), eq(o))).thenReturn(true);

        pr.deleteProduct("10");
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);

        if (entity.isLocked()) {
            throw new RuntimeException("entity is locked...?");
        }

        securityInterceptor.enable();
        ProductData result = productResource.getProduct(entity.getUuid());

        assertNotNull(result);
        assertFalse(entity.isChangedBy(result));
    }

    @Test
    public void getProductCertificate() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct(owner);
        // ensure we check SecurityHole
        securityInterceptor.enable();

        ProductCertificate cert = new ProductCertificate();
        cert.setCert("some text");
        cert.setKey("some key");
        cert.setProduct(entity);
        productCertificateCurator.create(cert);

        ProductCertificate cert1 = productResource.getProductCertificate(entity.getUuid());

        assertEquals(cert, cert1);
    }

    private List<Owner> setupDBForOwnerProdTests() {
        Owner owner1 = this.ownerCurator.create(new Owner("TestCorp-01"));
        Owner owner2 = this.ownerCurator.create(new Owner("TestCorp-02"));
        Owner owner3 = this.ownerCurator.create(new Owner("TestCorp-03"));

        Product prod1 = this.createProduct("p1", "p1", owner1);
        Product prod2 = this.createProduct("p1", "p1", owner2);
        Product prod3 = this.createProduct("p2", "p2", owner2);
        Product prod4 = this.createProduct("p2", "p2", owner3);
        Product prod5 = this.createProduct("p3", "p3", owner3);

        Product poolProd1 = this.createProduct(owner1);
        Product poolProd2 = this.createProduct(owner2);
        Product poolProd3 = this.createProduct(owner2);
        Product poolProd4 = this.createProduct(owner3);
        Product poolProd5 = this.createProduct(owner3);

        Pool pool1 = this.poolCurator.create(TestUtil.createPool(
            owner1, poolProd1, new HashSet(Arrays.asList(prod1)), 5));
        Pool pool2 = this.poolCurator.create(TestUtil.createPool(
            owner2, poolProd2, new HashSet(Arrays.asList(prod2)), 5));
        Pool pool3 = this.poolCurator.create(TestUtil.createPool(
            owner2, poolProd3, new HashSet(Arrays.asList(prod3)), 5));
        Pool pool4 = this.poolCurator.create(TestUtil.createPool(
            owner3, poolProd4, new HashSet(Arrays.asList(prod4)), 5));
        Pool pool5 = this.poolCurator.create(TestUtil.createPool(
            owner3, poolProd5, new HashSet(Arrays.asList(prod5)), 5));

        return Arrays.asList(owner1, owner2, owner3);
    }

    @Test
    public void testGetOwnersForProducts() {
        List<Owner> owners = this.setupDBForOwnerProdTests();
        Owner owner1 = owners.get(0);
        Owner owner2 = owners.get(1);
        Owner owner3 = owners.get(2);

        Response response = productResource.getProductOwners(Arrays.asList("p1"));
        owners = new LinkedList<Owner>();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            owners.add((Owner) entity);
        }
        assertEquals(Arrays.asList(owner1, owner2), owners);

        response = productResource.getProductOwners(Arrays.asList("p1", "p2"));
        owners.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            owners.add((Owner) entity);
        }
        assertEquals(Arrays.asList(owner1, owner2, owner3), owners);

        response = productResource.getProductOwners(Arrays.asList("p3"));
        owners.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            owners.add((Owner) entity);
        }
        assertEquals(Arrays.asList(owner3), owners);

        response = productResource.getProductOwners(Arrays.asList("nope"));
        owners.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            owners.add((Owner) entity);
        }
        assertEquals(0, owners.size());
    }

    @Test(expected = BadRequestException.class)
    public void testGetOwnersForProductsInputValidation() {
        productResource.getProductOwners(new LinkedList<String>());
    }

    private void verifyRefreshPoolsJobs(List<JobDetail> jobs, List<Owner> owners, boolean lazyRegen) {
        for (JobDetail job : jobs) {
            assertTrue(RefreshPoolsJob.class.isAssignableFrom(job.getJobClass()));

            JobDataMap jdmap = job.getJobDataMap();

            assertTrue(jdmap.containsKey(JobStatus.OWNER_ID));
            assertTrue(jdmap.containsKey(JobStatus.TARGET_TYPE));
            assertTrue(jdmap.containsKey(JobStatus.TARGET_ID));
            assertTrue(jdmap.containsKey(RefreshPoolsJob.LAZY_REGEN));

            assertEquals(JobStatus.TargetType.OWNER, jdmap.get(JobStatus.TARGET_TYPE));
            assertEquals(jdmap.get(JobStatus.OWNER_ID), jdmap.get(JobStatus.TARGET_ID));
            assertEquals(lazyRegen, jdmap.get(RefreshPoolsJob.LAZY_REGEN));

            boolean found = false;
            for (Owner owner : owners) {
                if (owner.getKey().equals(jdmap.get(JobStatus.OWNER_ID))) {
                    found = true;
                    break;
                }
            }

            assertTrue(found);
        }
    }

    @Test
    public void testRefreshPoolsByProduct() {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        ProductResource productResource = new ProductResource(
            this.productCurator, this.ownerCurator, this.productCertificateCurator, config, this.i18n,
            this.isoFactory
        );

        List<Owner> owners = this.setupDBForOwnerProdTests();
        Owner owner1 = owners.get(0);
        Owner owner2 = owners.get(1);
        Owner owner3 = owners.get(2);

        List<JobDetail> jobs = new LinkedList<JobDetail>();

        Response response = productResource.refreshPoolsForProduct(Arrays.asList("p1"), true);
        jobs.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            jobs.add((JobDetail) entity);
        }
        assertNotNull(jobs);
        assertEquals(2, jobs.size());
        this.verifyRefreshPoolsJobs(jobs, Arrays.asList(owner1, owner2), true);

        response = productResource.refreshPoolsForProduct(Arrays.asList("p1", "p2"), false);
        jobs.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            jobs.add((JobDetail) entity);
        }
        assertNotNull(jobs);
        assertEquals(3, jobs.size());
        this.verifyRefreshPoolsJobs(jobs, Arrays.asList(owner1, owner2, owner3), false);

        response = productResource.refreshPoolsForProduct(Arrays.asList("p3"), false);
        jobs.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            jobs.add((JobDetail) entity);
        }
        assertNotNull(jobs);
        assertEquals(1, jobs.size());
        this.verifyRefreshPoolsJobs(jobs, Arrays.asList(owner3), false);

        response = productResource.refreshPoolsForProduct(Arrays.asList("nope"), false);
        jobs.clear();
        for (Object entity : (IterableStreamingOutput) response.getEntity()) {
            jobs.add((JobDetail) entity);
        }
        assertNotNull(jobs);
        assertEquals(0, jobs.size());
    }

    @Test(expected = BadRequestException.class)
    public void testRefreshPoolsByProductInputValidation() {
        productResource.refreshPoolsForProduct(new LinkedList<String>(), true);
    }
}

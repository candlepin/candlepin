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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;


/**
 * ProductResourceTest
 */
public class ProductResourceTest extends DatabaseTestFixture {
    @Inject private ProductCertificateCurator productCertificateCurator;
    @Inject private ProductResource productResource;
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private Configuration config;
    @Inject private I18n i18n;

    private JobManager jobManager;

    @BeforeEach
    public void init() throws Exception {
        super.init();

        this.jobManager = mock(JobManager.class);

        doAnswer((Answer<AsyncJobStatus>) invocation -> {
            Object[] args = invocation.getArguments();
            JobConfig<?> jobConfig = (JobConfig<?>) args[0];

            return new AsyncJobStatus()
                .setState(AsyncJobStatus.JobState.QUEUED)
                .setJobKey(jobConfig.getJobKey())
                .setName(jobConfig.getJobName())
                .setJobArguments(jobConfig.getJobArguments());
        }).when(this.jobManager).queueJob(any(JobConfig.class));
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product", owner);
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        if (entity.isLocked()) {
            throw new RuntimeException("entity is locked...?");
        }

        securityInterceptor.enable();
        ProductDTO result = productResource.getProduct(entity.getUuid());

        assertNotNull(result);
        assertEquals(result, expected);
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

        ProductCertificateDTO cert1 = productResource.getProductCertificate(entity.getUuid());
        ProductCertificateDTO expected = this.modelTranslator.translate(cert, ProductCertificateDTO.class);
        assertEquals(cert1, expected);
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

        this.poolCurator.create(TestUtil.createPool(
            owner1, poolProd1, new HashSet<>(Collections.singletonList(prod1)), 5));
        this.poolCurator.create(TestUtil.createPool(
            owner2, poolProd2, new HashSet<>(Collections.singletonList(prod2)), 5));
        this.poolCurator.create(TestUtil.createPool(
            owner2, poolProd3, new HashSet<>(Collections.singletonList(prod3)), 5));
        this.poolCurator.create(TestUtil.createPool(
            owner3, poolProd4, new HashSet<>(Collections.singletonList(prod4)), 5));
        this.poolCurator.create(TestUtil.createPool(
            owner3, poolProd5, new HashSet<>(Collections.singletonList(prod5)), 5));

        return Arrays.asList(owner1, owner2, owner3);
    }

    @Test
    public void testGetOwnersForProducts() {
        List<Owner> owners = this.setupDBForOwnerProdTests();
        Owner owner1 = owners.get(0);
        Owner owner2 = owners.get(1);
        Owner owner3 = owners.get(2);
        OwnerDTO ownerDTO1 = this.modelTranslator.translate(owner1, OwnerDTO.class);
        OwnerDTO ownerDTO2 = this.modelTranslator.translate(owner2, OwnerDTO.class);
        OwnerDTO ownerDTO3 = this.modelTranslator.translate(owner3, OwnerDTO.class);

        List<OwnerDTO> ownersReturned;

        ownersReturned = productResource.getProductOwners(Collections.singletonList("p1")).list();
        assertEquals(Arrays.asList(ownerDTO1, ownerDTO2), ownersReturned);

        ownersReturned = productResource.getProductOwners(Arrays.asList("p1", "p2")).list();
        assertEquals(Arrays.asList(ownerDTO1, ownerDTO2, ownerDTO3), ownersReturned);

        ownersReturned = productResource.getProductOwners(Collections.singletonList("p3")).list();
        assertEquals(Collections.singletonList(ownerDTO3), ownersReturned);

        ownersReturned = productResource.getProductOwners(Collections.singletonList("nope")).list();
        assertEquals(0, ownersReturned.size());
    }

    @Test
    public void testGetOwnersForProductsInputValidation() {
        assertThrows(BadRequestException.class, () -> productResource.getProductOwners(new LinkedList<>()));
    }

    private void verifyRefreshPoolsJobsWereQueued(List<AsyncJobStatusDTO> jobs) {
        for (AsyncJobStatusDTO job : jobs) {
            assertEquals(RefreshPoolsJob.JOB_NAME, job.getName());
            assertEquals(AsyncJobStatus.JobState.CREATED.toString(), job.getState());
            assertEquals(AsyncJobStatus.JobState.CREATED.toString(), job.getPreviousState());
            assertEquals(Integer.valueOf(0), job.getAttempts());
            assertEquals(Integer.valueOf(1), job.getMaxAttempts());
        }
    }

    @Test
    public void testRefreshPoolsByProduct() {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        ProductResource productResource = new ProductResource(this.productCurator, this.ownerCurator,
            this.productCertificateCurator, config, this.i18n, this.modelTranslator, this.jobManager);
        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = productResource
            .refreshPoolsForProduct(Collections.singletonList("p1"), true)
            .collect(Collectors.toList());

        assertNotNull(jobs);
        assertEquals(2, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductForMultipleProducts() {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        ProductResource productResource = new ProductResource(this.productCurator, this.ownerCurator,
            this.productCertificateCurator, config, this.i18n, this.modelTranslator, this.jobManager);
        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = productResource
            .refreshPoolsForProduct(Arrays.asList("p1", "p2"), false)
            .collect(Collectors.toList());

        assertNotNull(jobs);
        assertEquals(3, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductWithoutLazyOffload() {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        ProductResource productResource = new ProductResource(this.productCurator, this.ownerCurator,
            this.productCertificateCurator, config, this.i18n, this.modelTranslator, this.jobManager);
        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = productResource
            .refreshPoolsForProduct(Collections.singletonList("p3"), false)
            .collect(Collectors.toList());

        assertNotNull(jobs);
        assertEquals(1, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductWithBadProductId() {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        ProductResource productResource = new ProductResource(this.productCurator, this.ownerCurator,
            this.productCertificateCurator, config, this.i18n, this.modelTranslator, this.jobManager);
        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = productResource
            .refreshPoolsForProduct(Collections.singletonList("nope"), false)
            .collect(Collectors.toList());

        assertNotNull(jobs);
        assertEquals(0, jobs.size());
    }

    @Test
    public void testRefreshPoolsByProductInputValidation() {
        assertThrows(BadRequestException.class, () ->
            productResource.refreshPoolsForProduct(new LinkedList<>(), true)
        );
    }

    @Test
    public void testRefreshPoolsByProductJobQueueingErrorsShouldBeHandled() throws JobException {
        Configuration config = new MapConfiguration(this.config);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        // We want to simulate the first queueJob call succeeds, and the second throws a validation exception
        JobManager jobManager = mock(JobManager.class);
        AsyncJobStatus status1 = new AsyncJobStatus();
        status1.setName(RefreshPoolsJob.JOB_NAME);
        status1.setState(AsyncJobStatus.JobState.CREATED); //need to prime the previous state field
        status1.setState(AsyncJobStatus.JobState.QUEUED);
        when(jobManager.queueJob(any(JobConfig.class)))
            .thenReturn(status1)
            .thenThrow(new JobConfigValidationException("a job config validation error happened!"));

        ProductResource productResource = new ProductResource(this.productCurator, this.ownerCurator,
            this.productCertificateCurator, config, this.i18n, this.modelTranslator, jobManager);
        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = null;
        try {
            jobs = productResource.refreshPoolsForProduct(Collections.singletonList("p1"), true)
                    .collect(Collectors.toList());
        }
        catch (Exception e) {
            fail("A validation exception when trying to queue a job should be handled " +
                "by the endpoint, not thrown!");
        }
        assertNotNull(jobs);
        assertEquals(2, jobs.size());

        // assert first job succeeded in queueing
        AsyncJobStatusDTO statusDTO1 = jobs.get(0);
        assertEquals(RefreshPoolsJob.JOB_NAME, statusDTO1.getName());
        assertEquals(AsyncJobStatus.JobState.CREATED.toString(), statusDTO1.getState());
        assertEquals(AsyncJobStatus.JobState.CREATED.toString(), statusDTO1.getPreviousState());
        assertEquals(Integer.valueOf(0), statusDTO1.getAttempts());
        assertEquals(Integer.valueOf(1), statusDTO1.getMaxAttempts());

        // assert second job failed validation
        AsyncJobStatusDTO statusDTO2 = jobs.get(1);
        assertEquals(RefreshPoolsJob.JOB_NAME, statusDTO2.getName());
        assertEquals(AsyncJobStatus.JobState.FAILED.toString(), statusDTO2.getState());
        assertEquals(AsyncJobStatus.JobState.CREATED.toString(), statusDTO2.getPreviousState());
        assertEquals(Integer.valueOf(0), statusDTO2.getAttempts());
        assertEquals(Integer.valueOf(1), statusDTO2.getMaxAttempts());
    }

}

/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;



/**
 * ProductResourceTest
 */
public class ProductResourceTest extends DatabaseTestFixture {

    @BeforeEach
    public void init() throws Exception {
        super.init();

        // Make sure we don't have any latent page requests in the context
        ResteasyContext.clearContextData();
    }

    @AfterEach
    public void cleanup() throws Exception {
        // Also cleanup after ourselves for other tests
        ResteasyContext.clearContextData();
    }

    private JobManager mockJobManager() throws JobException {
        JobManager manager = mock(JobManager.class);

        Answer<AsyncJobStatus> queueJobAnswer = invocation -> {
            JobConfig<?> jobConfig = invocation.getArgument(0);

            return new AsyncJobStatus()
                .setState(AsyncJobStatus.JobState.QUEUED)
                .setJobKey(jobConfig.getJobKey())
                .setName(jobConfig.getJobName())
                .setJobArguments(jobConfig.getJobArguments());
        };

        doAnswer(queueJobAnswer)
            .when(manager)
            .queueJob(any(JobConfig.class));

        return manager;
    }

    private ProductResource buildResource(JobManager jobManager) {
        PagingUtilFactory pagingUtilFactory = new PagingUtilFactory(config, this.i18nProvider);

        return new ProductResource(this.config, this.i18n, this.productCurator, this.ownerCurator,
            this.productCertificateCurator, pagingUtilFactory, this.modelTranslator, jobManager);
    }

    private ProductResource buildResource() {
        return this.injector.getInstance(ProductResource.class);
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product");
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        securityInterceptor.enable();

        ProductResource resource = this.buildResource();
        ProductDTO result = resource.getProductByUuid(entity.getUuid());

        assertNotNull(result);
        assertEquals(result, expected);
    }

    private List<Owner> setupDBForOwnerProdTests() {
        Owner owner1 = this.ownerCurator.create(new Owner().setKey("testorg-1").setDisplayName("testorg-1"));
        Owner owner2 = this.ownerCurator.create(new Owner().setKey("testorg-2").setDisplayName("testorg-2"));
        Owner owner3 = this.ownerCurator.create(new Owner().setKey("testorg-3").setDisplayName("testorg-3"));

        Product prod1 = this.createProduct("p1", "p1");
        Product prod2 = this.createProduct("p2", "p2");
        Product prod3 = this.createProduct("p3", "p3");

        Product poolProd1 = this.createProduct();
        Product poolProd2 = this.createProduct();
        Product poolProd3 = this.createProduct();
        Product poolProd4 = this.createProduct();
        Product poolProd5 = this.createProduct();

        // Set Provided Products
        poolProd1.setProvidedProducts(Arrays.asList(prod1));
        poolProd2.setProvidedProducts(Arrays.asList(prod1));
        poolProd3.setProvidedProducts(Arrays.asList(prod2));
        poolProd4.setProvidedProducts(Arrays.asList(prod2));
        poolProd5.setProvidedProducts(Arrays.asList(prod3));

        this.poolCurator.create(TestUtil.createPool(owner1, poolProd1, 5));
        this.poolCurator.create(TestUtil.createPool(owner2, poolProd2, 5));
        this.poolCurator.create(TestUtil.createPool(owner2, poolProd3, 5));
        this.poolCurator.create(TestUtil.createPool(owner3, poolProd4, 5));
        this.poolCurator.create(TestUtil.createPool(owner3, poolProd5, 5));

        return Arrays.asList(owner1, owner2, owner3);
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
    public void testRefreshPoolsByProduct() throws JobException {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        JobManager mockJobManager = this.mockJobManager();
        ProductResource resource = this.buildResource(mockJobManager);

        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = resource.refreshPoolsForProducts(List.of("p1"), true)
            .toList();

        assertNotNull(jobs);
        assertEquals(2, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductForMultipleProducts() throws JobException {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        JobManager mockJobManager = this.mockJobManager();
        ProductResource resource = this.buildResource(mockJobManager);

        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = resource.refreshPoolsForProducts(List.of("p1", "p2"), false)
            .toList();

        assertNotNull(jobs);
        assertEquals(3, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductWithoutLazyOffload() throws JobException {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        JobManager mockJobManager = this.mockJobManager();
        ProductResource resource = this.buildResource(mockJobManager);

        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = resource.refreshPoolsForProducts(List.of("p3"), false)
            .toList();

        assertNotNull(jobs);
        assertEquals(1, jobs.size());
        this.verifyRefreshPoolsJobsWereQueued(jobs);
    }

    @Test
    public void testRefreshPoolsByProductWithBadProductId() throws JobException {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        JobManager mockJobManager = this.mockJobManager();
        ProductResource resource = this.buildResource(mockJobManager);

        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = resource.refreshPoolsForProducts(List.of("nope"), false)
            .toList();

        assertNotNull(jobs);
        assertEquals(0, jobs.size());
    }

    @Test
    public void testRefreshPoolsByProductInputValidation() {
        ProductResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.refreshPoolsForProducts(List.of(), true));
    }

    @Test
    public void testRefreshPoolsByProductJobQueueingErrorsShouldBeHandled() throws JobException {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        AsyncJobStatus status1 = new AsyncJobStatus()
            .setName(RefreshPoolsJob.JOB_NAME)
            .setState(AsyncJobStatus.JobState.CREATED) // need to prime the previous state field
            .setState(AsyncJobStatus.JobState.QUEUED);

        // We want to simulate the first queueJob call succeeds, and the second throws a validation
        // exception
        JobManager jobManager = mock(JobManager.class);
        when(jobManager.queueJob(any(JobConfig.class)))
            .thenReturn(status1)
            .thenThrow(new JobConfigValidationException("a job config validation error happened!"));

        ProductResource resource = this.buildResource(jobManager);

        this.setupDBForOwnerProdTests();

        List<AsyncJobStatusDTO> jobs = null;
        try {
            jobs = resource.refreshPoolsForProducts(List.of("p1"), true)
                .toList();
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

    /**
     * Creates a set of products for use with the "testGetProducts..." family of tests below. Do not make
     * changes to these products unless you are updating the testing for the ProductResource.getProducts
     * method, and do not use this method to set up data for any other test!
     *
     * Note that this test data does not create full product graphs for products. Testing the definition
     * of "active" is left to a set of explicit tests which validates it in full.
     */
    private void createDataForEndpointQueryTesting() {
        List<Owner> owners = List.of(
            this.createOwner("owner1"),
            this.createOwner("owner2"),
            this.createOwner("owner3"));

        List<Product> globalProducts = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            Product gprod = new Product()
                .setId("g-prod-" + i)
                .setName("global product " + i);

            globalProducts.add(this.productCurator.create(gprod));
        }

        for (int oidx = 1; oidx <= owners.size(); ++oidx) {
            Owner owner = owners.get(oidx - 1);

            List<Product> ownerProducts = new ArrayList<>();

            // create two custom products
            for (int i = 1; i <= 2; ++i) {
                Product cprod = new Product()
                    .setId(String.format("o%d-prod-%d", oidx, i))
                    .setName(String.format("%s product %d", owner.getKey(), i))
                    .setNamespace(owner.getKey());

                ownerProducts.add(this.productCurator.create(cprod));
            }

            // create some pools:
            // - one which references a global product
            // - one which references a custom product
            Pool globalPool = this.createPool(owner, globalProducts.get(0));
            Pool customPool = this.createPool(owner, ownerProducts.get(0));
        }
    }

    @Test
    public void testGetProductsFetchesWithNoFiltering() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsDefaultsToActiveOnly() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-1", "o1-prod-1", "o2-prod-1", "o3-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null, null, null);

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithOwnerFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> ownerKeys = List.of("owner2", "owner3");
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o3-prod-1", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(ownerKeys, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_owner" })
    @NullAndEmptySource
    public void testGetProductsErrorsWithInvalidOwners(String invalidOwnerKey) {
        this.createDataForEndpointQueryTesting();

        List<String> ownerKeys = Arrays.asList("owner1", invalidOwnerKey);

        ProductResource resource = this.buildResource();

        assertThrows(NotFoundException.class, () -> resource.getProducts(ownerKeys, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetProductsFetchesWithIDFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> productIds = List.of("g-prod-2", "o1-prod-1", "o3-prod-1");
        List<String> expectedPids = List.of("g-prod-2", "o1-prod-1", "o3-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, productIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_prod_id" })
    @NullAndEmptySource
    public void testGetProductsFetchesWithInvalidIDs(String invalidProductId) {
        this.createDataForEndpointQueryTesting();

        List<String> productIds = Arrays.asList("o1-prod-1", invalidProductId);
        List<String> expectedPids = List.of("o1-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, productIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithNameFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> productNames = Arrays.asList("owner1 product 2", "global product 3", "owner3 product 1");
        List<String> expectedPids = List.of("g-prod-3", "o1-prod-2", "o3-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, productNames,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_prod_name" })
    @NullAndEmptySource
    public void testGetProductsFetchesWithInvalidNames(String invalidProductName) {
        this.createDataForEndpointQueryTesting();

        List<String> productNames = Arrays.asList("owner1 product 2", invalidProductName);
        List<String> expectedPids = List.of("o1-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, productNames,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithOmitActiveFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-2", "g-prod-3", "o1-prod-2", "o2-prod-2", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.EXCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithOnlyActiveFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-1", "o1-prod-1", "o2-prod-1", "o3-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithIncludeActiveFilter() {
        this.createDataForEndpointQueryTesting();

        // active = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsErrorsWithInvalidActiveFilter() {
        ProductResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getProducts(null, null, null,
            "invalid_type", Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetProductsFetchesWithOmitCustomFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithOnlyCustomFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("o1-prod-1", "o1-prod-2", "o2-prod-1", "o2-prod-2",
            "o3-prod-1", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUSIVE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsFetchesWithIncludeCustomFilter() {
        this.createDataForEndpointQueryTesting();

        // custom = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsErrorsWithInvalidCustomFilter() {
        ProductResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getProducts(null, null, null,
            Inclusion.INCLUDE.name(), "invalid_type"));
    }

    @Test
    public void testGetProductsFetchesWithMultipleFilters() {
        this.createDataForEndpointQueryTesting();

        // This test configures a bunch of filters which loosely resolve to the following:
        // - active global products: not custom, not inactive (active = only, custom = omit)
        // - in orgs 2 or 3
        // - matching the given list of product IDs (gp1, gp2, o1p1, o2p1, o3p2)
        // - matching the given list of product names (gp1, gp2, gp3, o2p1, o2p2)
        //
        // These filters should be applied as an intersection, resulting in a singular match on gp1

        List<String> ownerKeys = List.of("owner2", "owner3");
        List<String> productIds = List.of("g-prod-1", "g-prod-2", "o1-prod-1", "o2-prod-1", "o3-prod-2");
        List<String> productNames = List.of("global product 1", "global product 2", "global product 3",
            "owner2 product 1", "owner2 product 2");
        String activeInclusion = Inclusion.EXCLUSIVE.name();
        String customInclusion = Inclusion.EXCLUDE.name();

        List<String> expectedPids = List.of("g-prod-1");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(ownerKeys, productIds, productNames, activeInclusion,
            customInclusion);

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testGetProductsFetchesPagedResults(int pageSize) {
        this.createDataForEndpointQueryTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        int expectedPages = pageSize < expectedPids.size() ?
            (expectedPids.size() / pageSize) + (expectedPids.size() % pageSize != 0 ? 1 : 0) :
            1;

        ProductResource resource = this.buildResource();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            // Set the context page request
            ResteasyContext.popContextData(PageRequest.class);
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(++pages)
                .setPerPage(pageSize));

            Stream<ProductDTO> output = resource.getProducts(null, null, null, Inclusion.INCLUDE.name(),
                Inclusion.INCLUDE.name());

            assertNotNull(output);
            List<String> receivedPids = output.map(ProductDTO::getId)
                .toList();

            if (receivedPids.isEmpty()) {
                break;
            }

            found.addAll(receivedPids);
        }

        assertEquals(expectedPages, pages);
        assertThat(found)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testGetProductsFetchesOrderedResults(String field) {
        this.createDataForEndpointQueryTesting();

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy(field));

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null, Inclusion.INCLUDE.name(),
            Inclusion.INCLUDE.name());

        // Note that this output needs to be ordered according to our expected ordering!
        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsErrorsWithInvalidOrderingRequest() {
        this.createDataForEndpointQueryTesting();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy("invalid_field_name"));

        ProductResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.getProducts(null, null, null, null, null));
    }

    // These tests verify the definition of "active" is properly implemented, ensuring "active" is defined
    // as a product which is attached to a pool which has started and has not expired, or attached to
    // another active product (recursively).
    //
    // This definition is recursive in nature, so the effect is that it should consider any product that
    // is a descendant of a product attached to a non-future pool that hasn't yet expired.

    @Test
    public void testGetActiveProductsOnlyConsidersActivePools() {
        // - "active" only considers pools which have started but not expired (start time < now() < end time)
        Owner owner = this.createOwner("test_org");

        Product prod1 = this.createProduct("prod1");
        Product prod2 = this.createProduct("prod2");
        Product prod3 = this.createProduct("prod3");

        Function<Integer, Date> days = (offset) -> TestUtil.createDateOffset(0, 0, offset);

        // Create three pools: expired, current (active), future
        Pool pool1 = this.createPool(owner, prod1, 1L, days.apply(-3), days.apply(-1));
        Pool pool2 = this.createPool(owner, prod2, 1L, days.apply(-1), days.apply(1));
        Pool pool3 = this.createPool(owner, prod3, 1L, days.apply(1), days.apply(3));

        // Active = exclusive should only find the active pool; future and expired pools should be omitted
        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null, Inclusion.EXCLUSIVE.name(),
            Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .singleElement()
            .isEqualTo(prod2.getId());
    }

    @Test
    public void testGetActiveProductsAlsoConsidersDescendantsOfActivePoolProducts() {
        // - "active" includes descendants of products attached to a pool
        Owner owner = this.createOwner("test_org");

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 20; ++i) {
            Product product = new Product()
                .setId("p" + i)
                .setName("product " + i);

            products.add(product);
        }

        /*
        pool -> prod - p0
                    derived - p1
                        provided - p2
                        provided - p3
                            provided - p4
                    provided - p5
                    provided - p6

        pool -> prod - p7
                    derived - p8*
                    provided - p9

        pool -> prod - p8*
                    provided - p10
                        provided - p11
                    provided - p12
                        provided - p13

                prod - p14
                    derived - p15
                        provided - p16

                prod - p17
                    provided - p18

        pool -> prod - p19
                prod - p20
        */

        products.get(0).setDerivedProduct(products.get(1));
        products.get(0).addProvidedProduct(products.get(5));
        products.get(0).addProvidedProduct(products.get(6));

        products.get(1).addProvidedProduct(products.get(2));
        products.get(1).addProvidedProduct(products.get(3));

        products.get(3).addProvidedProduct(products.get(4));

        products.get(7).setDerivedProduct(products.get(8));
        products.get(7).addProvidedProduct(products.get(9));

        products.get(8).addProvidedProduct(products.get(10));
        products.get(8).addProvidedProduct(products.get(12));

        products.get(10).addProvidedProduct(products.get(11));

        products.get(12).addProvidedProduct(products.get(13));

        products.get(14).setDerivedProduct(products.get(15));

        products.get(15).setDerivedProduct(products.get(16));

        products.get(17).setDerivedProduct(products.get(18));

        // persist the products in reverse order so we don't hit any hibernate errors
        for (int i = products.size() - 1; i >= 0; --i) {
            this.productCurator.create(products.get(i));
        }

        Pool pool1 = this.createPool(owner, products.get(0));
        Pool pool2 = this.createPool(owner, products.get(7));
        Pool pool3 = this.createPool(owner, products.get(8));
        Pool pool4 = this.createPool(owner, products.get(19));

        List<String> expectedPids = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9",
            "p10", "p11", "p12", "p13", "p19");

        ProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProducts(null, null, null, Inclusion.EXCLUSIVE.name(),
            Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

}

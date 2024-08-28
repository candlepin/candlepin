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
package org.candlepin.spec.products;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

@SpecTest
public class ProductResourceSpecTest {
    private static ProductsApi productsApi;
    private static OwnerApi ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static JobsClient jobsClient;

    private String p1ProductId;
    private String p2ProductId;
    private String p3ProductId;
    private String p4dProductId;
    private String p4ProductId;
    private String p5dProductId;
    private String p5ProductId;
    private String p6dProductId;
    private String p6ProductId;

    @BeforeAll
    public static void beforeAll() {
        ApiClient client = ApiClients.admin();
        productsApi = client.products();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        jobsClient = client.jobs();
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshPoolsForOrgsOwningProducts() throws Exception {
        setupOrgProductsAndPools();

        List<AsyncJobStatusDTO> jobs = productsApi.refreshPoolsForProducts(Arrays.asList(p4ProductId), true);
        assertEquals(1, jobs.size());
        verifyRefreshPoolsForProducts(jobs.get(0));

        jobs = productsApi.refreshPoolsForProducts(Arrays.asList(p5dProductId), true);
        assertEquals(1, jobs.size());
        verifyRefreshPoolsForProducts(jobs.get(0));

        jobs = productsApi.refreshPoolsForProducts(Arrays.asList(p1ProductId), true);
        assertEquals(3, jobs.size());
        verifyRefreshPoolsForProducts(jobs.get(0));
        verifyRefreshPoolsForProducts(jobs.get(1));
        verifyRefreshPoolsForProducts(jobs.get(2));

        jobs = productsApi.refreshPoolsForProducts(Arrays.asList(p3ProductId), true);
        assertEquals(2, jobs.size());
        verifyRefreshPoolsForProducts(jobs.get(0));
        verifyRefreshPoolsForProducts(jobs.get(1));

        jobs = productsApi.refreshPoolsForProducts(Arrays.asList(p4ProductId, p6ProductId), true);
        assertEquals(2, jobs.size());
        verifyRefreshPoolsForProducts(jobs.get(0));
        verifyRefreshPoolsForProducts(jobs.get(1));

        // Unknown product
        assertEquals(0, productsApi.refreshPoolsForProducts(Arrays.asList("unknown"), true).size());
    }

    @Test
    public void shouldRaiseBadRequestOnRefreshWithNoProducts() {
        assertBadRequest(() -> productsApi.refreshPoolsForProducts(new ArrayList<>(), false));
    }

    @Test
    public void shouldRaiseNotFoundForNonExistingProducts() {
        assertNotFound(() -> productsApi.getProductByUuid("unknown-product-id"));
    }

    private List<OwnerDTO> setupOrgProductsAndPools() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());

        p1ProductId = StringUtil.random("p1");
        p2ProductId = StringUtil.random("p2");
        p3ProductId = StringUtil.random("p3");
        p4dProductId = StringUtil.random("p4d");
        p4ProductId = StringUtil.random("p4");
        p5dProductId = StringUtil.random("p5d");
        p5ProductId = StringUtil.random("p5");
        p6dProductId = StringUtil.random("p6d");
        p6ProductId = StringUtil.random("p6");

        ProductDTO prod1o1 = createProduct(p1ProductId, owner1.getKey(), null, null);
        ProductDTO prod1o2 = createProduct(p1ProductId, owner2.getKey(), null, null);
        ProductDTO prod1o3 = createProduct(p1ProductId, owner3.getKey(), null, null);

        ProductDTO prod2o1 = createProduct(p2ProductId, owner1.getKey(), null, null);
        ProductDTO prod2o2 = createProduct(p2ProductId, owner2.getKey(), null, null);

        ProductDTO prod3o2 = createProduct(p3ProductId, owner2.getKey(), null, null);
        ProductDTO prod3o3 = createProduct(p3ProductId, owner3.getKey(), null, null);

        ProductDTO prod4d = createProduct(p4dProductId, owner1.getKey(), null, Arrays.asList(prod2o1));
        ProductDTO prod4 = createProduct(p4ProductId, owner1.getKey(), prod4d, Arrays.asList(prod1o1));

        ProductDTO prod5d = createProduct(p5dProductId, owner2.getKey(), null, Arrays.asList(prod3o2));
        ProductDTO prod5
            = createProduct(p5ProductId, owner2.getKey(), prod5d, Arrays.asList(prod1o2, prod2o2));

        ProductDTO prod6d = createProduct(p6dProductId, owner3.getKey(), null, Arrays.asList(prod3o3));
        ProductDTO prod6 = createProduct(p6ProductId, owner3.getKey(), prod6d, Arrays.asList(prod1o3));

        createPool(owner1.getKey(), prod4);
        createPool(owner2.getKey(), prod5);
        createPool(owner3.getKey(), prod6);

        return Arrays.asList(owner1, owner2, owner3);
    }

    private ProductDTO createProduct(String productId, String ownerKey, ProductDTO derivedProduct,
        Collection<ProductDTO> providedProducts) {
        ProductDTO newProduct = new ProductDTO();
        newProduct.setId(productId);
        newProduct.setName(productId);
        if (derivedProduct != null) {
            newProduct.setDerivedProduct(derivedProduct);
        }

        if (providedProducts != null && !providedProducts.isEmpty()) {
            newProduct.setProvidedProducts(new HashSet<>(providedProducts));
        }

        return ownerProductApi.createProduct(ownerKey, newProduct);
    }

    private PoolDTO createPool(String ownerKey, ProductDTO product) {
        PoolDTO pool = new PoolDTO();
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());
        pool.setQuantity(10L);
        pool.setStartDate(Instant.now().atOffset(ZoneOffset.UTC));
        pool.setEndDate(Instant.now().plus(10, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC));

        return ownerApi.createPool(ownerKey, pool);
    }

    private void verifyRefreshPoolsForProducts(AsyncJobStatusDTO job) {
        assertEquals("Refresh Pools", job.getName());

        AsyncJobStatusDTO finishedJobStatus = jobsClient.waitForJob(job);
        assertEquals("FINISHED", finishedJobStatus.getState());
    }

    @Nested
    @Isolated
    @Execution(ExecutionMode.SAME_THREAD)
    class GetPages {
        @Test
        public void shouldListProductsPagedAndSorted() {
            ApiClient adminClient = ApiClients.admin();
            OwnerDTO owner = ownerApi.createOwner(Owners.random());

            // Ensure that there is data for this test.
            // Most likely, there will already be data left from other test runs that will show up
            IntStream.range(0, 5).forEach(entry -> {
                createProduct("test_product-" + entry, owner.getKey(), null, null);

                // for timestamp separation
                try {
                    sleep(1000);
                }
                catch (InterruptedException ie) {
                    throw new RuntimeException("Unable to sleep as expected");
                }
            });

            Request request = Request.from(adminClient)
                .setPath("/products")
                .addQueryParam("page", "1")
                .addQueryParam("per_page", "4")
                .addQueryParam("order", "asc")
                .addQueryParam("sort_by", "created")
                .addQueryParam("active", "include");

            Response response = request.execute();
            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ProductDTO> products = response.deserialize(new TypeReference<List<ProductDTO>>() {});
            assertThat(products)
                .isNotNull()
                .hasSize(4);
            assertThat(products.get(0).getCreated().compareTo(products.get(1).getCreated()))
                .isNotPositive();
            assertThat(products.get(1).getCreated().compareTo(products.get(2).getCreated()))
                .isNotPositive();
            assertThat(products.get(2).getCreated().compareTo(products.get(3).getCreated()))
                .isNotPositive();
        }
    }

    private void compareOwner(OwnerDTO expected, OwnerDTO actual) {
        assertEquals(expected.getCreated(), actual.getCreated());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getDisplayName(), actual.getDisplayName());
        assertEquals(expected.getKey(), actual.getKey());
        assertEquals(expected.getContentPrefix(), actual.getContentPrefix());
        assertEquals(expected.getDefaultServiceLevel(), actual.getDefaultServiceLevel());
        assertEquals(expected.getLogLevel(), actual.getLogLevel());
        assertEquals(expected.getContentAccessMode(), actual.getContentAccessMode());
        assertEquals(expected.getContentAccessModeList(), actual.getContentAccessModeList());
        assertEquals(expected.getAutobindDisabled(), actual.getAutobindDisabled());
        assertEquals(expected.getLastRefreshed(), actual.getLastRefreshed());
        assertEquals(expected.getParentOwner(), actual.getParentOwner());
        assertEquals(expected.getUpstreamConsumer(), actual.getUpstreamConsumer());
    }
}

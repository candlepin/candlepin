/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.resource.ProductsApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.JobsClient;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.SpecTestFixture;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@SpecTest
public class ProductResourceSpecTest extends SpecTestFixture {
    private ProductsApi productsApi;
    private OwnerApi ownerApi;
    private OwnerProductApi ownerProductApi;
    private JobsClient jobsClient;

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
    public void beforeAll() throws Exception {
        ApiClient client = ApiClients.admin();
        productsApi = client.products();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        jobsClient = getClient(JobsClient.class);
    }

    @Test
    @DisplayName("retrieves owners by product")
    public void retrieveOwnersByProduct() throws Exception {
        List<OwnerDTO> expectedOwners = setupOrgProductsAndPools();
        OwnerDTO owner1 = expectedOwners.get(0);
        OwnerDTO owner2 = expectedOwners.get(1);

        List<OwnerDTO> actual = productsApi.getProductOwners(Arrays.asList(p4ProductId));
        assertEquals(1, actual.size());
        compareOwner(owner1, actual.get(0));

        actual = productsApi.getProductOwners(Arrays.asList(p5dProductId));
        assertEquals(1, actual.size());
        compareOwner(owner2, actual.get(0));

        actual = productsApi.getProductOwners(Arrays.asList(p1ProductId));
        assertEquals(3, actual.size());

        for (OwnerDTO expectedOwner : expectedOwners) {
            boolean found = false;
            for (OwnerDTO actualOwner : actual) {
                if (expectedOwner.getKey().equals(actualOwner.getKey())) {
                    found = true;
                }
            }

            assertTrue(found);
        }

        // Unknown product
        assertEquals(0, productsApi.getProductOwners(Arrays.asList("UnknownProductId")).size());
    }

    // TODO: Add OnlyInHosted annotation and uncomment when ENT-4612 is completed
    //@Test
    @DisplayName("refreshes pools for orgs owning products")
    public void refreshPoolsForOrgsOwningProducts() throws Exception {
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
    @DisplayName("throws exception on get_owners with no products")
    public void getOwnersWithNoProductsException() throws Exception {
        assertBadRequest(() -> productsApi.getProductOwners(new ArrayList<>()));
    }

    @Test
    @DisplayName("throws exception on refresh with no products")
    public void refreshWithNoProductsException() throws Exception {
        assertBadRequest(() -> productsApi.refreshPoolsForProducts(new ArrayList<>(), false));
    }

    @Test
    @DisplayName("should fail when fetching non-existing products")
    public void getNonExistingProducts() {
        assertNotFound(() -> productsApi.getProduct("unknown-product-id"));
    }

    private List<OwnerDTO> setupOrgProductsAndPools() throws ApiException {
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
        Collection<ProductDTO> providedProducts) throws ApiException {
        ProductDTO newProduct = new ProductDTO();
        newProduct.setId(productId);
        newProduct.setName(productId);
        if (derivedProduct != null) {
            newProduct.setDerivedProduct(derivedProduct);
        }

        if (providedProducts != null && !providedProducts.isEmpty()) {
            newProduct.setProvidedProducts(new HashSet<>(providedProducts));
        }

        return ownerProductApi.createProductByOwner(ownerKey, newProduct);
    }

    private PoolDTO createPool(String ownerKey, ProductDTO product) throws ApiException {
        PoolDTO pool = new PoolDTO();
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());
        pool.setQuantity(10L);
        pool.setStartDate(Instant.now().atOffset(ZoneOffset.UTC));
        pool.setEndDate(Instant.now().plus(10, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC));
        return ownerApi.createPool(ownerKey, pool);
    }

    private void verifyRefreshPoolsForProducts(AsyncJobStatusDTO job)
        throws ApiException, InterruptedException {
        assertEquals("Refresh Pools", job.getName());
        AsyncJobStatusDTO finishedJobStatus = jobsClient.waitForJobToComplete(job.getId());
        assertEquals("FINISHED", finishedJobStatus.getState());
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

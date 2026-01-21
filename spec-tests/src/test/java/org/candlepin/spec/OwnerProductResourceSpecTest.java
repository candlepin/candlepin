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
package org.candlepin.spec;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.ActivationKeyApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.google.common.collect.Iterables;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@SpecTest
public class OwnerProductResourceSpecTest {

    private static ApiClient client;
    private static ActivationKeyApi activationKeyApi;
    private static ConsumerClient consumerApi;
    private static OwnerClient ownerApi;
    private static OwnerContentApi ownerContentApi;
    private static OwnerProductApi ownerProductApi;
    private static ProductsApi productsApi;
    private static HostedTestApi hostedTestApi;
    private static JobsClient jobsApi;

    @BeforeAll
    public static void beforeAll() throws Exception {
        client = ApiClients.admin();
        activationKeyApi = client.activationKeys();
        consumerApi = client.consumers();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        productsApi = client.products();
        hostedTestApi = client.hosted();
        jobsApi = client.jobs();
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        ProductDTO output = ownerProductApi.createProduct(owner.getKey(), Products.random());

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldUpdateGeneratedFieldsWhenUpdatingOwnerProducts() throws Exception {
        OwnerDTO owner = this.ownerApi.createOwner(Owners.random());
        ProductDTO entity = this.ownerProductApi.createProduct(owner.getKey(), Products.random());

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setName(entity.getName() + "-update");
        ProductDTO output = this.ownerProductApi.updateProduct(owner.getKey(), entity.getId(), entity);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldAllowCreatingProductsInOrgsWithLongKeys() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random()
            .key(StringUtil.random("test_org-", 245, StringUtil.CHARSET_NUMERIC_HEX)));

        ProductDTO prod = Products.randomEng();

        ProductDTO created = ownerProductApi.createProduct(owner.getKey(), prod);
        assertThat(created)
            .usingRecursiveComparison()
            .comparingOnlyFields("id", "name")
            .isEqualTo(prod);
    }

    @Test
    public void shouldFailWhenFetchingNonExistingProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        assertNotFound(() -> ownerProductApi.getProductById(owner.getKey(), "bad product id"));
    }

    @Test
    public void shouldUpdateIndividualProductFields() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.randomEng();
        prod.setMultiplier(2L);
        prod.setAttributes(List.of(ProductAttributes.Roles.withValue("role1")));
        prod.setDependentProductIds(Set.of(StringUtil.random("id"), StringUtil.random("id")));
        prod = ownerProductApi.createProduct(owner.getKey(), prod);

        // Ensure the dates are at least one second different
        sleep(1000);

        ProductDTO prod2 = Products.randomEng();
        prod2.setMultiplier(4L);
        prod2 = ownerProductApi.createProduct(owner.getKey(), prod2);

        assertNotEquals(prod.getName(), prod2.getName());
        assertNotEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertNotEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());

        // Verify Name change
        prod.setName(prod2.getName());
        prod = ownerProductApi.updateProduct(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getName(), prod2.getName());
        assertNotEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertNotEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());

        // the idea here is attributes should not change if set equal to null
        // then updated, so store it as a temp variable to compare to after
        // update_product is called.
        List<AttributeDTO> tempAttributes = prod.getAttributes();

        prod.setMultiplier(prod2.getMultiplier());
        prod.setAttributes(null);
        prod = ownerProductApi.updateProduct(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertEquals(tempAttributes.size(), prod.getAttributes().size());
        assertEquals(prod.getAttributes().get(0), tempAttributes.get(0));

        // Verify dependent products update
        prod.setDependentProductIds(prod2.getDependentProductIds());
        prod = ownerProductApi.updateProduct(owner.getKey(), prod.getId(), prod);
        assertEquals(prod.getDependentProductIds(), prod2.getDependentProductIds());
        assertEquals(prod.getMultiplier(), prod2.getMultiplier());
        assertEquals(tempAttributes.size(), prod.getAttributes().size());
        assertEquals(prod.getAttributes().get(0), tempAttributes.get(0));
    }

    @Test
    public void shouldNotUpdateProductNameWithNullValue() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.randomEng();
        String prodName = prod.getName();
        prod = ownerProductApi.createProduct(owner.getKey(), prod);
        assertEquals(prodName, prod.getName());

        prod.setName(null);
        prod = ownerProductApi.updateProduct(owner.getKey(), prod.getId(), prod);

        assertEquals(prodName, prod.getName());
    }

    @Test
    public void shouldAllowAddingContentToProducts() throws Exception {
        OwnerDTO owner = this.ownerApi.createOwner(Owners.random());
        ContentDTO content = this.ownerContentApi.createContent(owner.getKey(), Contents.random());
        ProductDTO product = ownerProductApi.createProduct(owner.getKey(), Products.random());

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        Thread.sleep(1100);

        ProductDTO output = this.ownerProductApi
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), true);

        assertThat(output.getProductContent())
            .singleElement()
            .returns(true, ProductContentDTO::getEnabled)
            .extracting(ProductContentDTO::getContent)
            .isEqualTo(content);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(product.getCreated())
            .isBeforeOrEqualTo(output.getUpdated());

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(product.getUpdated())
            .isAfterOrEqualTo(product.getCreated())
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    public void shouldAllowRemovingContentFromProducts() throws Exception {
        OwnerDTO owner = this.ownerApi.createOwner(Owners.random());
        ContentDTO content = this.ownerContentApi.createContent(owner.getKey(), Contents.random());
        ProductDTO product = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .addProductContentItem(Contents.toProductContent(content, true)));

        assertThat(product.getProductContent())
            .singleElement()
            .extracting(ProductContentDTO::getContent)
            .isEqualTo(content);

        Thread.sleep(1100);

        ProductDTO output = this.ownerProductApi
            .removeContentFromProduct(owner.getKey(), product.getId(), content.getId());

        assertThat(output.getProductContent())
            .isNotNull()
            .isEmpty();

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(product.getCreated())
            .isBeforeOrEqualTo(output.getUpdated());

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(product.getUpdated())
            .isAfterOrEqualTo(product.getCreated())
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    public void shouldAllowRegularUsersToViewProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(client, owner);
        ProductDTO expectedProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());

        ApiClient readOnlyclient = ApiClients.basic(readOnlyUser.getUsername(), readOnlyUser.getPassword());
        OwnerProductApi readOnlyOwnerProductApi = readOnlyclient.ownerProducts();
        ProductDTO actual = readOnlyOwnerProductApi
            .getProductById(owner.getKey(), expectedProduct.getId());
        assertEquals(expectedProduct, actual);
    }

    @Test
    public void shouldCreateTwoProductsWithTheSameName() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO prod = Products.random();
        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), prod);

        prod.setId(StringUtil.random("id"));
        ProductDTO prod2 = ownerProductApi.createProduct(owner.getKey(), prod);
        assertNotEquals(prod1.getId(), prod2.getId());
        assertEquals(prod1.getName(), prod2.getName());
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshPoolsForOrgsOwningProducts() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());

        ProductDTO product1 = Products.random();
        ownerProductApi.createProduct(owner1.getKey(), product1);
        ownerProductApi.createProduct(owner2.getKey(), product1);
        ownerProductApi.createProduct(owner3.getKey(), product1);

        ProductDTO product2 = Products.random();
        ownerProductApi.createProduct(owner1.getKey(), product2);
        ownerProductApi.createProduct(owner2.getKey(), product2);

        ProductDTO product3 = Products.random();
        ownerProductApi.createProduct(owner2.getKey(), product3);
        ownerProductApi.createProduct(owner3.getKey(), product3);

        ProductDTO prod4 = ownerProductApi.createProduct(owner1.getKey(), Products.random());
        ProductDTO prod4d = ownerProductApi.createProduct(owner1.getKey(), Products.random());
        ProductDTO prod5 = ownerProductApi.createProduct(owner2.getKey(), Products.random());
        ProductDTO prod5d = ownerProductApi.createProduct(owner2.getKey(), Products.random());
        ProductDTO prod6 = ownerProductApi.createProduct(owner3.getKey(), Products.random());
        ProductDTO prod6d = ownerProductApi.createProduct(owner3.getKey(), Products.random());

        PoolDTO pool1 = Pools.random(prod4);
        pool1.setDerivedProductId(prod4d.getId());
        pool1.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId())));
        pool1.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product2.getId())));
        ownerApi.createPool(owner1.getKey(), pool1);

        PoolDTO pool2 = Pools.random(prod5);
        pool2.setDerivedProductId(prod5d.getId());
        pool2.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId()),
            new ProvidedProductDTO().productId(product2.getId())));
        pool2.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product3.getId())));
        ownerApi.createPool(owner2.getKey(), pool2);

        PoolDTO pool3 = Pools.random(prod6);
        pool3.setDerivedProductId(prod6d.getId());
        pool3.setProvidedProducts(Set.of(new ProvidedProductDTO().productId(product1.getId())));
        pool3.setDerivedProvidedProducts(Set.of(new ProvidedProductDTO().productId(product3.getId())));
        ownerApi.createPool(owner3.getKey(), pool3);

        verifyRefreshPoolJob(owner1.getKey(), prod4.getId(), true);
        verifyRefreshPoolJob(owner2.getKey(), prod5d.getId(), true);
        verifyRefreshPoolJob(owner3.getKey(), product1.getId(), true);
        verifyRefreshPoolJob(owner3.getKey(), product3.getId(), true);

        assertNotFound(() -> ownerProductApi.refreshPoolsForProduct(owner1.getKey(), "bad_id", true));
    }

    @Test
    public void shouldNotAllowUpdatingIDFields() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ProductDTO created = adminClient.ownerProducts().createProduct(owner.getKey(), Products.random());
        assertNotNull(created);

        // Cache the ID since we're about to clobber it
        String id = created.getId();

        // These fields should be silently ignored during an update
        ProductDTO update = Products.copy(created)
            .uuid("updated_uuid")
            .id("updated_id");

        ProductDTO updated = adminClient.ownerProducts().updateProduct(owner.getKey(), id, update);
        assertNotNull(updated);

        // Both DTOs should be identical yet, since the fields changed aren't changeable
        assertEquals(created, updated);

        ProductDTO fetched = adminClient.ownerProducts().getProductById(owner.getKey(), id);
        assertNotNull(fetched);

        // Same here; should still be identical
        assertEquals(created, fetched);
        assertEquals(updated, fetched);
    }

    @Test
    public void shouldReturnCorrectExceptionForConstraintViolations() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        AttributeDTO attribute = ProductAttributes.SupportLevel.withValue(StringUtil.random(400));
        ProductDTO product = Products.withAttributes(attribute);
        assertBadRequest(() -> ownerProductApi.createProduct(owner.getKey(), product));
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteProductAttachedToPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ProductDTO product = this.ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool = this.ownerApi.createPool(owner.getKey(), Pools.random(product));

        assertBadRequest(() -> ownerProductApi.removeProduct(owner.getKey(), product.getId()));

        assertThat(this.productsApi.getProductByUuid(product.getUuid()))
            .isNotNull();
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteProvidedProductAttachedToProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ProductDTO product = this.ownerProductApi.createProduct(owner.getKey(), Products.random());

        ProductDTO parent = this.ownerProductApi.createProduct(owner.getKey(), Products.random()
            .addProvidedProductsItem(product));

        assertBadRequest(() -> this.ownerProductApi.removeProduct(owner.getKey(), product.getId()));

        assertThat(this.productsApi.getProductByUuid(product.getUuid()))
            .isNotNull();

        assertThat(this.productsApi.getProductByUuid(parent.getUuid()))
            .isNotNull()
            .extracting(ProductDTO::getProvidedProducts, as(collection(ProductDTO.class)))
            .isNotNull()
            .hasSize(1)
            .contains(product);
    }

    @Test
    public void shouldReturnBadRequestOnAttemptToDeleteDerivedProductAttachedToProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ProductDTO product = this.ownerProductApi.createProduct(owner.getKey(), Products.random());

        ProductDTO parent = this.ownerProductApi.createProduct(owner.getKey(), Products.random()
            .derivedProduct(product));

        assertBadRequest(() -> this.ownerProductApi.removeProduct(owner.getKey(), product.getId()));

        assertThat(this.productsApi.getProductByUuid(product.getUuid()))
            .isNotNull();

        assertThat(this.productsApi.getProductByUuid(parent.getUuid()))
            .isNotNull()
            .extracting(ProductDTO::getDerivedProduct)
            .isNotNull()
            .isEqualTo(product);
    }

    @Test
    public void shouldCreateAndDeleteProductsWithBrandingCorrectly() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = Products.random();
        product.setBranding(Set.of(Branding.random(product), Branding.random(product)));
        product = ownerProductApi.createProduct(ownerKey, product);
        Set<String> expectedBranding = getBrandingNames(product);
        assertEquals(2, expectedBranding.size());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        product = ownerProductApi.getProductById(ownerKey, product.getId());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        String newName = StringUtil.random("name");
        product.setName(newName);
        product = ownerProductApi.updateProduct(ownerKey, product.getId(), product);
        assertEquals(newName, product.getName());
        assertThat(getBrandingNames(product)).hasSameElementsAs(expectedBranding);

        ownerProductApi.removeProduct(ownerKey, product.getId());

        final String productUuid = product.getUuid();
        assertNotFound(() -> productsApi.getProductByUuid(productUuid));

        final String productId = product.getId();
        assertNotFound(() ->  ownerProductApi.getProductById(ownerKey, productId));
    }

    private Set<String> getBrandingNames(ProductDTO product) {
        return product.getBranding().stream()
            .map(BrandingDTO::getName)
            .collect(Collectors.toSet());
    }

    @Test
    public void shouldBeAbleToCreateProductWithProvidedProduct() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product1 = ownerProductApi.createProduct(ownerKey, Products.random());
        ProductDTO product2 = ownerProductApi.createProduct(ownerKey, Products.random());

        ProductDTO product = Products.random();
        product.setProvidedProducts(Set.of(product1, product2));
        product = ownerProductApi.createProduct(ownerKey, product);

        product = ownerProductApi.getProductById(ownerKey, product.getId());
        assertNotNull(product);
        assertTrue(product.getProvidedProducts().contains(product1));
        assertTrue(product.getProvidedProducts().contains(product2));
    }

    @Test
    public void shouldBeAbleToUpdateProductWithProvidedProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product1 = ownerProductApi.createProduct(ownerKey, Products.random());
        ownerProductApi.createProduct(ownerKey, Products.random());

        ProductDTO product = createProductWithProvidedAndDerivedProductAndPool(ownerKey);
        product.setProvidedProducts(Set.of(product1));
        product = ownerProductApi.updateProduct(ownerKey, product.getId(), product);

        assertEquals(1, product.getProvidedProducts().size());
        ProductDTO providedProduct = Iterables.getOnlyElement(product.getProvidedProducts());
        assertEquals(product1.getId(), providedProduct.getId());
    }

    @Test
    public void shouldAllowDeletingAProductAssociatedWithAnActivationKey() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ProductDTO product = ownerProductApi.createProduct(ownerKey, Products.random());
        ActivationKeyDTO activationKey = ownerApi.createActivationKey(ownerKey, ActivationKeys.random(owner));
        activationKeyApi.addProductIdToKey(activationKey.getId(), product.getId());

        // The activation key should have the product associated with it
        ActivationKeyDTO actual = activationKeyApi.getActivationKey(activationKey.getId());
        assertNotNull(actual);
        List<ActivationKeyProductDTO> akProducts = actual.getProducts().stream()
            .filter(p -> p.getProductId().equals(product.getId()))
            .collect(Collectors.toList());
        assertEquals(1, akProducts.size());

        // Deleting the product should remove its reference from the activation key
        ownerProductApi.removeProduct(ownerKey, product.getId());

        // The activation key should still exist, but should no longer reference the product
        actual = activationKeyApi.getActivationKey(activationKey.getId());
        assertNotNull(actual);
        assertEquals(0, actual.getProducts().size());
    }

    private static Stream<Arguments> criticalProductStringFieldsAndValues() {
        Set<String> fields = Set.of("id", "name");
        List<String> values = Arrays.asList("", "  ", null);
        List<Arguments> matrix = new ArrayList<>();

        for (String field : fields) {
            for (String value : values) {
                matrix.add(Arguments.of(field, value));
            }
        }

        return matrix.stream();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @MethodSource("criticalProductStringFieldsAndValues")
    public void shouldRequireValidCriticalStringFieldsWhenInsertingProduct(String fieldName, String value)
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ObjectNode productNode = ApiClient.MAPPER.readValue(Products.random().toJson(), ObjectNode.class);
        ObjectNode nullNode = null;
        productNode = value == null ? productNode.set(fieldName, nullNode) :
            productNode.put(fieldName, value);

        Response response = Request.from(client)
            .setPath("/owners/{owner_key}/products")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(productNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .containsIgnoringCase("product has a null or invalid " + fieldName);
    }

    private static Stream<String> critialProductFields() {
        return Stream.of("id", "name");
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("critialProductFields")
    public void shouldRequirePopulatedCriticalStringFieldsWhenInsertingProduct(String fieldName)
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ObjectNode productNode = ApiClient.MAPPER.readValue(Products.random().toJson(), ObjectNode.class);

        productNode.remove(fieldName);

        Response response = Request.from(client)
            .setPath("/owners/{owner_key}/products")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(productNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .containsIgnoringCase("product has a null or invalid " + fieldName);
    }

    @Nested
    @OnlyInHosted
    public class LockedEntityTests {
        private OwnerProductApi ownerProductApi;

        private OwnerDTO owner;
        private ProductDTO derivedProvProduct;
        private ProductDTO providedProduct;
        private ProductDTO product;

        @BeforeEach
        public void setup() {
            ApiClient client = ApiClients.admin();
            this.owner = client.owners().createOwner(Owners.random());
            this.ownerProductApi = client.ownerProducts();
            HostedTestApi hosted = client.hosted();

            derivedProvProduct = hosted.createProduct(Products.random());
            ProductDTO derivedProduct = Products.random();
            derivedProduct.setProvidedProducts(Set.of(derivedProvProduct));
            derivedProduct = hosted.createProduct(derivedProduct);

            providedProduct = hostedTestApi.createProduct(Products.random());
            product = Products.random();
            product.setProvidedProducts(Set.of(providedProduct));
            product.setDerivedProduct(derivedProduct);
            product = hosted.createProduct(product);

            hosted.createSubscription(Subscriptions.random(owner, product));
            AsyncJobStatusDTO job = ownerApi.refreshPools(this.owner.getKey(), false);
            AsyncJobStatusDTO status = jobsApi.waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }

        @Test
        public void shouldReturnForbiddenRequestWhenDeletingDerivedProvidedProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi
                .removeProduct(owner.getKey(), derivedProvProduct.getId()));
        }

        @Test
        public void shouldReturnForbiddenRequestOnAttemptToDeleteProvidedProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi
                .removeProduct(owner.getKey(), providedProduct.getId()));
        }

        @Test
        public void shouldReturnForbiddenRequestOnAttemptToDeleteProductAttachedToSub() {
            assertForbidden(() -> this.ownerProductApi.removeProduct(owner.getKey(), product.getId()));
        }
    }

    private ProductDTO createProductWithProvidedProduct(String ownerKey) {
        ProductDTO derivedProvProduct = ownerProductApi.createProduct(ownerKey, Products.random());
        ProductDTO derivedProduct = Products.random();
        derivedProduct.setProvidedProducts(Set.of(derivedProvProduct));
        derivedProduct = ownerProductApi.createProduct(ownerKey, derivedProduct);

        return derivedProduct;
    }

    private ProductDTO createProductWithProvidedAndDerivedProductAndPool(String ownerKey) {
        ProductDTO derivedProduct = createProductWithProvidedProduct(ownerKey);
        ProductDTO provProduct = ownerProductApi.createProduct(ownerKey, Products.random());
        ProductDTO product = Products.random();
        product.setProvidedProducts(Set.of(provProduct));
        product.setDerivedProduct(derivedProduct);
        product = ownerProductApi.createProduct(ownerKey, product);

        ownerApi.createPool(ownerKey, Pools.random(product));

        return product;
    }

    private void verifyRefreshPoolJob(String ownerKey, String productId, boolean lazyRegen) {
        AsyncJobStatusDTO job = ownerProductApi.refreshPoolsForProduct(ownerKey, productId, lazyRegen);
        assertNotNull(job);
        AsyncJobStatusDTO status = jobsApi.waitForJob(job.getId());
        assertEquals("RefreshPoolsForProductJob", status.getKey());
        assertEquals("FINISHED", status.getState());
    }

    @Nested
    @Isolated
    @TestInstance(Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    public class ProductQueryTests {

        private static final String QUERY_PATH = "/owners/{owner_key}/products";

        private static final String INCLUSION_INCLUDE = "include";
        private static final String INCLUSION_EXCLUDE = "exclude";
        private static final String INCLUSION_EXCLUSIVE = "exclusive";

        private static record ProductInfo(ProductDTO dto, OwnerDTO owner, Set<String> activeOwners) {
            public String id() {
                return this.dto() != null ? this.dto().getId() : null;
            }

            public boolean active() {
                return !this.activeOwners().isEmpty();
            }

            public boolean custom() {
                return this.owner() != null;
            }
        };

        private ApiClient adminClient;

        private List<OwnerDTO> owners;
        private Map<String, ProductInfo> productMap;

        /**
         * Verifies either the manifest generator extension or the hosted test extension is present as
         * required by the current operating mode.
         */
        private void checkRequiredExtensions() {
            if (CandlepinMode.isStandalone()) {
                assumeTrue(CandlepinMode::hasManifestGenTestExtension);
            }
            else {
                assumeTrue(CandlepinMode::hasHostedTestExtension);
            }
        }

        private OwnerDTO createOwner(String keyPrefix) {
            OwnerDTO owner = Owners.random()
                .key(StringUtil.random(keyPrefix + "-"));

            return this.adminClient.owners()
                .createOwner(owner);
        }

        private SubscriptionDTO createSubscription(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Impl note:
            // We create active subscriptions in the future to work around a limitation on manifests
            // disallowing and ignoring expired pools
            return Subscriptions.random(owner, product)
                .startDate(active ? now.minus(7, ChronoUnit.DAYS) : now.plus(7, ChronoUnit.DAYS))
                .endDate(now.plus(30, ChronoUnit.DAYS));
        }

        private PoolDTO createPool(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            PoolDTO pool = Pools.random(product)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(active ? now.plus(7, ChronoUnit.DAYS) : now.minus(3, ChronoUnit.DAYS));

            return this.adminClient.owners()
                .createPool(owner.getKey(), pool);
        }

        private void mapProductInfo(ProductDTO product, OwnerDTO owner, OwnerDTO activeOwner) {
            Set<String> activeOwnerKeys = new HashSet<>();
            if (activeOwner != null) {
                activeOwnerKeys.add(activeOwner.getKey());
            }

            ProductInfo existing = this.productMap.get(product.getId());
            if (existing != null) {
                activeOwnerKeys.addAll(existing.activeOwners());
            }

            ProductInfo pinfo = new ProductInfo(product, owner, activeOwnerKeys);
            this.productMap.put(product.getId(), pinfo);

        }

        private void commitGlobalSubscriptions(Collection<SubscriptionDTO> subscriptions) throws Exception {
            Map<String, List<SubscriptionDTO>> subscriptionMap = new HashMap<>();

            for (SubscriptionDTO subscription : subscriptions) {
                NestedOwnerDTO owner = subscription.getOwner();

                subscriptionMap.computeIfAbsent(owner.getKey(), key -> new ArrayList<>())
                    .add(subscription);
            }

            for (Map.Entry<String, List<SubscriptionDTO>> entry : subscriptionMap.entrySet()) {
                String ownerKey = entry.getKey();
                List<SubscriptionDTO> ownerSubs = entry.getValue();

                AsyncJobStatusDTO job;
                if (CandlepinMode.isStandalone()) {
                    File manifest = new ExportGenerator()
                        .addSubscriptions(ownerSubs)
                        .export();

                    job = this.adminClient.owners()
                        .importManifestAsync(ownerKey, List.of(), manifest);
                }
                else {
                    ownerSubs.forEach(subscription -> this.adminClient.hosted()
                        .createSubscription(subscription, true));

                    job = this.adminClient.owners()
                        .refreshPools(ownerKey, false);
                }

                assertThatJob(job)
                    .isNotNull()
                    .terminates(this.adminClient)
                    .isFinished();
            }
        }

        @BeforeAll
        public void setup() throws Exception {
            // Ensure we have our required test extensions or we'll be very broken...
            this.checkRequiredExtensions();

            this.adminClient = ApiClients.admin();

            this.owners = List.of(
                this.createOwner("owner1"),
                this.createOwner("owner2"),
                this.createOwner("owner3"));

            this.productMap = new HashMap<>();

            List<SubscriptionDTO> subscriptions = new ArrayList<>();

            // Dummy org we use for creating a bunch of future pools for our global products. We'll also
            // create per-org subscriptions for these products later. Also note that these will never be
            // fully resolved.
            OwnerDTO globalOwner = this.createOwner("global");

            List<ProductDTO> globalProducts = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                ProductDTO gprod = new ProductDTO()
                    .id("g-prod-" + i)
                    .name("global product " + i);

                subscriptions.add(this.createSubscription(globalOwner, gprod, false));
                globalProducts.add(gprod);
                this.mapProductInfo(gprod, null, null);
            }

            for (int oidx = 1; oidx <= owners.size(); ++oidx) {
                OwnerDTO owner = owners.get(oidx - 1);

                List<ProductDTO> ownerProducts = new ArrayList<>();
                for (int i = 1; i <= 2; ++i) {
                    ProductDTO cprod = new ProductDTO()
                        .id(String.format("o%d-prod-%d", oidx, i))
                        .name(String.format("%s product %d", owner.getKey(), i));

                    cprod = this.adminClient.ownerProducts()
                        .createProduct(owner.getKey(), cprod);

                    ownerProducts.add(cprod);
                    this.mapProductInfo(cprod, owner, null);
                }

                // Create an active and inactive global subscription for this org
                subscriptions.add(this.createSubscription(owner, globalProducts.get(0), true));
                subscriptions.add(this.createSubscription(owner, globalProducts.get(1), false));
                this.mapProductInfo(globalProducts.get(0), null, owner);

                // Create an active and inactive custom subscription
                this.createPool(owner, ownerProducts.get(0), true);
                this.createPool(owner, ownerProducts.get(1), false);
                this.mapProductInfo(ownerProducts.get(0), owner, owner);
            }

            this.commitGlobalSubscriptions(subscriptions);
        }

        // Impl note:
        // Since we cannot depend on the global state being prestine from run to run (or even test to
        // test), we cannot use exact matching on our output as extraneous products from previous test
        // runs or otherwise pre-existing data may show up in the result set. Instead, these tests will
        // verify our expected products are present, and unexpected products from the test data are not.

        private List<String> getUnexpectedIds(List<String> expectedIds) {
            return this.productMap.values()
                .stream()
                .map(ProductInfo::dto)
                .map(ProductDTO::getId)
                .filter(Predicate.not(expectedIds::contains))
                .toList();
        }

        private Predicate<ProductInfo> buildOwnerProductPredicate(OwnerDTO owner) {
            return pinfo -> pinfo.owner() == null || owner.getKey().equals(pinfo.owner().getKey());
        }

        @Test
        public void shouldAllowQueryingWithoutAnyFilters() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldDefaultToActiveExclusiveCustomInclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, null, null);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldFailWithInvalidOwners() {
            assertNotFound(() -> this.adminClient.ownerProducts()
                .getProductsByOwner("invalid_owner", null, null, null, null));
        }

        @Test
        public void shouldAllowFilteringOnIDs() {
            // Randomly seeded for consistency; seed itself chosen randomly
            Random rand = new Random(58258);

            OwnerDTO owner = this.owners.get(1);

            List<String> pids = this.productMap.values()
                .stream()
                .filter(elem -> rand.nextBoolean())
                .map(ProductInfo::id)
                .toList();

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .map(ProductInfo::id)
                .filter(pids::contains)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), pids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_id" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidIds(String pid) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            OwnerDTO owner = this.owners.get(1);

            ProductDTO expected = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .findAny()
                .map(ProductInfo::dto)
                .get();

            List<String> expectedPids = List.of(expected.getId());

            List<String> pids = Arrays.asList(expected.getId(), pid);

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), pids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringOnName() {
            Random rand = new Random(55851);

            OwnerDTO owner = this.owners.get(1);

            List<String> names = this.productMap.values()
                .stream()
                .filter(elem -> rand.nextBoolean())
                .map(pinfo -> pinfo.dto().getName())
                .toList();

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> names.contains(pinfo.dto().getName()))
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, names, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_name" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidNames(String name) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            OwnerDTO owner = this.owners.get(1);

            ProductDTO expected = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .findAny()
                .map(ProductInfo::dto)
                .get();

            List<String> expectedPids = List.of(expected.getId());

            List<String> names = Arrays.asList(expected.getName(), name);

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, names, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithActiveIncluded() {
            // This is effectively the same as no filter -- we expect everything within the org back
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids);
        }

        @Test
        public void shouldAllowFilteringWithActiveExcluded() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> !pinfo.active())
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithActiveExclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldErrorWithInvalidActiveInclusion() {
            OwnerDTO owner = this.owners.get(1);

            assertBadRequest(() -> this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, "invalid_type", INCLUSION_INCLUDE));
        }

        @Test
        public void shouldAllowFilteringWithCustomIncluded() {
            // This is effectively the same as no filter -- we expect everything in the org back
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids);
        }

        @Test
        public void shouldAllowFilteringWithCustomExcluded() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> !pinfo.custom())
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithCustomExclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::custom)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldErrorWithInvalidCustomInclusion() {
            OwnerDTO owner = this.owners.get(1);

            assertBadRequest(() -> this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, "invalid_type"));
        }

        @Test
        public void shouldAllowQueryingWithMultipleFilters() {
            Random rand = new Random(14023);

            OwnerDTO owner = this.owners.get(1);

            // Hand pick a product to ensure that we have *something* that comes out of the filter, should
            // our random selection process not have any intersection.
            ProductInfo selected = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .findAny()
                .get();

            List<String> pids = this.productMap.values()
                .stream()
                .filter(elem -> rand.nextBoolean())
                .map(ProductInfo::id)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> names = this.productMap.values()
                .stream()
                .filter(elem -> rand.nextBoolean())
                .map(pinfo -> pinfo.dto().getName())
                .collect(Collectors.toCollection(ArrayList::new));

            pids.add(selected.dto().getId());
            names.add(selected.dto().getName());

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> pids.contains(pinfo.dto().getId()))
                .filter(pinfo -> names.contains(pinfo.dto().getName()))
                .map(ProductInfo::id)
                .toList();

            assertThat(expectedPids)
                .isNotEmpty();

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), pids, names, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
        public void shouldAllowQueryingInPages(int pageSize) {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::custom)
                .map(ProductInfo::id)
                .toList();

            List<String> received = new ArrayList<>();

            int page = 0;
            while (true) {
                Response response = Request.from(this.adminClient)
                    .setPath(QUERY_PATH)
                    .setPathParam("owner_key", owner.getKey())
                    .addQueryParam("page", String.valueOf(++page))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .addQueryParam("active", "include")
                    .addQueryParam("custom", "exclusive")
                    .execute();

                assertEquals(200, response.getCode());

                List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});
                assertNotNull(output);

                if (output.isEmpty()) {
                    break;
                }

                output.stream()
                    .map(ProductDTO::getId)
                    .sequential()
                    .forEach(received::add);
            }

            assertThat(received)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("page", String.valueOf(page))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("per_page", String.valueOf(pageSize))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithAscendingOrderedOutput(String field) {
            OwnerDTO owner = this.owners.get(1);

            Map<String, Comparator<ProductDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ProductDTO::getId),
                "name", Comparator.comparing(ProductDTO::getName),
                "uuid", Comparator.comparing(ProductDTO::getUuid));

            List<ProductDTO> products = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedPids = products.stream()
                .sorted(comparatorMap.get(field))
                .map(ProductDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "asc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyElementsOf(expectedPids); // this must be an ordered check
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithDescendingOrderedOutput(String field) {
            OwnerDTO owner = this.owners.get(1);

            Map<String, Comparator<ProductDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ProductDTO::getId),
                "name", Comparator.comparing(ProductDTO::getName),
                "uuid", Comparator.comparing(ProductDTO::getUuid));

            List<ProductDTO> products = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedPids = products.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(ProductDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "desc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyElementsOf(expectedPids); // this must be an ordered check
        }

        @Test
        public void shouldFailWithInvalidOrderField() {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", "invalid field")
                .execute();

            assertEquals(400, response.getCode());
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("order", "invalid order")
                .execute();

            assertEquals(400, response.getCode());
        }

        // These tests verify the definition of "active" is properly implemented, ensuring "active" is
        // defined as a product which is attached to a pool which has started and has not expired, or
        // attached to another active product (recursively).
        //
        // This definition is recursive in nature, so the effect is that it should consider any product
        // that is a descendant of a product attached to a non-future pool that hasn't yet expired.

        @Test
        public void shouldOnlySelectActiveProductsFromActivePools() {
            // "active" only considers pools which have started but have not yet expired -- that is:
            // (start time < now() < end time)
            OwnerDTO owner = this.createOwner("test_org");

            ProductDTO prod1 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());
            ProductDTO prod2 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());
            ProductDTO prod3 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());

            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Create three pools: expired, current (active), future
            PoolDTO pool1 = Pools.random(prod1)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(now.minus(3, ChronoUnit.DAYS));
            PoolDTO pool2 = Pools.random(prod2)
                .startDate(now.minus(3, ChronoUnit.DAYS))
                .endDate(now.plus(3, ChronoUnit.DAYS));
            PoolDTO pool3 = Pools.random(prod3)
                .startDate(now.plus(3, ChronoUnit.DAYS))
                .endDate(now.plus(7, ChronoUnit.DAYS));

            this.adminClient.owners().createPool(owner.getKey(), pool1);
            this.adminClient.owners().createPool(owner.getKey(), pool2);
            this.adminClient.owners().createPool(owner.getKey(), pool3);

            // Active = exclusive should only find the active pool; future and expired pools should be omitted
            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .singleElement()
                .isEqualTo(prod2.getId());
        }

        @Test
        public void shouldAlsoIncludeDescendantProductsOfActiveProducts() {
            // "active" includes descendants of products attached to an active pool
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 0; i < 20; ++i) {
                ProductDTO product = new ProductDTO()
                    .id("p" + i)
                    .name("product " + i);

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
            products.get(0).addProvidedProductsItem(products.get(5));
            products.get(0).addProvidedProductsItem(products.get(6));

            products.get(1).addProvidedProductsItem(products.get(2));
            products.get(1).addProvidedProductsItem(products.get(3));

            products.get(3).addProvidedProductsItem(products.get(4));

            products.get(7).setDerivedProduct(products.get(8));
            products.get(7).addProvidedProductsItem(products.get(9));

            products.get(8).addProvidedProductsItem(products.get(10));
            products.get(8).addProvidedProductsItem(products.get(12));

            products.get(10).addProvidedProductsItem(products.get(11));

            products.get(12).addProvidedProductsItem(products.get(13));

            products.get(14).setDerivedProduct(products.get(15));

            products.get(15).setDerivedProduct(products.get(16));

            products.get(17).setDerivedProduct(products.get(18));

            // persist the products in reverse order so we don't hit any linkage errors
            for (int i = products.size() - 1; i >= 0; --i) {
                this.adminClient.ownerProducts().createProduct(owner.getKey(), products.get(i));
            }

            // create some pools to link to our product tree
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(0)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(7)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(8)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(19)));

            List<String> expectedPids = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9",
                "p10", "p11", "p12", "p13", "p19");

            List<ProductDTO> output = this.adminClient.ownerProducts()
                .getProductsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyInAnyOrderElementsOf(expectedPids);
        }
    }
}

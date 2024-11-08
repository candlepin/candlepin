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
package org.candlepin.controller.refresher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.TransactionExecutionException;
import org.candlepin.util.Util;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.EntityManager;


// FIXME: Rewrite this class to not mock the DB-level operations and just use the mock DB as a whole.
// Mocking DB functionality is nonsense and makes maintaining and adding new tests incredibly painful.


/**
 * Test suite for the RefreshWorker class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefreshWorkerTest {

    private EntityManager mockEntityManager;
    private PoolCurator mockPoolCurator;
    private ProductCurator mockProductCurator;
    private ContentCurator mockContentCurator;

    @BeforeEach
    protected void init() {
        this.mockEntityManager = mock(EntityManager.class);
        this.mockPoolCurator = mock(PoolCurator.class);
        this.mockProductCurator = mock(ProductCurator.class);
        this.mockContentCurator = mock(ContentCurator.class);

        TestUtil.mockTransactionalFunctionality(this.mockEntityManager, this.mockPoolCurator,
            this.mockProductCurator, this.mockContentCurator);

        doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .create(Mockito.any(Product.class), anyBoolean());

        doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .create(Mockito.any(Product.class));

        doAnswer(returnsFirstArg())
            .when(this.mockContentCurator)
            .create(Mockito.any(Content.class), anyBoolean());

        doAnswer(returnsFirstArg())
            .when(this.mockContentCurator)
            .create(Mockito.any(Content.class));

        doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .merge(Mockito.any(Product.class));

        doAnswer(returnsFirstArg())
            .when(this.mockContentCurator)
            .merge(Mockito.any(Content.class));
    }

    private void mockProductLookup(Collection<Product> products) {
        products.forEach(prod -> prod.setUuid(Util.generateDbUUID()));

        doAnswer(iom -> {
            List<Product> output = new ArrayList<>();
            Collection<String> productUuids = iom.getArgument(0);

            if (productUuids != null) {
                products.stream()
                    .filter(prod -> productUuids.contains(prod.getUuid()))
                    .forEach(output::add);
            }

            return output;
        }).when(this.mockProductCurator).getProductsByUuids(any(Collection.class));

        doAnswer(iom -> {
            Map<String, Product> output = new HashMap<>();
            Collection<String> productIds = iom.getArgument(0);

            if (productIds != null) {
                products.stream()
                    .filter(prod -> productIds.contains(prod.getId()))
                    .forEach(prod -> output.put(prod.getId(), prod));
            }

            return output;
        }).when(this.mockProductCurator).getProductsByIds(eq(null), any(Collection.class));
    }

    private void mockContentLookup(Collection<Content> contents) {
        doAnswer(iom -> {
            Map<String, Content> output = new HashMap<>();
            Collection<String> contentIds = iom.getArgument(0);

            if (contents != null && contentIds != null) {
                contents.stream()
                    .filter(prod -> contentIds.contains(prod.getId()))
                    .forEach(prod -> output.put(prod.getId(), prod));
            }

            return output;
        }).when(this.mockContentCurator).getContentsByIds(eq(null), any(Collection.class));
    }

    private RefreshWorker buildRefreshWorker() {
        return new RefreshWorker(this.mockPoolCurator, this.mockProductCurator, this.mockContentCurator);
    }

    private SubscriptionInfo mockSubscriptionInfo(String id, ProductInfo pinfo) {
        SubscriptionInfo sinfo = mock(SubscriptionInfo.class);
        doReturn(id).when(sinfo).getId();
        doReturn(pinfo).when(sinfo).getProduct();

        return sinfo;
    }

    private ProductInfo mockProductInfo(String id, String name) {
        ProductInfo pinfo = mock(ProductInfo.class);
        doReturn(id).when(pinfo).getId();
        doReturn(name).when(pinfo).getName();

        return pinfo;
    }

    private ContentInfo mockContentInfo(String id, String name) {
        ContentInfo cinfo = mock(ContentInfo.class);
        doReturn(id).when(cinfo).getId();
        doReturn(name).when(cinfo).getName();

        return cinfo;
    }

    private ProductContentInfo mockProductContentInfo(String id, String name) {
        ContentInfo cinfo = mock(ContentInfo.class);
        doReturn(id).when(cinfo).getId();
        doReturn(name).when(cinfo).getName();

        ProductContentInfo pcinfo = mock(ProductContentInfo.class);
        doReturn(cinfo).when(pcinfo).getContent();

        return pcinfo;
    }

    private Exception buildConstraintViolationException() {
        return new ConstraintViolationException("test exception", new SQLException(), null);
    }

    private Exception buildDatabaseDeadlockException() {
        return new LockAcquisitionException("test exception", new SQLException(), null);
    }

    private void mockChildrenProductLookup(Collection<Product> products) {
        doAnswer(iom -> {
            Collection<String> input = iom.getArgument(0);
            Set<Product> output = new HashSet<>();

            if (input != null) {
                products.stream()
                    .filter(product -> input.contains(product.getUuid()))
                    .map(Product::getProvidedProducts)
                    .flatMap(Collection::stream)
                    .forEach(output::add);

                products.stream()
                    .filter(product -> input.contains(product.getUuid()))
                    .map(Product::getDerivedProduct)
                    .filter(Objects::nonNull)
                    .forEach(output::add);
            }

            return output;
        }).when(this.mockProductCurator)
            .getChildrenProductsOfProductsByUuids(Mockito.any(Collection.class));
    }

    private void mockChildrenContentLookup(Collection<Product> products) {
        doAnswer(iom -> {
            Collection<String> input = iom.getArgument(0);
            Set<Content> output = new HashSet<>();

            if (input != null) {
                products.stream()
                    .filter(product -> input.contains(product.getUuid()))
                    .map(Product::getProductContent)
                    .flatMap(Collection::stream)
                    .map(ProductContent::getContent)
                    .forEach(output::add);
            }

            return output;
        }).when(this.mockContentCurator)
            .getChildrenContentOfProductsByUuids(Mockito.any(Collection.class));
    }

    @Test
    public void testVariadicAddSubscriptions() {
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo("sub-1", null);
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo("sub-2", null);
        SubscriptionInfo sinfo3 = this.mockSubscriptionInfo("sub-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(0, subscriptions.size());

        SubscriptionInfo[] param = new SubscriptionInfo[] { sinfo1, sinfo2, sinfo3 };
        worker.addSubscriptions(param);

        subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(3, subscriptions.size());
        assertThat(subscriptions, hasEntry(sinfo1.getId(), sinfo1));
        assertThat(subscriptions, hasEntry(sinfo2.getId(), sinfo2));
        assertThat(subscriptions, hasEntry(sinfo3.getId(), sinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addSubscriptions(param);

        subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(3, subscriptions.size());
        assertThat(subscriptions, hasEntry(sinfo1.getId(), sinfo1));
        assertThat(subscriptions, hasEntry(sinfo2.getId(), sinfo2));
        assertThat(subscriptions, hasEntry(sinfo3.getId(), sinfo3));
    }

    @Test
    public void testAddSubscriptions() {
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo("sub-1", null);
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo("sub-2", null);
        SubscriptionInfo sinfo3 = this.mockSubscriptionInfo("sub-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(0, subscriptions.size());

        SubscriptionInfo[] param = new SubscriptionInfo[] { sinfo1, sinfo2, sinfo3 };
        worker.addSubscriptions(Arrays.asList(param));

        subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(3, subscriptions.size());
        assertThat(subscriptions, hasEntry(sinfo1.getId(), sinfo1));
        assertThat(subscriptions, hasEntry(sinfo2.getId(), sinfo2));
        assertThat(subscriptions, hasEntry(sinfo3.getId(), sinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addSubscriptions(Arrays.asList(param));

        subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(3, subscriptions.size());
        assertThat(subscriptions, hasEntry(sinfo1.getId(), sinfo1));
        assertThat(subscriptions, hasEntry(sinfo2.getId(), sinfo2));
        assertThat(subscriptions, hasEntry(sinfo3.getId(), sinfo3));
    }

    @Test
    public void testVariadicAddDuplicateSubscriptionUsesMostRecent() {
        String id = "sub_id";
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo(id, null);
        doReturn("123").when(sinfo1).getOrderNumber();
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo(id, null);
        doReturn("ABC").when(sinfo1).getOrderNumber();

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo1);
        worker.addSubscriptions(sinfo2);

        Map<String, ? extends SubscriptionInfo> subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(1, subscriptions.size());

        assertThat(subscriptions, hasKey(id));

        SubscriptionInfo output = subscriptions.get(id);
        assertNotNull(output);
        assertEquals(sinfo2.getOrderNumber(), output.getOrderNumber());
    }

    @Test
    public void testVariadicAddSubscriptionRequiresNonNullId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(sinfo));
    }

    @Test
    public void testVariadicAddSubscriptionRequiresNonEmptyId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(sinfo));
    }

    @Test
    public void testAddDuplicateSubscriptionUsesMostRecent() {
        String id = "sub_id";
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo(id, null);
        doReturn("123").when(sinfo1).getOrderNumber();
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo(id, null);
        doReturn("ABC").when(sinfo1).getOrderNumber();

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(Arrays.asList(sinfo1));
        worker.addSubscriptions(Arrays.asList(sinfo2));

        Map<String, ? extends SubscriptionInfo> subscriptions = worker.getSubscriptions();
        assertNotNull(subscriptions);
        assertEquals(1, subscriptions.size());

        assertThat(subscriptions, hasKey(id));

        SubscriptionInfo output = subscriptions.get(id);
        assertNotNull(output);
        assertEquals(sinfo2.getOrderNumber(), output.getOrderNumber());
    }

    @Test
    public void testAddSubscriptionRequiresNonNullId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(Arrays.asList(sinfo)));
    }

    @Test
    public void testAddSubscriptionRequiresNonEmptyId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(Arrays.asList(sinfo)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testAddSubscriptionAddsNestedObjects(boolean variadic) {
        // Subscription -> Product (x2) -> ProductContent -> Content
        //                              -> Product -> ProductContent -> Content

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductContentInfo pcinfo3 = this.mockProductContentInfo("cid-3", "content-3");
        ProductContentInfo pcinfo4 = this.mockProductContentInfo("cid-4", "content-4");
        ProductContentInfo pcinfo5 = this.mockProductContentInfo("cid-5", "content-5");
        ProductContentInfo pcinfo6 = this.mockProductContentInfo("cid-6", "content-6");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();
        ContentInfo cinfo3 = pcinfo3.getContent();
        ContentInfo cinfo4 = pcinfo4.getContent();
        ContentInfo cinfo5 = pcinfo5.getContent();
        ContentInfo cinfo6 = pcinfo6.getContent();

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");
        ProductInfo pinfo3 = this.mockProductInfo("pid-3", "product-3");
        ProductInfo pinfo4 = this.mockProductInfo("pid-4", "product-4");

        doReturn(Arrays.asList(pcinfo1, pcinfo2)).when(pinfo1).getProductContent();
        doReturn(Arrays.asList(pcinfo3, pcinfo4)).when(pinfo2).getProductContent();
        doReturn(Arrays.asList(pcinfo5)).when(pinfo3).getProductContent();
        doReturn(Arrays.asList(pcinfo6)).when(pinfo4).getProductContent();

        doReturn(Arrays.asList(pinfo3)).when(pinfo1).getProvidedProducts();
        doReturn(Arrays.asList(pinfo4)).when(pinfo2).getProvidedProducts();

        doReturn(pinfo2).when(pinfo1).getDerivedProduct();

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptionMap = worker.getSubscriptions();
        Map<String, ? extends ProductInfo> productMap = worker.getProducts();
        Map<String, ? extends ContentInfo> contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(0, productMap.size());

        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        if (variadic) {
            worker.addSubscriptions(sinfo);
        }
        else {
            worker.addSubscriptions(Arrays.asList(sinfo));
        }

        subscriptionMap = worker.getSubscriptions();
        productMap = worker.getProducts();
        contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(1, subscriptionMap.size());
        assertThat(subscriptionMap, hasEntry(sinfo.getId(), sinfo));

        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        assertThat(productMap, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(productMap, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(productMap, hasEntry(pinfo3.getId(), pinfo3));
        assertThat(productMap, hasEntry(pinfo4.getId(), pinfo4));

        assertNotNull(contentMap);
        assertEquals(6, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
        assertThat(contentMap, hasEntry(cinfo4.getId(), cinfo4));
        assertThat(contentMap, hasEntry(cinfo5.getId(), cinfo5));
        assertThat(contentMap, hasEntry(cinfo6.getId(), cinfo6));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testAddSubscriptionIgnoresNestedNulls(boolean variadic) {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", null);
        assertNull(sinfo.getProduct());

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptionMap = worker.getSubscriptions();
        Map<String, ? extends ProductInfo> productMap = worker.getProducts();
        Map<String, ? extends ContentInfo> contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(0, productMap.size());

        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        if (variadic) {
            worker.addSubscriptions(sinfo);
        }
        else {
            worker.addSubscriptions(Arrays.asList(sinfo));
        }

        subscriptionMap = worker.getSubscriptions();
        productMap = worker.getProducts();
        contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(1, subscriptionMap.size());
        assertThat(subscriptionMap, hasEntry(sinfo.getId(), sinfo));

        assertNotNull(productMap);
        assertEquals(0, productMap.size());

        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());
    }

    @Test
    public void testVariadicAddProducts() {
        ProductInfo pinfo1 = this.mockProductInfo("product-1", null);
        ProductInfo pinfo2 = this.mockProductInfo("product-2", null);
        ProductInfo pinfo3 = this.mockProductInfo("product-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ProductInfo> products = worker.getProducts();
        assertNotNull(products);
        assertEquals(0, products.size());

        ProductInfo[] param = new ProductInfo[] { pinfo1, pinfo2, pinfo3 };
        worker.addProducts(param);

        products = worker.getProducts();
        assertNotNull(products);
        assertEquals(3, products.size());
        assertThat(products, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(products, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(products, hasEntry(pinfo3.getId(), pinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addProducts(param);

        products = worker.getProducts();
        assertNotNull(products);
        assertEquals(3, products.size());
        assertThat(products, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(products, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(products, hasEntry(pinfo3.getId(), pinfo3));
    }

    @Test
    public void testAddProducts() {
        ProductInfo pinfo1 = this.mockProductInfo("product-1", null);
        ProductInfo pinfo2 = this.mockProductInfo("product-2", null);
        ProductInfo pinfo3 = this.mockProductInfo("product-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ProductInfo> products = worker.getProducts();
        assertNotNull(products);
        assertEquals(0, products.size());

        ProductInfo[] param = new ProductInfo[] { pinfo1, pinfo2, pinfo3 };
        worker.addProducts(Arrays.asList(param));

        products = worker.getProducts();
        assertNotNull(products);
        assertEquals(3, products.size());
        assertThat(products, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(products, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(products, hasEntry(pinfo3.getId(), pinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addProducts(Arrays.asList(param));

        products = worker.getProducts();
        assertNotNull(products);
        assertEquals(3, products.size());
        assertThat(products, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(products, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(products, hasEntry(pinfo3.getId(), pinfo3));
    }

    @Test
    public void testVariadicAddDuplicateProductUsesMostRecent() {
        String id = "sub_id";
        ProductInfo pinfo1 = this.mockProductInfo(id, "123");
        ProductInfo pinfo2 = this.mockProductInfo(id, "ABC");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(pinfo1);
        worker.addProducts(pinfo2);

        Map<String, ? extends ProductInfo> products = worker.getProducts();
        assertNotNull(products);
        assertEquals(1, products.size());

        assertThat(products, hasKey(id));

        ProductInfo output = products.get(id);
        assertNotNull(output);
        assertEquals(pinfo2.getName(), output.getName());
    }

    @Test
    public void testVariadicAddProductRequiresNonNullId() {
        ProductInfo pinfo = this.mockProductInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProducts(pinfo));
    }

    @Test
    public void testVariadicAddProductRequiresNonEmptyId() {
        ProductInfo pinfo = this.mockProductInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProducts(pinfo));
    }

    @Test
    public void testAddDuplicateProductUsesMostRecent() {
        String id = "sub_id";
        ProductInfo pinfo1 = this.mockProductInfo(id, "123");
        ProductInfo pinfo2 = this.mockProductInfo(id, "ABC");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(Arrays.asList(pinfo1));
        worker.addProducts(Arrays.asList(pinfo2));

        Map<String, ? extends ProductInfo> products = worker.getProducts();
        assertNotNull(products);
        assertEquals(1, products.size());

        assertThat(products, hasKey(id));

        ProductInfo output = products.get(id);
        assertNotNull(output);
        assertEquals(pinfo2.getName(), output.getName());
    }

    @Test
    public void testAddProductRequiresNonNullId() {
        ProductInfo pinfo = this.mockProductInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProducts(Arrays.asList(pinfo)));
    }

    @Test
    public void testAddProductRequiresNonEmptyId() {
        ProductInfo pinfo = this.mockProductInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProducts(Arrays.asList(pinfo)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testAddProductsAddsNestedObjects(boolean variadic) {
        // Product -> ProductContent -> Content
        //         -> Product -> ProductContent -> Content
        //                    -> Product -> ...

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductContentInfo pcinfo3 = this.mockProductContentInfo("cid-3", "content-3");
        ProductContentInfo pcinfo4 = this.mockProductContentInfo("cid-4", "content-4");
        ProductContentInfo pcinfo5 = this.mockProductContentInfo("cid-5", "content-5");
        ProductContentInfo pcinfo6 = this.mockProductContentInfo("cid-6", "content-6");
        ProductContentInfo pcinfo7 = this.mockProductContentInfo("cid-7", "content-7");
        ProductContentInfo pcinfo8 = this.mockProductContentInfo("cid-8", "content-8");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();
        ContentInfo cinfo3 = pcinfo3.getContent();
        ContentInfo cinfo4 = pcinfo4.getContent();
        ContentInfo cinfo5 = pcinfo5.getContent();
        ContentInfo cinfo6 = pcinfo6.getContent();
        ContentInfo cinfo7 = pcinfo7.getContent();
        ContentInfo cinfo8 = pcinfo8.getContent();

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");
        ProductInfo pinfo3 = this.mockProductInfo("pid-3", "product-3");
        ProductInfo pinfo4 = this.mockProductInfo("pid-4", "product-4");

        doReturn(Arrays.asList(pcinfo1, pcinfo2)).when(pinfo1).getProductContent();
        doReturn(Arrays.asList(pcinfo3, pcinfo4)).when(pinfo2).getProductContent();
        doReturn(Arrays.asList(pcinfo5, pcinfo6)).when(pinfo3).getProductContent();
        doReturn(Arrays.asList(pcinfo7, pcinfo8)).when(pinfo4).getProductContent();

        doReturn(Arrays.asList(pinfo2, pinfo3)).when(pinfo1).getProvidedProducts();
        doReturn(Arrays.asList(pinfo4)).when(pinfo3).getProvidedProducts();

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptionMap = worker.getSubscriptions();
        Map<String, ? extends ProductInfo> productMap = worker.getProducts();
        Map<String, ? extends ContentInfo> contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(0, productMap.size());

        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        if (variadic) {
            worker.addProducts(pinfo1);
        }
        else {
            worker.addProducts(Arrays.asList(pinfo1));
        }

        subscriptionMap = worker.getSubscriptions();
        productMap = worker.getProducts();
        contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        assertThat(productMap, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(productMap, hasEntry(pinfo2.getId(), pinfo2));
        assertThat(productMap, hasEntry(pinfo3.getId(), pinfo3));
        assertThat(productMap, hasEntry(pinfo4.getId(), pinfo4));

        assertNotNull(contentMap);
        assertEquals(8, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
        assertThat(contentMap, hasEntry(cinfo4.getId(), cinfo4));
        assertThat(contentMap, hasEntry(cinfo5.getId(), cinfo5));
        assertThat(contentMap, hasEntry(cinfo6.getId(), cinfo6));
        assertThat(contentMap, hasEntry(cinfo7.getId(), cinfo7));
        assertThat(contentMap, hasEntry(cinfo8.getId(), cinfo8));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    public void testAddProductsIgnoresNestedNulls(boolean variadic) {
        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");

        doReturn(Arrays.asList(null, pcinfo1, null, pcinfo2, null)).when(pinfo1).getProductContent();
        doReturn(Arrays.asList(null, pinfo2, null)).when(pinfo1).getProvidedProducts();

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends SubscriptionInfo> subscriptionMap = worker.getSubscriptions();
        Map<String, ? extends ProductInfo> productMap = worker.getProducts();
        Map<String, ? extends ContentInfo> contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(0, productMap.size());

        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        if (variadic) {
            worker.addProducts(pinfo1);
        }
        else {
            worker.addProducts(Arrays.asList(pinfo1));
        }

        subscriptionMap = worker.getSubscriptions();
        productMap = worker.getProducts();
        contentMap = worker.getContent();

        assertNotNull(subscriptionMap);
        assertEquals(0, subscriptionMap.size());

        assertNotNull(productMap);
        assertEquals(2, productMap.size());
        assertThat(productMap, hasEntry(pinfo1.getId(), pinfo1));
        assertThat(productMap, hasEntry(pinfo2.getId(), pinfo2));

        assertNotNull(contentMap);
        assertEquals(2, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
    }

    @Test
    public void testVariadicAddContent() {
        ContentInfo cinfo1 = this.mockContentInfo("content-1", null);
        ContentInfo cinfo2 = this.mockContentInfo("content-2", null);
        ContentInfo cinfo3 = this.mockContentInfo("content-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        ContentInfo[] param = new ContentInfo[] { cinfo1, cinfo2, cinfo3 };
        worker.addContent(param);

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addContent(param);

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
    }

    @Test
    public void testAddContent() {
        ContentInfo cinfo1 = this.mockContentInfo("content-1", null);
        ContentInfo cinfo2 = this.mockContentInfo("content-2", null);
        ContentInfo cinfo3 = this.mockContentInfo("content-3", null);

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        ContentInfo[] param = new ContentInfo[] { cinfo1, cinfo2, cinfo3 };
        worker.addContent(Arrays.asList(param));

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addContent(Arrays.asList(param));

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
    }

    @Test
    public void testVariadicAddDuplicateContentUsesMostRecent() {
        String id = "sub_id";
        ContentInfo cinfo1 = this.mockContentInfo(id, "123");
        ContentInfo cinfo2 = this.mockContentInfo(id, "ABC");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addContent(cinfo1);
        worker.addContent(cinfo2);

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());

        assertThat(contentMap, hasKey(id));

        ContentInfo output = contentMap.get(id);
        assertNotNull(output);
        assertEquals(cinfo2.getName(), output.getName());
    }

    @Test
    public void testVariadicAddContentRequiresNonNullId() {
        ContentInfo cinfo = this.mockContentInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addContent(cinfo));
    }

    @Test
    public void testVariadicAddContentRequiresNonEmptyId() {
        ContentInfo cinfo = this.mockContentInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addContent(cinfo));
    }

    @Test
    public void testAddDuplicateContentUsesMostRecent() {
        String id = "sub_id";
        ContentInfo cinfo1 = this.mockContentInfo(id, "123");
        ContentInfo cinfo2 = this.mockContentInfo(id, "ABC");

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addContent(Arrays.asList(cinfo1));
        worker.addContent(Arrays.asList(cinfo2));

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());

        assertThat(contentMap, hasKey(id));

        ContentInfo output = contentMap.get(id);
        assertNotNull(output);
        assertEquals(cinfo2.getName(), output.getName());
    }

    @Test
    public void testAddContentRequiresNonNullId() {
        ContentInfo cinfo = this.mockContentInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addContent(Arrays.asList(cinfo)));
    }

    @Test
    public void testAddContentRequiresNonEmptyId() {
        ContentInfo cinfo = this.mockContentInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addContent(Arrays.asList(cinfo)));
    }

    @Test
    public void testExecuteIncludesImportedEntities() {
        Owner owner = new Owner();

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductContentInfo pcinfo3 = this.mockProductContentInfo("cid-3", "content-3");
        ProductContentInfo pcinfo4 = this.mockProductContentInfo("cid-4", "content-4");
        ProductContentInfo pcinfo5 = this.mockProductContentInfo("cid-5", "content-5");
        ProductContentInfo pcinfo6 = this.mockProductContentInfo("cid-6", "content-6");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();
        ContentInfo cinfo3 = pcinfo3.getContent();
        ContentInfo cinfo4 = pcinfo4.getContent();
        ContentInfo cinfo5 = pcinfo5.getContent();
        ContentInfo cinfo6 = pcinfo6.getContent();

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");
        ProductInfo pinfo3 = this.mockProductInfo("pid-3", "product-3");
        ProductInfo pinfo4 = this.mockProductInfo("pid-4", "product-4");

        doReturn(Arrays.asList(pcinfo1, pcinfo2)).when(pinfo1).getProductContent();
        doReturn(Arrays.asList(pcinfo3, pcinfo4)).when(pinfo2).getProductContent();
        doReturn(Arrays.asList(pcinfo5)).when(pinfo3).getProductContent();
        doReturn(Arrays.asList(pcinfo6)).when(pinfo4).getProductContent();

        doReturn(Arrays.asList(pinfo3)).when(pinfo1).getProvidedProducts();
        doReturn(Arrays.asList(pinfo4)).when(pinfo2).getProvidedProducts();

        doReturn(pinfo2).when(pinfo1).getDerivedProduct();

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo);

        // Add some empty collections for our existing entities
        this.mockProductLookup(List.of());
        this.mockContentLookup(List.of());

        RefreshResult result = worker.execute(owner);

        assertNotNull(result);

        // At the time of writing, pools should always be empty since they're still processed
        // outside of the worker framework
        assertNotNull(result.getEntities(Pool.class));
        assertEquals(0, result.getEntities(Pool.class).size());

        Map<String, Product> productMap = result.getEntities(Product.class);
        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        for (ProductInfo pinfo : Arrays.asList(pinfo1, pinfo2, pinfo3, pinfo4)) {
            assertThat(productMap, hasKey(pinfo.getId()));
            assertNotNull(productMap.get(pinfo.getId()));
            assertEquals(pinfo.getId(), productMap.get(pinfo.getId()).getId());
        }

        Map<String, Content> contentMap = result.getEntities(Content.class);
        assertNotNull(contentMap);
        assertEquals(6, contentMap.size());
        for (ContentInfo cinfo : Arrays.asList(cinfo1, cinfo2, cinfo3, cinfo4, cinfo5, cinfo6)) {
            assertThat(contentMap, hasKey(cinfo.getId()));
            assertNotNull(contentMap.get(cinfo.getId()));
            assertEquals(cinfo.getId(), contentMap.get(cinfo.getId()).getId());
        }
    }

    // TODO: Fix these two tests, new logic only includes existing items that might be updated by
    // one or more of the subscriptions referenced

    // @Test
    // public void testExecuteIncludesExistingEntities() {
    //     Owner owner = new Owner();

    //     // At the time of writing, pools aren't part of the refresh worker framework, and existing
    //     // pools are not fetched and processed by it. As such, we need not worry about mocking
    //     // pools here or verifying that existing ones are output.
    //     Content content1 = new Content("cid-1");
    //     Content content2 = new Content("cid-2");
    //     Content content3 = new Content("cid-3");

    //     Product product1 = new Product("pid-1", "product-1");
    //     Product product2 = new Product("pid-2", "product-2");
    //     Product product3 = new Product("pid-3", "product-3");

    //     Pool pool1 = new Pool()
    //         .setId("pool1")
    //         .setProduct(product1);
    //     Pool pool2 = new Pool()
    //         .setId("pool2")
    //         .setProduct(product2);
    //     Pool pool3 = new Pool()
    //         .setId("pool3")
    //         .setProduct(product3);

    //     this.mockProductLookup(List.of(product1, product2, product3));
    //     this.mockContentLookup(List.of(content1, content2, content3));

    //     doReturn(Arrays.asList(pool1, pool2, pool3))
    //         .when(this.mockPoolCurator)
    //         .listByOwnerAndTypes(eq(owner.getId()), any(PoolType[].class));

    //     RefreshWorker worker = this.buildRefreshWorker();
    //     RefreshResult result = worker.execute(owner);

    //     assertNotNull(result);
    //     assertNotNull(result.getEntities(Pool.class));
    //     assertEquals(0, result.getEntities(Pool.class).size());

    //     Map<String, Product> productMap = result.getEntities(Product.class);
    //     assertNotNull(productMap);
    //     assertEquals(3, productMap.size());
    //     assertThat(productMap, hasEntry(product1.getId(), product1));
    //     assertThat(productMap, hasEntry(product2.getId(), product2));
    //     assertThat(productMap, hasEntry(product3.getId(), product3));

    //     Map<String, Content> contentMap = result.getEntities(Content.class);
    //     assertNotNull(contentMap);
    //     assertEquals(3, contentMap.size());
    //     assertThat(contentMap, hasEntry(content1.getId(), content1));
    //     assertThat(contentMap, hasEntry(content2.getId(), content2));
    //     assertThat(contentMap, hasEntry(content3.getId(), content3));
    // }

    // TODO: This test also needs fixing inline with the above test
    @Test
    public void testExecuteMergesExistingAndImportedEntities() {
        Owner owner = new Owner();

        ContentInfo cinfo1 = this.mockContentInfo("cid-1", "content-1");
        ContentInfo cinfo2 = this.mockContentInfo("cid-2", "content-2");
        ContentInfo cinfo3 = this.mockContentInfo("cid-3a", "imported_content");
        Content content1 = new Content("cid-1")
            .setName("content-1");
        Content content2 = new Content("cid-2")
            .setName("content-2");
        Content content3 = new Content("cid-3b")
            .setName("existing_content");

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");
        ProductInfo pinfo3 = this.mockProductInfo("pid-3a", "imported_product");
        Product product1 = new Product("pid-1", "product-1");
        Product product2 = new Product("pid-2", "product-2");
        Product product3 = new Product("pid-3b", "existing_product");

        Pool pool1 = new Pool()
            .setId("pool1")
            .setProduct(product1);
        Pool pool2 = new Pool()
            .setId("pool2")
            .setProduct(product2);
        Pool pool3 = new Pool()
            .setId("pool3")
            .setProduct(product3);

        doReturn(Arrays.asList(pool1, pool2, pool3))
            .when(this.mockPoolCurator)
            .listByOwnerAndTypes(eq(owner.getId()), any(PoolType[].class));

        this.mockProductLookup(List.of(product1, product2, product3));
        this.mockContentLookup(List.of(content1, content2, content3));

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(pinfo1, pinfo2, pinfo3);
        worker.addContent(cinfo1, cinfo2, cinfo3);

        RefreshResult result = worker.execute(owner);

        assertNotNull(result);

        // See note above as to why this is empty in this test
        assertNotNull(result.getEntities(Pool.class));
        assertEquals(0, result.getEntities(Pool.class).size());

        Map<String, Product> productMap = result.getEntities(Product.class);
        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        for (ProductInfo pinfo : Arrays.asList(product1, product2, product3, pinfo1, pinfo2, pinfo3)) {
            assertThat(productMap, hasKey(pinfo.getId()));
            assertNotNull(productMap.get(pinfo.getId()));
            assertEquals(pinfo.getName(), productMap.get(pinfo.getId()).getName());
        }

        Map<String, Content> contentMap = result.getEntities(Content.class);
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        for (ContentInfo cinfo : Arrays.asList(content1, content2, cinfo1, cinfo2, cinfo3)) {
            assertThat(contentMap, hasKey(cinfo.getId()));
            assertNotNull(contentMap.get(cinfo.getId()));
            assertEquals(cinfo.getName(), contentMap.get(cinfo.getId()).getName());
        }
    }

    @Test
    public void testExecuteAlwaysReturnsRefreshResult() {
        Owner owner = new Owner();

        this.mockProductLookup(List.of());
        this.mockContentLookup(List.of());

        RefreshWorker worker = this.buildRefreshWorker();

        // Even with no data, we should still get an empty result set
        RefreshResult result = worker.execute(owner);

        assertNotNull(result);
        assertNotNull(result.getEntities(Pool.class));
        assertEquals(0, result.getEntities(Pool.class).size());
        assertNotNull(result.getEntities(Product.class));
        assertEquals(0, result.getEntities(Product.class).size());
        assertNotNull(result.getEntities(Content.class));
        assertEquals(0, result.getEntities(Content.class).size());
    }

    @Test
    public void testExecuteRetriesOnConstraintViolation() {
        Owner owner = new Owner();

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");

        doReturn(List.of(pcinfo1, pcinfo2)).when(pinfo2).getProductContent();
        doReturn(List.of(pinfo2)).when(pinfo1).getProvidedProducts();

        this.mockProductLookup(List.of());
        this.mockContentLookup(List.of());

        // Throw the constraint violation exception on the first invocation, triggering
        // a retry
        Exception exception = this.buildConstraintViolationException();

        doThrow(exception).doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .create(Mockito.any(Product.class), anyBoolean());

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo);

        RefreshResult result = worker.execute(owner);
        assertNotNull(result);

        verify(mockProductCurator, times(2)).getProductsByIds(eq(null), any(Collection.class));
        verify(mockProductCurator, times(3)).create(Mockito.any(Product.class), anyBoolean());
    }

    @Test
    public void testExecuteRetriesOnDatabaseDeadlock() {
        Owner owner = new Owner();

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");

        doReturn(List.of(pcinfo1, pcinfo2)).when(pinfo2).getProductContent();
        doReturn(List.of(pinfo2)).when(pinfo1).getProvidedProducts();

        this.mockProductLookup(List.of());
        this.mockContentLookup(List.of());

        // Throw the constraint violation exception on the first invocation, triggering
        // a retry
        Exception exception = this.buildDatabaseDeadlockException();

        doThrow(exception).doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .create(Mockito.any(Product.class), anyBoolean());

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo);

        RefreshResult result = worker.execute(owner);
        assertNotNull(result);

        verify(mockProductCurator, times(2)).getProductsByIds(eq(null), any(Collection.class));
        verify(mockProductCurator, times(3)).create(Mockito.any(Product.class), anyBoolean());
    }

    @Test
    public void testExecuteSkipsRetriesWhenTransactionAlreadyExists() {
        Owner owner = new Owner();

        ProductContentInfo pcinfo1 = this.mockProductContentInfo("cid-1", "content-1");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("cid-2", "content-2");
        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");

        doReturn(List.of(pcinfo1, pcinfo2)).when(pinfo2).getProductContent();
        doReturn(List.of(pinfo2)).when(pinfo1).getProvidedProducts();

        this.mockProductLookup(List.of());
        this.mockContentLookup(List.of());

        // Throw the constraint violation exception on the first invocation, triggering
        // a retry
        Exception exception = this.buildConstraintViolationException();

        doThrow(exception).doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .create(any(Product.class), anyBoolean());

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo);

        // Impl note: this only works because of the call to mockTransactionalFunctionality done in setup
        this.mockEntityManager.getTransaction().begin();
        assertThrows(TransactionExecutionException.class, () -> worker.execute(owner));

        verify(mockProductCurator, times(1)).getProductsByIds(eq(null), any(Collection.class));
        verify(mockProductCurator, times(1)).create(Mockito.any(Product.class), anyBoolean());
    }

}

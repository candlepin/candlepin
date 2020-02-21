/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContent;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the RefreshWorker class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefreshWorkerTest {

    private ProductCurator mockProductCurator;
    private OwnerProductCurator mockOwnerProductCurator;
    private ContentCurator mockContentCurator;
    private OwnerContentCurator mockOwnerContentCurator;

    @BeforeEach
    private void init() {
        this.mockProductCurator = mock(ProductCurator.class);
        this.mockOwnerProductCurator = mock(OwnerProductCurator.class);
        this.mockContentCurator = mock(ContentCurator.class);
        this.mockOwnerContentCurator = mock(OwnerContentCurator.class);

        doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .saveOrUpdate(Mockito.any(Product.class));

        doAnswer(returnsFirstArg())
            .when(this.mockOwnerProductCurator)
            .saveOrUpdate(Mockito.any(OwnerProduct.class));

        doAnswer(returnsFirstArg())
            .when(this.mockContentCurator)
            .saveOrUpdate(Mockito.any(Content.class));

        doAnswer(returnsFirstArg())
            .when(this.mockOwnerContentCurator)
            .saveOrUpdate(Mockito.any(OwnerContent.class));

        doReturn(Collections.emptyMap())
            .when(this.mockOwnerContentCurator)
            .getVersionedContentById(Mockito.any(Owner.class), anyCollection());

        doReturn(Collections.emptyMap())
            .when(this.mockOwnerProductCurator)
            .getVersionedProductsById(Mockito.any(Owner.class), anyCollection());
    }

    private RefreshWorker buildRefreshWorker() {
        return new RefreshWorker(this.mockProductCurator, this.mockOwnerProductCurator,
            this.mockContentCurator, this.mockOwnerContentCurator);
    }

    private SubscriptionInfo mockSubscriptionInfo(String id, ProductInfo pinfo, ProductInfo dpinfo) {
        SubscriptionInfo sinfo = mock(SubscriptionInfo.class);
        doReturn(id).when(sinfo).getId();
        doReturn(pinfo).when(sinfo).getProduct();
        doReturn(dpinfo).when(sinfo).getDerivedProduct();

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

    private CandlepinQuery mockCandlepinQuery(List elements) {
        if (elements == null) {
            elements = Collections.emptyList();
        }

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(elements.iterator());
        when(cqmock.list()).thenReturn(elements);

        return cqmock;
    }

    @Test
    public void testVariadicAddSubscriptions() {
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo("sub-1", null, null);
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo("sub-2", null, null);
        SubscriptionInfo sinfo3 = this.mockSubscriptionInfo("sub-3", null, null);

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
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo("sub-1", null, null);
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo("sub-2", null, null);
        SubscriptionInfo sinfo3 = this.mockSubscriptionInfo("sub-3", null, null);

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
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo(id, null, null);
        doReturn("123").when(sinfo1).getOrderNumber();
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo(id, null, null);
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
        SubscriptionInfo sinfo = this.mockSubscriptionInfo(null, null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(sinfo));
    }

    @Test
    public void testVariadicAddSubscriptionRequiresNonEmptyId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("", null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(sinfo));
    }

    @Test
    public void testAddDuplicateSubscriptionUsesMostRecent() {
        String id = "sub_id";
        SubscriptionInfo sinfo1 = this.mockSubscriptionInfo(id, null, null);
        doReturn("123").when(sinfo1).getOrderNumber();
        SubscriptionInfo sinfo2 = this.mockSubscriptionInfo(id, null, null);
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
        SubscriptionInfo sinfo = this.mockSubscriptionInfo(null, null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addSubscriptions(Arrays.asList(sinfo)));
    }

    @Test
    public void testAddSubscriptionRequiresNonEmptyId() {
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("", null, null);
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

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1, pinfo2);

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
        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", null, null);
        assertNull(sinfo.getProduct());
        assertNull(sinfo.getDerivedProduct());

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
    public void testVariadicAddProductContent() {
        ProductContentInfo pcinfo1 = this.mockProductContentInfo("content-1", null);
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("content-2", null);
        ProductContentInfo pcinfo3 = this.mockProductContentInfo("content-3", null);
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();
        ContentInfo cinfo3 = pcinfo3.getContent();

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        ProductContentInfo[] param = new ProductContentInfo[] { pcinfo1, pcinfo2, pcinfo3 };
        worker.addProductContent(param);

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addProductContent(param);

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
    }

    @Test
    public void testAddProductContent() {
        ProductContentInfo pcinfo1 = this.mockProductContentInfo("content-1", null);
        ProductContentInfo pcinfo2 = this.mockProductContentInfo("content-2", null);
        ProductContentInfo pcinfo3 = this.mockProductContentInfo("content-3", null);
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();
        ContentInfo cinfo3 = pcinfo3.getContent();

        RefreshWorker worker = this.buildRefreshWorker();

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        ProductContentInfo[] param = new ProductContentInfo[] { pcinfo1, pcinfo2, pcinfo3 };
        worker.addProductContent(Arrays.asList(param));

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));

        // Verify repeated additions do not trigger additional entries
        worker.addProductContent(Arrays.asList(param));

        contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(cinfo1.getId(), cinfo1));
        assertThat(contentMap, hasEntry(cinfo2.getId(), cinfo2));
        assertThat(contentMap, hasEntry(cinfo3.getId(), cinfo3));
    }

    @Test
    public void testVariadicAddDuplicateProductContentUsesMostRecent() {
        String id = "sub_id";
        ProductContentInfo pcinfo1 = this.mockProductContentInfo(id, "123");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo(id, "ABC");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProductContent(pcinfo1);
        worker.addProductContent(pcinfo2);

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());

        assertThat(contentMap, hasKey(id));

        ContentInfo output = contentMap.get(id);
        assertNotNull(output);
        assertEquals(cinfo2.getName(), output.getName());
    }

    @Test
    public void testVariadicAddProductContentRequiresNonNullContent() {
        ProductContentInfo pcinfo = mock(ProductContentInfo.class);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(pcinfo));
    }

    @Test
    public void testVariadicAddProductContentRequiresNonNullId() {
        ProductContentInfo pcinfo = this.mockProductContentInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(pcinfo));
    }

    @Test
    public void testVariadicAddProductContentRequiresNonEmptyId() {
        ProductContentInfo pcinfo = this.mockProductContentInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(pcinfo));
    }

    @Test
    public void testAddDuplicateProductContentUsesMostRecent() {
        String id = "sub_id";
        ProductContentInfo pcinfo1 = this.mockProductContentInfo(id, "123");
        ProductContentInfo pcinfo2 = this.mockProductContentInfo(id, "ABC");
        ContentInfo cinfo1 = pcinfo1.getContent();
        ContentInfo cinfo2 = pcinfo2.getContent();

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProductContent(Arrays.asList(pcinfo1));
        worker.addProductContent(Arrays.asList(pcinfo2));

        Map<String, ? extends ContentInfo> contentMap = worker.getContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());

        assertThat(contentMap, hasKey(id));

        ContentInfo output = contentMap.get(id);
        assertNotNull(output);
        assertEquals(cinfo2.getName(), output.getName());
    }

    @Test
    public void testAddProductContentRequiresNonNullContent() {
        ProductContentInfo pcinfo = mock(ProductContentInfo.class);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(Arrays.asList(pcinfo)));
    }

    @Test
    public void testAddProductContentRequiresNonNullId() {
        ProductContentInfo pcinfo = this.mockProductContentInfo(null, null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(Arrays.asList(pcinfo)));
    }

    @Test
    public void testAddProductContentRequiresNonEmptyId() {
        ProductContentInfo pcinfo = this.mockProductContentInfo("", null);
        RefreshWorker worker = this.buildRefreshWorker();

        assertThrows(IllegalArgumentException.class, () -> worker.addProductContent(Arrays.asList(pcinfo)));
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

        SubscriptionInfo sinfo = this.mockSubscriptionInfo("sub", pinfo1, pinfo2);

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addSubscriptions(sinfo);

        // Add some empty collections for our existing entities
        doReturn(this.mockCandlepinQuery(null))
            .when(this.mockOwnerProductCurator)
            .getProductsByOwner(eq(owner));

        doReturn(Collections.emptyMap())
            .when(this.mockOwnerProductCurator)
            .getVersionedProductsById(eq(owner), anyCollection());

        doReturn(this.mockCandlepinQuery(null))
            .when(this.mockOwnerContentCurator)
            .getContentByOwner(eq(owner));

        doReturn(Collections.emptyMap())
            .when(this.mockOwnerContentCurator)
            .getVersionedContentById(eq(owner), anyCollection());


        RefreshResult result = worker.execute(owner);

        assertNotNull(result);

        // At the time of writing, pools should always be empty since they're still processed
        // outside of the worker framework
        assertNotNull(result.getProcessedPools());
        assertEquals(0, result.getProcessedPools().size());

        Map<String, Product> productMap = result.getProcessedProducts();
        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        for (ProductInfo pinfo : Arrays.asList(pinfo1, pinfo2, pinfo3, pinfo4)) {
            assertThat(productMap, hasKey(pinfo.getId()));
            assertNotNull(productMap.get(pinfo.getId()));
            assertEquals(pinfo.getId(), productMap.get(pinfo.getId()).getId());
        }

        Map<String, Content> contentMap = result.getProcessedContent();
        assertNotNull(contentMap);
        assertEquals(6, contentMap.size());
        for (ContentInfo cinfo : Arrays.asList(cinfo1, cinfo2, cinfo3, cinfo4, cinfo5, cinfo6)) {
            assertThat(contentMap, hasKey(cinfo.getId()));
            assertNotNull(contentMap.get(cinfo.getId()));
            assertEquals(cinfo.getId(), contentMap.get(cinfo.getId()).getId());
        }
    }

    @Test
    public void testExecuteIncludesExistingEntities() {
        Owner owner = new Owner();

        // At the time of writing, pools aren't part of the refresh worker framework, and existing
        // pools are not fetched and processed by it. As such, we need not worry about mocking
        // pools here or verifying that existing ones are output.
        Product product1 = new Product("pid-1", "product-1");
        Product product2 = new Product("pid-2", "product-2");
        Product product3 = new Product("pid-3", "product-3");

        Content content1 = new Content("cid-1");
        Content content2 = new Content("cid-2");
        Content content3 = new Content("cid-3");

        CandlepinQuery cqmock1 = this.mockCandlepinQuery(Arrays.asList(product1, product2, product3));
        CandlepinQuery cqmock2 = this.mockCandlepinQuery(Arrays.asList(content1, content2, content3));

        doReturn(cqmock1).when(this.mockOwnerProductCurator).getProductsByOwner(eq(owner));
        doReturn(cqmock2).when(this.mockOwnerContentCurator).getContentByOwner(eq(owner));

        RefreshWorker worker = this.buildRefreshWorker();
        RefreshResult result = worker.execute(owner);

        assertNotNull(result);

        // See note above as to why this is empty in this test
        assertNotNull(result.getProcessedPools());
        assertEquals(0, result.getProcessedPools().size());

        Map<String, Product> productMap = result.getProcessedProducts();
        assertNotNull(productMap);
        assertEquals(3, productMap.size());
        assertThat(productMap, hasEntry(product1.getId(), product1));
        assertThat(productMap, hasEntry(product2.getId(), product2));
        assertThat(productMap, hasEntry(product3.getId(), product3));

        Map<String, Content> contentMap = result.getProcessedContent();
        assertNotNull(contentMap);
        assertEquals(3, contentMap.size());
        assertThat(contentMap, hasEntry(content1.getId(), content1));
        assertThat(contentMap, hasEntry(content2.getId(), content2));
        assertThat(contentMap, hasEntry(content3.getId(), content3));
    }

    @Test
    public void testExecuteMergesExistingAndImportedEntities() {
        Owner owner = new Owner();

        ProductInfo pinfo1 = this.mockProductInfo("pid-1", "product-1");
        ProductInfo pinfo2 = this.mockProductInfo("pid-2", "product-2");
        ProductInfo pinfo3 = this.mockProductInfo("pid-3a", "imported_product");
        Product product1 = new Product("pid-1", "product-1");
        Product product2 = new Product("pid-2", "product-2");
        Product product3 = new Product("pid-3b", "existing_product");

        ContentInfo cinfo1 = this.mockContentInfo("cid-1", "content-1");
        ContentInfo cinfo2 = this.mockContentInfo("cid-2", "content-2");
        ContentInfo cinfo3 = this.mockContentInfo("cid-3a", "imported_content");
        Content content1 = new Content("cid-1");
        content1.setName("content-1");
        Content content2 = new Content("cid-2");
        content2.setName("content-2");
        Content content3 = new Content("cid-3b");
        content3.setName("existing_content");

        CandlepinQuery cqmock1 = this.mockCandlepinQuery(Arrays.asList(product1, product2, product3));
        CandlepinQuery cqmock2 = this.mockCandlepinQuery(Arrays.asList(content1, content2, content3));

        doReturn(cqmock1).when(this.mockOwnerProductCurator).getProductsByOwner(eq(owner));
        doReturn(cqmock2).when(this.mockOwnerContentCurator).getContentByOwner(eq(owner));

        RefreshWorker worker = this.buildRefreshWorker();
        worker.addProducts(pinfo1, pinfo2, pinfo3);
        worker.addContent(cinfo1, cinfo2, cinfo3);

        RefreshResult result = worker.execute(owner);

        assertNotNull(result);

        // See note above as to why this is empty in this test
        assertNotNull(result.getProcessedPools());
        assertEquals(0, result.getProcessedPools().size());

        Map<String, Product> productMap = result.getProcessedProducts();
        assertNotNull(productMap);
        assertEquals(4, productMap.size());
        for (ProductInfo pinfo : Arrays.asList(product1, product2, product3, pinfo1, pinfo2, pinfo3)) {
            assertThat(productMap, hasKey(pinfo.getId()));
            assertNotNull(productMap.get(pinfo.getId()));
            assertEquals(pinfo.getName(), productMap.get(pinfo.getId()).getName());
        }

        Map<String, Content> contentMap = result.getProcessedContent();
        assertNotNull(contentMap);
        assertEquals(4, contentMap.size());
        for (ContentInfo cinfo : Arrays.asList(content1, content2, content3, cinfo1, cinfo2, cinfo3)) {
            assertThat(contentMap, hasKey(cinfo.getId()));
            assertNotNull(contentMap.get(cinfo.getId()));
            assertEquals(cinfo.getName(), contentMap.get(cinfo.getId()).getName());
        }
    }

    @Test
    public void testExecuteAlwaysReturnsRefreshResult() {
        Owner owner = new Owner();

        doReturn(this.mockCandlepinQuery(null))
            .when(this.mockOwnerProductCurator)
            .getProductsByOwner(eq(owner));

        doReturn(this.mockCandlepinQuery(null))
            .when(this.mockOwnerContentCurator)
            .getContentByOwner(eq(owner));

        RefreshWorker worker = this.buildRefreshWorker();

        // Even with no data, we should still get an empty result set
        RefreshResult result = worker.execute(owner);

        assertNotNull(result);
        assertNotNull(result.getProcessedPools());
        assertEquals(0, result.getProcessedPools().size());
        assertNotNull(result.getProcessedProducts());
        assertEquals(0, result.getProcessedProducts().size());
        assertNotNull(result.getProcessedContent());
        assertEquals(0, result.getProcessedContent().size());
    }



}

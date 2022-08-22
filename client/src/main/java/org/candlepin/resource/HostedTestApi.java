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

package org.candlepin.resource;

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.ApiResponse;
import org.candlepin.Configuration;
import org.candlepin.Pair;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.SubscriptionDTO;

import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HostedTestApi {
    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String PRODUCT_ID = "product_id";
    private static final String CONTENT_ID = "content_id";
    private static final String SUBSCRIPTION_ID = "subscription_id";
    private static final String CREATE_CHILDREN_PARAM = "create_children";

    private ApiClient localVarApiClient;
    private int localHostIndex;
    private String localCustomBaseUrl;

    public HostedTestApi() {
        this(Configuration.getDefaultApiClient());
    }

    public HostedTestApi(ApiClient apiClient) {
        this.localVarApiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return localVarApiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.localVarApiClient = apiClient;
    }

    public int getHostIndex() {
        return localHostIndex;
    }

    public void setHostIndex(int hostIndex) {
        this.localHostIndex = hostIndex;
    }

    public String getCustomBaseUrl() {
        return localCustomBaseUrl;
    }

    public void setCustomBaseUrl(String customBaseUrl) {
        this.localCustomBaseUrl = customBaseUrl;
    }

    public Boolean isAlive() throws ApiException {
        okhttp3.Call localVarCall = isAliveCall();
        Type localVarReturnType = new TypeToken<String>() {}.getType();
        ApiResponse<String> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return Boolean.valueOf(localVarResp.getData());
    }

    public okhttp3.Call isAliveCall() throws ApiException {
        // Operation Servers
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String path = "/hostedtest/alive";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(CONTENT_TYPE_HEADER, TEXT_PLAIN);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, path, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public void clearData() throws ApiException {
        okhttp3.Call localVarCall = clearDataCall();
        localVarApiClient.execute(localVarCall);
    }

    public okhttp3.Call clearDataCall() throws ApiException {
        // Operation Servers
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = "/hostedtest";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public OwnerDTO createOwner(OwnerDTO owner) throws ApiException {
        okhttp3.Call localVarCall = createOwnerCall(owner);
        Type localVarReturnType = new TypeToken<OwnerDTO>() {}.getType();
        ApiResponse<OwnerDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call createOwnerCall(OwnerDTO owner) throws ApiException {
        String basePath = getBasePath();

        Object body = owner;

        // create path and map variables
        String localVarPath = "/hostedtest/owners";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }


    public SubscriptionDTO createSubscription(SubscriptionDTO subscription) throws ApiException {
        return createSubscription(subscription, false);
    }

    public SubscriptionDTO createSubscription(SubscriptionDTO subscription, boolean createChildren) throws ApiException {
        okhttp3.Call localVarCall = createSubscriptionCall(subscription, createChildren);
        Type localVarReturnType = new TypeToken<SubscriptionDTO>() {}.getType();
        ApiResponse<SubscriptionDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call createSubscriptionCall(SubscriptionDTO subscription, boolean createChildren) throws ApiException {
        String basePath = getBasePath();

        Object body = subscription;

        // create path and map variables
        String localVarPath = "/hostedtest/subscriptions";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        if (createChildren) {
            queryParams.add(new Pair(CREATE_CHILDREN_PARAM, "true"));
        }

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }


    public List<SubscriptionDTO> listSubscriptions() throws ApiException {
        okhttp3.Call localVarCall = listSubscriptionsCall();
        Type localVarReturnType = new TypeToken<List<SubscriptionDTO>>() {}.getType();
        ApiResponse<List<SubscriptionDTO>> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call listSubscriptionsCall() throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = "/hostedtest/subscriptions";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public SubscriptionDTO getSubscription(String subscriptionId) throws ApiException {
        okhttp3.Call localVarCall = getSubscriptionCall(subscriptionId);
        Type localVarReturnType = new TypeToken<SubscriptionDTO>() {}.getType();
        ApiResponse<SubscriptionDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call getSubscriptionCall(String subscriptionId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/subscriptions/{subscription_id}",
            new Pair(SUBSCRIPTION_ID, subscriptionId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public SubscriptionDTO updateSubscription(String subscriptionId, SubscriptionDTO subscription) throws ApiException {
        return updateSubscription(subscriptionId, subscription, false);
    }

    public SubscriptionDTO updateSubscription(String subscriptionId, SubscriptionDTO subscription, Boolean createChildren) throws ApiException {
        okhttp3.Call localVarCall = updateSubscriptionCall(subscriptionId, subscription, createChildren);
        Type localVarReturnType = new TypeToken<SubscriptionDTO>() {}.getType();
        ApiResponse<SubscriptionDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call updateSubscriptionCall(String subscriptionId, SubscriptionDTO subscription, boolean createChildren) throws ApiException {
        String basePath = getBasePath();

        Object body = subscription;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/subscriptions/{subscription_id}",
            new Pair(SUBSCRIPTION_ID, subscriptionId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        if (createChildren) {
            queryParams.add(new Pair("create_children", "true"));
        }

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "PUT", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public void deleteSubscription(String subscriptionId) throws ApiException {
        okhttp3.Call localVarCall = deleteSubscriptionCall(subscriptionId);
        localVarApiClient.execute(localVarCall);
    }

    public okhttp3.Call deleteSubscriptionCall(String subscriptionId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/subscriptions/{subscription_id}",
            new Pair(SUBSCRIPTION_ID, subscriptionId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public List<ProductDTO> listProducts() throws ApiException {
        okhttp3.Call localVarCall = listProductsCall();
        Type localVarReturnType = new TypeToken<List<ProductDTO>>() {}.getType();
        ApiResponse<List<ProductDTO>> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call listProductsCall() throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = "/hostedtest/products";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO getProduct(String productId) throws ApiException {
        okhttp3.Call localVarCall = getProductCall(productId);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call getProductCall(String productId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}", new Pair(PRODUCT_ID, productId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO createProduct(ProductDTO product) throws ApiException {
        return createProduct(product, false);
    }

    public ProductDTO createProduct(ProductDTO product, boolean createChildren) throws ApiException {
        okhttp3.Call localVarCall = createProductCall(product, createChildren);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call createProductCall(ProductDTO product, boolean createChildren) throws ApiException {
        String basePath = getBasePath();

        Object body = product;

        // create path and map variables
        String localVarPath = "/hostedtest/products";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();
        if (createChildren) {
            queryParams.add(new Pair("create_children", "true"));
        }

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO updateProduct(String productId, ProductDTO product) throws ApiException {
        return updateProduct(productId, product, false);
    }

    public ProductDTO updateProduct(String productId, ProductDTO product, boolean createChildren) throws ApiException {
        okhttp3.Call localVarCall = updateProductCall(productId, product, createChildren);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call updateProductCall(String productId, ProductDTO product, boolean createChildren) throws ApiException {
        String basePath = getBasePath();

        Object body = product;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}", new Pair(PRODUCT_ID, productId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();
        if (createChildren) {
            queryParams.add(new Pair("create_children", "true"));
        }

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "PUT", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public void deleteProduct(String productId) throws ApiException {
        okhttp3.Call localVarCall = deleteProductCall(productId);
        localVarApiClient.execute(localVarCall);
    }

    public okhttp3.Call deleteProductCall(String productId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}", new Pair(PRODUCT_ID, productId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public List<ContentDTO> listContent() throws ApiException {
        okhttp3.Call localVarCall = listContentCall();
        Type localVarReturnType = new TypeToken<List<ContentDTO>>() {}.getType();
        ApiResponse<List<ContentDTO>> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call listContentCall() throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = "/hostedtest/content";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ContentDTO getContent(String contentId) throws ApiException {
        okhttp3.Call localVarCall = getContentCall(contentId);
        Type localVarReturnType = new TypeToken<ContentDTO>() {}.getType();
        ApiResponse<ContentDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call getContentCall(String contentId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/content/{content_id}", new Pair(CONTENT_ID, contentId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "GET", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ContentDTO createContent(ContentDTO content) throws ApiException {
        okhttp3.Call localVarCall = createContentCall(content);
        Type localVarReturnType = new TypeToken<ContentDTO>() {}.getType();
        ApiResponse<ContentDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call createContentCall(ContentDTO content) throws ApiException {
        String basePath = getBasePath();

        Object body = content;

        // create path and map variables
        String localVarPath = "/hostedtest/content";

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ContentDTO updateContent(String contentId, ContentDTO content) throws ApiException {
        okhttp3.Call localVarCall = updateContentCall(contentId, content);
        Type localVarReturnType = new TypeToken<ContentDTO>() {}.getType();
        ApiResponse<ContentDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call updateContentCall(String contentId, ContentDTO content) throws ApiException {
        String basePath = getBasePath();

        Object body = content;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/content/{content_id}", new Pair(CONTENT_ID, contentId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "PUT", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public void deleteContent(String contentId) throws ApiException {
        okhttp3.Call localVarCall = deleteContentCall(contentId);
        localVarApiClient.execute(localVarCall);
    }

    public okhttp3.Call deleteContentCall(String contentId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/content/{content_id}",
            new Pair(CONTENT_ID, contentId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO addContentToProduct(String productId, Map<String, Boolean> content) throws ApiException {
        okhttp3.Call localVarCall = addContentToProductCall(productId, content);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call addContentToProductCall(String productId, Map<String, Boolean> content) throws ApiException {
        String basePath = getBasePath();

        Object body = content;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}/content",
            new Pair(PRODUCT_ID, productId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO addContentToProduct(String productId, String contentId, Boolean enabled) throws ApiException {
        okhttp3.Call localVarCall = addContentToProductCall(productId, contentId, enabled);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call addContentToProductCall(String productId, String contentId, Boolean enabled) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}/content/{content_id}",
            new Pair(PRODUCT_ID, productId), new Pair(CONTENT_ID, contentId));

        List<Pair> queryParams = new ArrayList<>();
        queryParams.add(new Pair("enabled", Boolean.toString(enabled)));
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "POST", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    public ProductDTO removeContentFromProduct(String productId, List<String> contentIds) throws ApiException {
        okhttp3.Call localVarCall = removeContentFromProductCall(productId, contentIds);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call removeContentFromProductCall(String productId, List<String> contentIds) throws ApiException {
        String basePath = getBasePath();

        Object body = contentIds;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}/content",
            new Pair(PRODUCT_ID, productId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }


    public ProductDTO removeContentFromProduct(String productId, String contentId) throws ApiException {
        okhttp3.Call localVarCall = removeContentFromProductCall(productId, contentId);
        Type localVarReturnType = new TypeToken<ProductDTO>() {}.getType();
        ApiResponse<ProductDTO> localVarResp = localVarApiClient.execute(localVarCall, localVarReturnType);
        return localVarResp.getData();
    }

    public okhttp3.Call removeContentFromProductCall(String productId, String contentId) throws ApiException {
        String basePath = getBasePath();

        Object body = null;

        // create path and map variables
        String localVarPath = buildPath("/hostedtest/products/{product_id}/content/{content_id}",
            new Pair(PRODUCT_ID, productId), new Pair(CONTENT_ID, contentId));

        List<Pair> queryParams = new ArrayList<>();
        List<Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        Map<String, Object> form = new HashMap<>();

        headers.put(ACCEPT_HEADER, APPLICATION_JSON);
        headers.put(CONTENT_TYPE_HEADER, APPLICATION_JSON);

        String[] auth = new String[]{};
        return localVarApiClient.buildCall(basePath, localVarPath, "DELETE", queryParams, collectionQueryParams, body, headers, cookies, form, auth, null);
    }

    @NotNull
    private String buildPath(String basePath, Pair... params) {
        String path = basePath;
        for (Pair param : params) {
            String escapedParam = localVarApiClient.escapeString(param.getValue());
            path = path.replaceAll("\\{" + param.getName() + "}", escapedParam);
        }
        return path;
    }

    @Nullable
    private String getBasePath() {
        String basePath;
        String[] localBasePaths = new String[]{};

        // Determine Base Path to Use
        if (localCustomBaseUrl != null) {
            basePath = localCustomBaseUrl;
        }
        else if (localBasePaths.length > 0) {
            basePath = localBasePaths[localHostIndex];
        }
        else {
            basePath = null;
        }
        return basePath;
    }
}

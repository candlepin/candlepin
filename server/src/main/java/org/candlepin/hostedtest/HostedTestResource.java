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
package org.candlepin.hostedtest;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.util.SuppressSwaggerCheck;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import com.google.inject.persist.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;



/**
 * The HostedTestResource class provides an endpoint for managing the upstream data stored by the
 * backing HostedTestDataStore class
 */
@SuppressSwaggerCheck
@Path("/hostedtest")
public class HostedTestResource {

    @Inject
    private HostedTestDataStore datastore;

    @Inject
    private UniqueIdGenerator idGenerator;

    /**
     * API to check if resource is alive
     *
     * @return always returns true
     */
    @GET
    @Path("/alive")
    @Produces(MediaType.TEXT_PLAIN)
    public Boolean isAlive() {
        return true;
    }

    /**
     * Creates or updates all of the subobjects referenced by the given subscription.
     *
     * @deprecated
     *  This method is a shim to work with the existing "hosted" spec tests that create
     *  subscriptions and its subobjects from the raw JSON provided. In the future, this should be
     *  more well-formed and require that objects are created explicitly rather than implicitly.
     *
     * @param subscription
     *  The subscription for which to create or update subobjects.
     */
    @Deprecated
    protected void createSubscriptionObjects(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        Map<String, ProductData> pmap = new HashMap<>();
        Map<String, ContentData> cmap = new HashMap<>();

        this.addProductsToMap(subscription.getProduct(), pmap);
        this.addProductsToMap(subscription.getProvidedProducts(), pmap);
        this.addProductsToMap(subscription.getDerivedProduct(), pmap);
        this.addProductsToMap(subscription.getDerivedProvidedProducts(), pmap);

        for (ProductData product : pmap.values()) {
            this.addContentToMap(product.getProductContent(), cmap);
        }

        // Create content...
        for (ContentData content : cmap.values()) {
            if (this.datastore.getContent(content.getId()) != null) {
                this.datastore.updateContent(content.getId(), content);
            }
            else {
                this.datastore.createContent(content);
            }
        }

        // Create products...
        for (ProductData product : pmap.values()) {
            if (this.datastore.getProduct(product.getId()) != null) {
                this.datastore.updateProduct(product.getId(), product);
            }
            else {
                this.datastore.createProduct(product);
            }
        }
    }

    private void addProductsToMap(ProductData product, Map<String, ProductData> pmap) {
        if (product != null) {
            if (product.getId() == null || product.getId().matches("\\A\\s*\\z")) {
                throw new BadRequestException("product has a null or empty product ID: " + product);
            }

            pmap.put(product.getId(), product);
        }
    }

    private void addProductsToMap(Collection<ProductData> products, Map<String, ProductData> pmap) {
        if (products != null) {
            for (ProductData product : products) {
                if (product == null) {
                    throw new BadRequestException("product collection contains a null product");
                }

                this.addProductsToMap(product, pmap);
            }
        }
    }

    private void addContentToMap(Collection<ProductContentData> content, Map<String, ContentData> cmap) {
        if (content != null) {
            for (ProductContentData pcdata : content) {
                if (pcdata != null) {
                    ContentData cdata = pcdata.getContent();

                    if (cdata == null) {
                        throw new BadRequestException("product contains a null content: " + pcdata);
                    }

                    if (cdata.getId() == null || cdata.getId().matches("\\A\\s*\\z")) {
                        throw new BadRequestException("content has a null or empty content ID: " + cdata);
                    }

                    cmap.put(cdata.getId(), cdata);
                }
            }
        }
    }


    /**
     * Creates a new owner from the subscription JSON provided. Any UUID
     * provided in the JSON will be ignored when creating the new subscription.
     *
     * @param owner
     *  An OwnerDTO object built from the JSON provided in the request
     *
     * @return
     *  The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/owners")
    public OwnerInfo createOwner(OwnerDTO owner) {
        if (this.datastore.getOwner(owner.getKey()) != null) {
            throw new ConflictException("owner already exists: " + owner.getKey());
        }

        // Create owner object...
        OwnerInfo ownerInfo = this.datastore.createOwner(owner);

        return ownerInfo;
    }

    // TODO: Add remaining owner CRUD operations as needed



    /**
     * Creates a new subscription from the subscription JSON provided. Any UUID
     * provided in the JSON will be ignored when creating the new subscription.
     *
     * @param subscription
     *  A Subscription object built from the JSON provided in the request
     *
     * @return
     *  The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions")
    public SubscriptionInfo createSubscription(Subscription subscription) {
        // Generate an ID if necessary
        if (subscription.getId() == null || subscription.getId().matches("\\A\\s*\\z")) {
            subscription.setId(this.idGenerator.generateId());
        }

        if (this.datastore.getSubscription(subscription.getId()) != null) {
            throw new ConflictException("subscription already exists: " + subscription.getId());
        }

        // Create the subobjects first
        this.createSubscriptionObjects(subscription);

        // Create subscription object...
        SubscriptionInfo sinfo = this.datastore.createSubscription(subscription);

        return sinfo;
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return
     *  A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions")
    public Collection<? extends SubscriptionInfo> listSubscriptions() {
        return this.datastore.listSubscriptions();
    }

    /**
     * Retrieves the subscription for the specified subscription id. If the
     * subscription id cannot be found, this method returns null.
     *
     * @param subscriptionId
     *        The id of the subscription to retrieve
     * @return
     *         The requested Subscription object, or null if the subscription
     *         could not be found
     */
    @GET
    @Path("/subscriptions/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public SubscriptionInfo getSubscription(@PathParam("subscription_id") String subscriptionId) {
        return this.datastore.getSubscription(subscriptionId);
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subscriptionId the ID of the subscription to update
     * @param subscription
     *        A Subscription object built from the JSON provided in the request;
     *        contains the data to use
     *        to update the specified subscription
     * @return
     *         The updated Subscription object
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions/{subscription_id}")
    public SubscriptionInfo updateSubscription(
        @PathParam("subscription_id") String subscriptionId,
        Subscription subscription) {

        if (subscription == null) {
            throw new BadRequestException("no subscription data provided");
        }

        if (this.datastore.getSubscription(subscriptionId) == null) {
            throw new NotFoundException("subscription does not yet exist: " + subscriptionId);
        }

        // Create/Update sub objects
        this.createSubscriptionObjects(subscription);

        // Update subscription
        return this.datastore.updateSubscription(subscriptionId, subscription);
    }

    /**
     * Deletes the specified subscription.
     *
     * @param subscriptionId
     *        The id of the subscription to delete
     * @return
     *         True if the subscription was deleted successfully; false
     *         otherwise
     */
    @DELETE
    @Path("/subscriptions/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteSubscription(@PathParam("subscription_id") String subscriptionId) {
        return this.datastore.deleteSubscription(subscriptionId) != null;
    }

    /**
     * Deletes all data currently maintained by the backing adapter.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void clearData() {
        this.datastore.clearData();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/products/{product_id}/batch_content")
    @Transactional
    public ProductInfo addContentToProduct(
        @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        ProductInfo pinfo = this.datastore.getProduct(productId);

        if (pinfo == null) {
            throw new NotFoundException("product not found: " + productId);
        }

        for (String contentId : contentMap.keySet()) {
            if (this.datastore.getContent(contentId) == null) {
                throw new NotFoundException("content not found: " + contentId);
            }
        }

        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            String contentId = entry.getKey();

            boolean enabled = entry.getValue() != null ?
                entry.getValue() :
                ProductContent.DEFAULT_ENABLED_STATE;

            this.datastore.addContentToProduct(productId, contentId, enabled);
        }

        return pinfo;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/products/{product_id}/content/{content_id}")
    @Transactional
    public ProductInfo addContentToProduct(
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        // Package the params up and pass it off to our batch method
        Map<String, Boolean> contentMap = Collections.singletonMap(contentId, enabled);
        return this.addContentToProduct(productId, contentMap);
    }

    @DELETE
    @Path("/products/{product_id}/content")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInfo removeContentFromProduct(@PathParam("product_id") String productId,
        Collection<String> contentIds) {

        if (this.datastore.getProduct(productId) == null) {
            throw new NotFoundException("product not found: " + productId);
        }

        if (contentIds != null) {
            for (String contentId : contentIds) {
                this.datastore.removeContentFromProduct(productId, contentId);
            }
        }

        return this.datastore.getProduct(productId);
    }

    @DELETE
    @Path("/products/{product_id}/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductInfo removeContentFromProduct(@PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        return this.removeContentFromProduct(productId, Collections.<String>singletonList(contentId));
    }

    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Collection<? extends ProductInfo> listProducts() {
        return this.datastore.listProducts();
    }

    @GET
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductInfo getProduct(@PathParam("product_id") String productId) {
        ProductInfo pinfo = this.datastore.getProduct(productId);

        if (pinfo == null) {
            throw new NotFoundException("product does not exist: " + productId);
        }

        return pinfo;
    }

    @POST
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductInfo createProduct(ProductDTO product) {
        if (product == null) {
            throw new BadRequestException("product is null");
        }

        if (product.getId() == null || product.getId().isEmpty()) {
            throw new BadRequestException("product lacks a product ID: " + product);
        }

        if (this.datastore.getProduct(product.getId()) != null) {
            throw new ConflictException("product already exists: " + product.getId());
        }

        return this.datastore.createProduct(product);
    }

    @PUT
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductInfo updateProduct(
        @PathParam("product_id") String productId,
        ProductDTO update) {

        if (update == null) {
            throw new BadRequestException("product update is null");
        }

        if (this.datastore.getProduct(productId) == null) {
            throw new NotFoundException("product does not yet exist: " + productId);
        }

        return this.datastore.updateProduct(productId, update);
    }

    @DELETE
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteProduct(@PathParam("product_id") String productId) {
        return this.datastore.deleteProduct(productId) != null;
    }


    @GET
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Collection<? extends ContentInfo> listContent() {
        return this.datastore.listContent();
    }

    @GET
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public ContentInfo getContent(@PathParam("content_id") String contentId) {
        ContentInfo cinfo = this.datastore.getContent(contentId);

        if (cinfo == null) {
            throw new NotFoundException("content does not exist: " + contentId);
        }

        return cinfo;
    }

    @POST
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ContentInfo createContent(ContentDTO content) {
        if (content == null) {
            throw new BadRequestException("content is null");
        }

        if (content.getId() == null || content.getId().isEmpty()) {
            throw new BadRequestException("content lacks a content ID: " + content);
        }

        if (this.datastore.getContent(content.getId()) != null) {
            throw new ConflictException("content already exists: " + content.getId());
        }

        return this.datastore.createContent(content);
    }

    @PUT
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ContentInfo updateContent(
        @PathParam("content_id") String contentId,
        ContentDTO update) {

        if (update == null) {
            throw new BadRequestException("content update is null");
        }

        if (this.datastore.getContent(contentId) == null) {
            throw new NotFoundException("content does not yet exist: " + contentId);
        }

        return this.datastore.updateContent(contentId, update);
    }

    @DELETE
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteContent(@PathParam("content_id") String contentId) {
        return this.datastore.deleteContent(contentId) != null;
    }

}

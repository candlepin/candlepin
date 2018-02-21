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

import org.candlepin.common.util.SuppressSwaggerCheck;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.UniqueIdGenerator;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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
 * The SubscriptionResource class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
@SuppressSwaggerCheck
@Path("/hostedtest/")
public class HostedTestSubscriptionResource {

    @Inject
    private HostedTestSubscriptionServiceAdapter adapter;

    @Inject
    private UniqueIdGenerator idGenerator;

    @Inject
    private I18n i18n;

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
     * Deletes all subscriptions, products and content.
     */
    @DELETE
    public void clearUpstreamData() {
        adapter.clearData();
    }

    /**
     * Creates a new subscription from the subscription JSON provided. Any UUID
     * provided in the JSON will be ignored when creating the new subscription.
     *
     * @param subscription
     *        A Subscription object built from the JSON provided in the request
     * @return
     *         The newly created Subscription object
     */
    @POST
    @Path("/subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription createSubscription(Subscription subscription) {
        if (subscription.getId() == null || subscription.getId().trim().length() == 0) {
            subscription.setId(this.idGenerator.generateId());
        }

        return this.adapter.createSubscription(subscription);
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return
     *  A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<Subscription> listSubscriptions() {
        return this.adapter.getSubscriptions();
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
    public Subscription getSubscription(@PathParam("subscription_id") String subscriptionId) {
        return this.adapter.getSubscription(subscriptionId);
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subId
     *  The ID of the subscription to update
     *
     * @param dto
     *  A subscription DTO containing the changes to apply
     *
     * @return
     *  The updated subscription
     */
    @PUT
    @Path("/subscriptions/{subscription_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription updateSubscription(@PathParam("subscription_id") String subId, Subscription dto) {
        return this.adapter.updateSubscription(subId, dto);
    }

    /**
     * Deletes the specified subscription.
     *
     * @param subscriptionId
     *  The id of the subscription to delete
     */
    @DELETE
    @Path("/subscriptions/{subscription_id}")
    public void deleteSubscription(@PathParam("subscription_id") String subscriptionId) {
        this.adapter.deleteSubscription(subscriptionId);
    }

    @POST
    @Path("/products")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData createUpstreamProduct(ProductData dto) {
        return this.adapter.createProduct(dto);
    }

    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<ProductData> listUpstreamProducts() {
        return this.adapter.getProducts();
    }

    @GET
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData getUpstreamProduct(@PathParam("product_id") String productId) {
        return this.adapter.getProduct(productId);
    }

    @PUT
    @Path("/products/{product_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData updateUpstreamProduct(@PathParam("product_id") String productId, ProductData dto) {
        return this.adapter.updateProduct(productId, dto);
    }

    @DELETE
    @Path("/products/{product_id}")
    public void deleteUpstreamProduct(@PathParam("product_id") String productId) {
        this.adapter.deleteProduct(productId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/content")
    public ContentData createUpstreamContent(ContentData dto) {
        return this.adapter.createContent(dto);
    }

    @GET
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<ContentData> listUpstreamContent() {
        return this.adapter.getContent();
    }

    @GET
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ContentData getUpstreamContent(@PathParam("content_id") String contentId) {
        return this.adapter.getContent(contentId);
    }

    @PUT
    @Path("/content/{content_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ContentData updateUpstreamContent(@PathParam("content_id") String contentId, ContentData dto) {
        return this.adapter.updateContent(contentId, dto);
    }

    @DELETE
    @Path("/content/{content_id}")
    public void deleteUpstreamContent(@PathParam("content_id") String contentId) {
        this.adapter.deleteContent(contentId);
    }

    @POST
    @Path("/products/{product_id}/content")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData addMultipleContentToProductUpstream(@PathParam("product_id") String productId,
        Map<String, Boolean> contentIdMap) {

        return this.adapter.addContentToProduct(productId, contentIdMap);
    }

    @POST
    @Path("/products/{product_id}/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData addContentToProductUpstream(@PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") @DefaultValue("true") boolean enabled) {

        Map<String, Boolean> contentIdMap = Collections.<String, Boolean>singletonMap(contentId, enabled);
        return this.addMultipleContentToProductUpstream(productId, contentIdMap);
    }

    @DELETE
    @Path("/products/{product_id}/content")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData removeMultipleContentFromProductUpstream(@PathParam("product_id") String productId,
        Collection<String> contentIds) {

        return this.adapter.removeContentFromProduct(productId, contentIds);
    }

    @DELETE
    @Path("/products/{product_id}/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductData removeContentToProductUpstream(@PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        List<String> contentIds = Collections.<String>singletonList(contentId);
        return this.removeMultipleContentFromProductUpstream(productId, contentIds);
    }

}

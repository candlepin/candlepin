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

import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.util.SuppressSwaggerCheck;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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
 * The SubscriptionResource class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
@SuppressSwaggerCheck
@Path("/hostedtest/subscriptions")
public class HostedTestSubscriptionResource {

    @Inject
    private HostedTestSubscriptionServiceAdapter adapter;

    @Inject
    private UniqueIdGenerator idGenerator;

    @Inject
    private ResolverUtil resolverUtil;

    @Inject
    private ProductManager productManager;

    @Inject
    private ProductCurator productCurator;

    @Inject
    private OwnerContentCurator ownerContentCurator;

    @Inject
    private OwnerCurator ownerCurator;

    @Inject
    private OwnerProductCurator ownerProductCurator;

    @Inject
    private I18n i18n;

    @Inject
    private ModelTranslator translator;

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
     * Creates a new subscription from the subscription JSON provided. Any UUID
     * provided in the JSON will be ignored when creating the new subscription.
     *
     * @param subscription
     *        A Subscription object built from the JSON provided in the request
     * @return
     *         The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription createSubscription(Subscription subscription) {
        if (subscription.getId() == null || subscription.getId().trim().length() == 0) {
            subscription.setId(this.idGenerator.generateId());
        }
        return adapter.createSubscription(resolverUtil.resolveSubscription(subscription));
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return
     *  A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> listSubscriptions() {
        return adapter.getSubscriptions();
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
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(@PathParam("subscription_id") String subscriptionId) {
        return adapter.getSubscription(subscriptionId);
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subscriptionNew
     *        A Subscription object built from the JSON provided in the request;
     *        contains the data to use
     *        to update the specified subscription
     * @return
     *         The updated Subscription object
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription updateSubscription(Subscription subscriptionNew) {
        return adapter.updateSubscription(resolverUtil.resolveSubscription(subscriptionNew));
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
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteSubscription(@PathParam("subscription_id") String subscriptionId) {
        return adapter.deleteSubscription(subscriptionId);
    }

    /**
     * Deletes all subscriptions.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteAllSubscriptions() {
        adapter.deleteAllSubscriptions();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/owners/{owner_key}/products/{product_id}/batch_content")
    @Transactional
    public ProductDTO addBatchContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        Collection<ProductContent> productContent = new LinkedList<ProductContent>();

        ProductDTO pdto = this.translator.translate(product, ProductDTO.class);

        // Impl note:
        // This is a wholely inefficient way of doing this. When we return to using ID-based linking
        // and we're not linking the universe with our model, we can just attach the IDs directly
        // without needing all this DTO conversion back and forth.
        // Alternatively, we can shut off Hibernate's auto-commit junk and get in the habit of
        // calling commit methods as necessary so we don't have to work with DTOs internally.

        boolean changed = false;
        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.fetchContent(owner, entry.getKey());
            boolean enabled = entry.getValue() != null ?
                entry.getValue() :
                ProductContent.DEFAULT_ENABLED_STATE;

            ContentDTO cdto = this.translator.translate(content, ContentDTO.class);

            changed |= pdto.addContent(cdto, enabled);
        }

        if (changed) {
            product = this.productManager.updateProduct(pdto, owner, true);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/owners/{owner_key}/products/{product_id}/content/{content_id}")
    @Transactional
    public ProductDTO addContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        // Package the params up and pass it off to our batch method
        Map<String, Boolean> contentMap = Collections.singletonMap(contentId, enabled);
        return this.addBatchContent(ownerKey, productId, contentMap);
    }

    @PUT
    @Path("/owners/{owner_key}/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductDTO updateProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        ProductDTO update) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product existing = this.fetchProduct(owner, productId);
        Product updated = this.productManager.updateProduct(update, owner, true);

        return this.translator.translate(updated, ProductDTO.class);
    }

    protected Product fetchProduct(Owner owner, String productId) {
        Product product = this.ownerProductCurator.getProductById(owner, productId);

        if (product == null) {
            throw new NotFoundException(i18n.tr("Product with ID ''{0}'' could not be found.", productId));
        }

        return product;
    }

    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    protected Content fetchContent(Owner owner, String contentId) {
        Content content = this.ownerContentCurator.getContentById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(i18n.tr("Content with ID \"{0}\" could not be found.", contentId));
        }

        return content;
    }
}

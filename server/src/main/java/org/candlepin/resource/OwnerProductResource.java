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
package org.candlepin.resource;

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Statistic;
import org.candlepin.model.StatisticCurator;
import org.candlepin.pinsetter.tasks.RefreshPoolsForProductJob;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.LockModeType;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * API Gateway into /product
 *
 * @version $Rev$
 */
@Path("/owners/{owner_key}/products")
public class OwnerProductResource {
    private static Logger log = LoggerFactory.getLogger(OwnerProductResource.class);

    private ProductCurator productCurator;
    private ContentCurator contentCurator;
    private OwnerCurator ownerCurator;
    private ProductCertificateCurator productCertCurator;
    private StatisticCurator statisticCurator;
    private I18n i18n;


    @Inject
    public OwnerProductResource(ProductCurator productCurator, ContentCurator contentCurator,
        OwnerCurator ownerCurator, ProductCertificateCurator productCertCurator,
        StatisticCurator statisticCurator, I18n i18n) {

        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.productCertCurator = productCertCurator;
        this.statisticCurator = statisticCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner
     * could not be found, this method throws an exception.
     *
     * @param key
     *  The key for the owner to retrieve
     *
     * @throws NotFoundException
     *  if an owner could not be found for the specified key.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    /**
     * Retrieves a list of Products
     *
     * @param productIds if specified, the list of product IDs to return product info for
     * @return a list of Product objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @QueryParam("product") List<String> productIds) {

        Owner owner = this.getOwnerByKey(ownerKey);

        return productIds.isEmpty() ?
            productCurator.listByOwner(owner) :
            productCurator.listAllByIds(owner, productIds);
    }

    /**
     * Retrieves a single Product
     * <p>
     * <pre>
     * {
     *   "id" : "product_id",
     *   "name" : "product_name",
     *   "multiplier" : 1,
     *   "attributes" : [ {
     *     "name" : "version",
     *     "value" : "1.0",
     *     "created" : [date],
     *     "updated" : [date]
     *   } ],
     *   "productContent" : [ ],
     *   "dependentProductIds" : [ ],
     *   "href" : "/products/product_id",
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @param productId id of the product sought.
     * @return a Product object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public Product getProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = productCurator.lookupById(owner, productId);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with ID ''{0}'' could not be found.", productId)
            );
        }

        return product;
    }

    /**
     * Retreives a Certificate for a Product
     *
     * @return a ProductCertificate object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{product_id}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductCertificate getProductCertificate(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId) {

        Product product = this.getProduct(ownerKey, productId);
        return this.productCertCurator.getCertForProduct(product);
    }

    /**
     * Creates a Product
     * <p>
     * Returns either the new created Product or the Product that already existed.
     *
     * @param product
     * @return a Product object
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Product createProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        Product product) {

        Owner owner = this.getOwnerByKey(ownerKey);

        product.setOwner(owner);

        return productCurator.create(product);
    }

    /**
     * Updates a Product
     *
     * @return a Product object
     * @httpcode 400
     * @httpcode 200
     */
    @PUT
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Product updateProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        Product product) {

        Product toUpdate = this.getProduct(ownerKey, productId);

        if (performProductUpdates(toUpdate, product)) {
            this.productCurator.merge(toUpdate);
        }

        return toUpdate;
    }

    protected boolean performProductUpdates(Product existing, Product incoming) {
        boolean changesMade = false;

        if (incoming.getName() != null && !existing.getName().equals(incoming.getName()) &&
            !incoming.getName().isEmpty()) {

            log.debug("Updating product name");
            changesMade = true;
            existing.setName(incoming.getName());
        }

        if (incoming.getAttributes() != null &&
            !existing.getAttributes().equals(incoming.getAttributes())) {

            log.debug("Updating product attributes");

            // clear and addall here instead of replacing instance so there are no
            // dangling memory references
            existing.getAttributes().clear();
            existing.getAttributes().addAll(incoming.getAttributes());
            changesMade = true;
        }

        if (incoming.getDependentProductIds() != null &&
            !existing.getDependentProductIds().equals(incoming.getDependentProductIds())) {

            log.debug("Updating dependent product ids");

            // clear and addall here instead of replacing instance so there are no
            // dangling memory references
            existing.getDependentProductIds().clear();
            existing.getDependentProductIds().addAll(incoming.getDependentProductIds());
            changesMade = true;
        }

        if (incoming.getMultiplier() != null &&
            existing.getMultiplier().longValue() != incoming.getMultiplier().longValue()) {

            log.debug("Updating product multiplier");
            changesMade = true;
            existing.setMultiplier(incoming.getMultiplier());
        }

        // not calling setHref() it's a no op and pointless to call.

        return changesMade;
    }

    /**
     * Retrieves a Content instance for the content with the specified id. If no matching content
     * could be found, this method throws an exception.
     *
     * @param owner
     *  The organization
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws NotFoundException
     *  if no matching content could be found with the specified id.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     */
    protected Content getContent(Owner owner, String contentId) {
        Content content = this.contentCurator.lookupById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
    }

    /**
     * Adds Content to a Product
     * <p>
     * Batch mode
     *
     * @return a Product object
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/batch_content")
    public Product addBatchContent(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        Product product = this.getProduct(ownerKey, productId);
        List<ProductContent> productContent = new LinkedList<ProductContent>();

        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.getContent(product.getOwner(), entry.getKey());
            productContent.add(new ProductContent(product, content, entry.getValue()));
        }

        product.getProductContent().addAll(productContent);

        // TODO: Why are we doing this instead of just returning the product we already have?
        return productCurator.find((product.getUuid()));
    }

    /**
     * Adds Content to a Product
     * <p>
     * Single mode
     *
     * @return a Product object
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/content/{content_id}")
    public Product addContent(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        Product product = this.getProduct(ownerKey, productId);
        Content content = this.getContent(product.getOwner(), contentId);

        this.productCurator.lock(product, LockModeType.PESSIMISTIC_WRITE);

        ProductContent productContent = new ProductContent(product, content, enabled);
        product.addProductContent(productContent);

        this.productCurator.merge(product);
        this.productCurator.flush();

        return product;
    }

    /**
     * Removes Content from a Product
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/content/{content_id}")
    public void removeContent(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        Product product = this.getProduct(ownerKey, productId);
        Content content = this.getContent(product.getOwner(), contentId);

        productCurator.removeProductContent(product, content);
    }

    /**
     * Removes a Product
     *
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}")
    public void deleteProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId) {

        Product product = this.getProduct(ownerKey, productId);

        if (productCurator.productHasSubscriptions(product)) {
            throw new BadRequestException(
                i18n.tr(
                    "Product with ID ''{0}'' cannot be deleted while subscriptions exist.",
                    productId
                )
            );
        }

        productCurator.delete(product);
    }

    /**
     * Retrieves a list of Statistics for a Product
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("/{product_id}/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getProductStats(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {

        return this.getProductStats(ownerKey, productId, null, from, to, days);
    }

    /**
     * Retrieves a list of Statistics for a Product
     * <p>
     * By Statistic type
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("/{product_id}/statistics/{vtype}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getProductStats(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        @PathParam("vtype") String valueType,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {

        Owner owner = this.getOwnerByKey(ownerKey);

        return statisticCurator.getStatisticsByProduct(owner, productId, valueType,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
    }

    /**
     * Refreshes Pools by Product
     *
     * @param productId
     * @param lazyRegen
     * @return a JobDetail object
     */
    @PUT
    @Path("/{product_id}/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public JobDetail refreshPoolsForProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @Verify(Product.class) @PathParam("product_id") String productId,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Product product = this.getProduct(ownerKey, productId);

        return RefreshPoolsForProductJob.forProduct(product, lazyRegen);
    }
}

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

import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.pinsetter.tasks.RefreshPoolsForProductJob;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
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
    private ProductManager productManager;
    private Configuration config;
    private I18n i18n;

    @Inject
    public OwnerProductResource(ProductCurator productCurator, ContentCurator contentCurator,
            OwnerCurator ownerCurator, ProductCertificateCurator productCertCurator,
            ProductManager productManager, Configuration config, I18n i18n) {

        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.ownerCurator = ownerCurator;
        this.productCertCurator = productCertCurator;
        this.productManager = productManager;
        this.config = config;
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
     *
     * @httpcode 200
     * @httpcode 404
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    /**
     * Retrieves a Product instance for the product with the specified id. If no matching product
     * could be found, this method throws an exception.
     *
     * @param owner
     *  The organization
     *
     * @param productId
     *  The ID of the product to retrieve
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified id
     *
     * @return
     *  the Product instance for the product with the specified id
     */
    protected Product fetchProduct(Owner owner, String productId) {
        Product product = productCurator.lookupById(owner, productId);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with ID ''{0}'' could not be found.", productId)
            );
        }

        return product;
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
     *  the Content instance for the content with the specified id
     */
    protected Content fetchContent(Owner owner, String contentId) {
        Content content = this.contentCurator.lookupById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
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
        return this.fetchProduct(owner, productId);
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
    @Transactional
    public ProductCertificate getProductCertificate(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId) {

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
    @Transactional
    public Product createProduct(
        @PathParam("owner_key") String ownerKey,
        Product product) {

        Owner owner = this.getOwnerByKey(ownerKey);

        return productManager.createProduct(product, owner);
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
    @Transactional
    public Product updateProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        Product product) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product existing = this.getProduct(ownerKey, productId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{1}\" is locked", product.getId()));
        }

        return this.productManager.updateProduct(((Product) existing.clone()).merge(product), owner, true);
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
    @Transactional
    public Product addBatchContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        List<ProductContent> productContent = new LinkedList<ProductContent>();

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{1}\" is locked", product.getId()));
        }

        this.productCurator.lock(product, LockModeType.PESSIMISTIC_WRITE);
        product = (Product) product.clone();
        boolean change = false;

        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.fetchContent(owner, entry.getKey());
            change = product.addContent(content, entry.getValue()) || change;
        }

        return change ? this.productManager.updateProduct(product, owner, true) : product;
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
    @Transactional
    public Product addContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        Content content = this.fetchContent(owner, contentId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{1}\" is locked", product.getId()));
        }

        this.productCurator.lock(product, LockModeType.PESSIMISTIC_WRITE);

        product = (Product) product.clone();
        return product.addContent(content, enabled) ?
            this.productManager.updateProduct(product, owner, true) :
            product;
    }

    /**
     * Removes Content from a Product
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/content/{content_id}")
    @Transactional
    public void removeContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        Content content = this.fetchContent(owner, contentId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{1}\" is locked", product.getId()));
        }

        // Remove content
        this.productManager.removeProductContent(product, Arrays.asList(content), owner, true);
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
    @Transactional
    public void deleteProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{1}\" is locked", product.getId()));
        }

        if (this.productCurator.productHasSubscriptions(product, owner)) {
            throw new BadRequestException(
                i18n.tr(
                    "Product with ID ''{0}'' cannot be deleted while subscriptions exist.",
                    productId
                )
            );
        }

        this.productManager.removeProduct(product, owner);
    }

    /**
     * Refreshes Pools by Product
     *
     * @param productId
     * @param lazyRegen
     * @return a JobDetail object
     * @httpcode 200
     */
    @PUT
    @Path("/{product_id}/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public JobDetail refreshPoolsForProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Product product = this.getProduct(ownerKey, productId);

        return RefreshPoolsForProductJob.forProduct(product, lazyRegen);
    }
}

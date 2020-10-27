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

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RefreshPoolsForProductJob;
import org.candlepin.auth.Verify;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ProductManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
@Api(value = "owners", authorizations = { @Authorization("basic") })
public class OwnerProductResource {
    private static Logger log = LoggerFactory.getLogger(OwnerProductResource.class);

    private Configuration config;
    private I18n i18n;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCertificateCurator productCertCurator;
    private ProductCurator productCurator;
    private ProductManager productManager;
    private ModelTranslator translator;
    private JobManager jobManager;

    @Inject
    public OwnerProductResource(Configuration config, I18n i18n, OwnerCurator ownerCurator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator,
        ProductCertificateCurator productCertCurator, ProductCurator productCurator,
        ProductManager productManager, ModelTranslator translator, JobManager jobManager) {

        this.config = config;
        this.i18n = i18n;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerCurator = ownerCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCertCurator = productCertCurator;
        this.productCurator = productCurator;
        this.productManager = productManager;
        this.translator = translator;
        this.jobManager = jobManager;
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
        Owner owner = this.ownerCurator.getByKey(key);
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
        Product product = this.ownerProductCurator.getProductById(owner, productId);
        if (product == null) {
            throw new NotFoundException(i18n.tr("Product with ID \"{0}\" could not be found.", productId));
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
        Content content = this.ownerContentCurator.getContentById(owner, contentId);
        if (content == null) {
            throw new NotFoundException(i18n.tr("Content with ID \"{0}\" could not be found.", contentId));
        }

        return content;
    }

    @ApiOperation(notes = "Retrieves a list of Products", value = "List Products for an Owner",
        response = ProductDTO.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<ProductDTO> listProducts(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @QueryParam("product") List<String> productIds) {

        Owner owner = this.getOwnerByKey(ownerKey);

        CandlepinQuery<Product> query = productIds != null && !productIds.isEmpty() ?
            this.ownerProductCurator.getProductsByIds(owner, productIds) :
            this.ownerProductCurator.getProductsByOwner(owner);

        return this.translator.translateQuery(query, ProductDTO.class);
    }

    @ApiOperation(notes = "Retrieves a single Product", value = "getProduct")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductDTO getProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        return this.translator.translate(product, ProductDTO.class);
    }

    @ApiOperation(notes = "Retrieves a Certificate for a Product", value = "getProductCertificate")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_id}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductCertificateDTO getProductCertificate(
        @PathParam("owner_key") String ownerKey,
        @ApiParam(name = "productId", required = true, value = "Numeric product identifier")
        @PathParam("product_id") String productId) {

        if (!productId.matches("\\d+")) {
            throw new BadRequestException(i18n.tr("Only numeric product IDs are allowed."));
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        ProductCertificate productCertificate = this.productCertCurator.getCertForProduct(product);
        return this.translator.translate(productCertificate, ProductCertificateDTO.class);
    }

    @ApiOperation(notes = "Creates a Product.  Returns either the new created Product or " +
        "the Product that already existed.", value = "createProduct")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductDTO createProduct(
        @PathParam("owner_key") String ownerKey,
        ProductDTO pdto) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product entity = productManager.createProduct(owner, pdto);

        return this.translator.translate(entity, ProductDTO.class);
    }

    @ApiOperation(notes = "Updates a Product", value = "updateProduct")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductDTO updateProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @ApiParam(name = "update", required = true) ProductDTO update) {

        if (StringUtils.isEmpty(update.getId())) {
            update.setId(productId);
        }
        else if (!StringUtils.equals(update.getId(), productId)) {
            throw new BadRequestException(
                i18n.tr("Contradictory ids in update request: {0}, {1}", productId, update.getId())
            );
        }

        Owner owner = this.getOwnerByKey(ownerKey);

        // Get the matching owner_product & lock it while we are doing the update for this org
        // This is done in order to prevent collisions in updates on different parts of the product
        OwnerProduct ownerProduct = ownerProductCurator.getOwnerProductByProductId(owner, productId);
        ownerProductCurator.lock(ownerProduct);
        ownerProductCurator.refresh(ownerProduct);

        Product existing = ownerProduct.getProduct();

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", existing.getId()));
        }

        Product updated = this.productManager.updateProduct(owner, update, true);

        return this.translator.translate(updated, ProductDTO.class);
    }

    /**
     * Creates an new, unmanaged product instance using the Red Hat product ID and content of the
     * given product entity.
     *
     * @param entity
     *  the product instance from which to copy the Red Hat product ID and content
     *
     * @return
     *  an unmanaged product instance
     */
    private Product buildProductForBatchContentChange(Product entity) {
        // Impl note: we need to fully clone the object to ensure we don't make any changes to any
        // other fields; or we need to create a new product implementation that returns the correct
        // no-change value for every other field.
        return (Product) entity.clone();
    }

    @ApiOperation(notes = "Adds one or more Content entities to a Product", value = "addBatchContent")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/batch_content")
    @Transactional
    public ProductDTO addBatchContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @ApiParam(name = "contentMap", required = true) Map<String, Boolean> contentMap) {

        Owner owner = this.getOwnerByKey(ownerKey);

        // Get the matching owner_product & lock it while we are doing the update for this org
        // This is done in order to prevent collisions in updates on different parts of the product
        OwnerProduct ownerProduct = ownerProductCurator.getOwnerProductByProductId(owner, productId);
        ownerProductCurator.lock(ownerProduct);
        ownerProductCurator.refresh(ownerProduct);

        Product product = ownerProduct.getProduct();

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        Product update = this.buildProductForBatchContentChange(product);

        boolean changed = false;
        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.fetchContent(owner, entry.getKey());
            boolean enabled = entry.getValue() != null ?
                entry.getValue() :
                ProductContent.DEFAULT_ENABLED_STATE;

            changed |= update.addContent(content, enabled);
        }

        if (changed) {
            product = this.productManager.updateProduct(owner, update, true);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    @ApiOperation(notes = "Adds a single Content to a Product", value = "addContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/{product_id}/content/{content_id}")
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

    @ApiOperation(notes = "Adds one or more Content entities to a Product", value = "addBatchContent")
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/batch_content")
    @Transactional
    public ProductDTO removeBatchContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @ApiParam(name = "content", required = true) List<String> contentIds) {

        Owner owner = this.getOwnerByKey(ownerKey);

        // Get the matching owner_product & lock it while we are doing the update for this org
        // This is done in order to prevent collisions in updates on different parts of the product
        OwnerProduct ownerProduct = ownerProductCurator.getOwnerProductByProductId(owner, productId);
        ownerProductCurator.lock(ownerProduct);
        ownerProductCurator.refresh(ownerProduct);

        Product product = ownerProduct.getProduct();

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        Product update = this.buildProductForBatchContentChange(product);

        boolean changed = false;
        for (String contentId : contentIds) {
            changed |= update.removeContent(contentId);
        }

        if (changed) {
            product = this.productManager.updateProduct(owner, update, true);
        }

        return this.translator.translate(product, ProductDTO.class);
    }

    @ApiOperation(notes = "Removes a single Content from a Product", value = "removeContent")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/content/{content_id}")
    @Transactional
    public ProductDTO removeContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        // Package up the params and pass it to our bulk operation
        return this.removeBatchContent(ownerKey, productId, Collections.<String>singletonList(contentId));
    }

    @ApiOperation(notes = "Removes a Product", value = "deleteProduct")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
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
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        if (this.productCurator.productHasSubscriptions(owner, product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more subscriptions",
                productId));
        }

        if (this.productCurator.productHasParentProducts(owner, product)) {
            throw new BadRequestException(i18n.tr(
                "Product \"{0}\" cannot be deleted while referenced by one or more products",
                productId));
        }

        this.productManager.removeProduct(owner, product);
    }

    @ApiOperation(notes = "Refreshes Pools by Product", value = "refreshPoolsForProduct")
    @PUT
    @Path("/{product_id}/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public AsyncJobStatusDTO refreshPoolsForProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @QueryParam("lazy_regen") @DefaultValue("true") boolean lazyRegen) throws JobException {

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        JobConfig config = RefreshPoolsForProductJob.createJobConfig()
            .setProduct(product)
            .setLazy(lazyRegen);

        AsyncJobStatus status = jobManager.queueJob(config);
        return this.translator.translate(status, AsyncJobStatusDTO.class);
    }
}

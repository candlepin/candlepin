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

import org.apache.commons.lang3.StringUtils;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Content;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ProductData;
import org.candlepin.pinsetter.tasks.RefreshPoolsForProductJob;
import org.candlepin.util.ElementTransformer;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collection;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

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

    @Inject
    public OwnerProductResource(Configuration config, I18n i18n, OwnerCurator ownerCurator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator,
        ProductCertificateCurator productCertCurator, ProductCurator productCurator,
        ProductManager productManager) {

        this.config = config;
        this.i18n = i18n;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerCurator = ownerCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCertCurator = productCertCurator;
        this.productCurator = productCurator;
        this.productManager = productManager;
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
        Product product = this.ownerProductCurator.getProductById(owner, productId);

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
        Content content = this.ownerContentCurator.getContentById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
    }

    @ApiOperation(notes = "Retrieves a list of Products", value = "List Products for an Owner",
        response = Product.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<ProductData> listProducts(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @QueryParam("product") List<String> productIds) {

        Owner owner = this.getOwnerByKey(ownerKey);

        CandlepinQuery<Product> query = productIds != null && !productIds.isEmpty() ?
            this.ownerProductCurator.getProductsByIds(owner, productIds) :
            this.ownerProductCurator.getProductsByOwner(owner);

        return query.transform(new ElementTransformer<Product, ProductData>() {
            @Override
            public ProductData transform(Product element) {
                return element.toDTO();
            }
        });
    }

    @ApiOperation(notes = "Retrieves a single Product", value = "getProduct")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductData getProduct(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        return product.toDTO();
    }

    @ApiOperation(notes = "Retreives a Certificate for a Product", value = "getProductCertificate")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_id}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    @Transactional
    public ProductCertificate getProductCertificate(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        return this.productCertCurator.getCertForProduct(product);
    }

    @ApiOperation(notes = "Creates a Product.  Returns either the new created Product or " +
        "the Product that already existed.", value = "createProduct")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductData createProduct(
        @PathParam("owner_key") String ownerKey,
        ProductData product) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product entity = productManager.createProduct(product, owner);

        return entity.toDTO();
    }

    @ApiOperation(notes = "Updates a Product", value = "updateProduct")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public ProductData updateProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @ApiParam(name = "update", required = true) ProductData update) {

        if (StringUtils.isEmpty(update.getId())) {
            update.setId(productId);
        }
        else if (!StringUtils.equals(update.getId(), productId)) {
            throw new BadRequestException(
                i18n.tr("Contradictory ids in update request: {0}, {1}", productId, update.getId())
            );
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product existing = this.fetchProduct(owner, productId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", existing.getId()));
        }

        Product updated = this.productManager.updateProduct(update, owner, true);

        return updated.toDTO();
    }

    @ApiOperation(notes = "Adds one or more Content entities to a Product", value = "addBatchContent")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_id}/batch_content")
    @Transactional
    public ProductData addBatchContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @ApiParam(name = "contentMap", required = true) Map<String, Boolean> contentMap) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        Collection<ProductContent> productContent = new LinkedList<ProductContent>();

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        this.productCurator.lock(product, LockModeType.PESSIMISTIC_WRITE);
        boolean change = false;

        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = this.fetchContent(owner, entry.getKey());
            productContent.add(new ProductContent(product, content, entry.getValue()));
        }

        product = this.productManager.addContentToProduct(product, productContent, owner, true);

        return product.toDTO();
    }

    @ApiOperation(notes = "Adds a single Content to a Product", value = "addContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/{product_id}/content/{content_id}")
    @Transactional
    public ProductData addContent(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);
        Content content = this.fetchContent(owner, contentId);

        if (product.isLocked()) {
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        this.productCurator.lock(product, LockModeType.PESSIMISTIC_WRITE);

        product = this.productManager.addContentToProduct(
            product, Arrays.asList(new ProductContent(product, content, enabled)), owner, true
        );

        return product.toDTO();
    }

    @ApiOperation(notes = "Removes a single Content from a Product", value = "removeContent")
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
            throw new ForbiddenException(i18n.tr("product \"{0}\" is locked", product.getId()));
        }

        // Remove content
        this.productManager.removeProductContent(product, Arrays.asList(content), owner, true);
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
            throw new BadRequestException(
                i18n.tr(
                    "Product with ID ''{0}'' cannot be deleted while subscriptions exist.",
                    productId
                )
            );
        }

        this.productManager.removeProduct(owner, product);
    }

    @ApiOperation(notes = "Refreshes Pools by Product", value = "refreshPoolsForProduct")
    @PUT
    @Path("/{product_id}/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public JobDetail refreshPoolsForProduct(
        @PathParam("owner_key") String ownerKey,
        @PathParam("product_id") String productId,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        Owner owner = this.getOwnerByKey(ownerKey);
        Product product = this.fetchProduct(owner, productId);

        return RefreshPoolsForProductJob.forProduct(product, lazyRegen);
    }
}

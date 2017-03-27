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

import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ResultIterator;
import org.candlepin.model.dto.ProductData;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;

import com.google.inject.Inject;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
 */
@Path("/products")
@Api(value = "products", authorizations = { @Authorization("basic") })
public class ProductResource {

    private static Logger log = LoggerFactory.getLogger(ProductResource.class);
    private ProductCurator productCurator;
    private OwnerCurator ownerCurator;
    private ProductCertificateCurator productCertCurator;
    private Configuration config;
    private I18n i18n;

    @Inject
    public ProductResource(ProductCurator productCurator, OwnerCurator ownerCurator,
        ProductCertificateCurator productCertCurator, Configuration config, I18n i18n) {

        this.productCurator = productCurator;
        this.productCertCurator = productCertCurator;
        this.ownerCurator = ownerCurator;
        this.config = config;
        this.i18n = i18n;
    }

    /**
     * Retrieves a Product instance for the product with the specified id. If no matching product
     * could be found, this method throws an exception.
     *
     * @param productUuid
     *  The ID of the product to retrieve
     *
     * @throws NotFoundException
     *  if no matching product could be found with the specified id
     *
     * @return
     *  the Product instance for the product with the specified id
     */
    protected Product fetchProduct(String productUuid) {
        Product product = this.productCurator.find(productUuid);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found.", productUuid)
            );
        }

        return product;
    }

    @ApiOperation(notes = "Retrieves a single Product", value = "getProduct")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductData getProduct(@PathParam("product_uuid") String productUuid) {
        Product product = this.fetchProduct(productUuid);
        return product.toDTO();
    }

    @ApiOperation(notes = "Retreives a Certificate for a Product", value = "getProductCertificate")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Path("/{product_uuid}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductCertificate getProductCertificate(
        @PathParam("product_uuid") String productUuid) {

        // TODO:
        // Should this be enabled globally? This will create a cert if it hasn't yet been created.

        Product product = this.fetchProduct(productUuid);
        return this.productCertCurator.getCertForProduct(product);
    }

    /**
     * @deprecated Use per-org version
     * @return Product
     */
    @ApiOperation(notes = "Creates a Product. Returns either the new created " +
        "Product or the Product that already existed. @deprecated Use per-org" +
        " version", value = "createProduct")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Deprecated
    public ProductData createProduct(ProductData product) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }

    /**
     * @deprecated Use per-org version
     * @return Product
     */
    @ApiOperation(notes = "Updates a Product @deprecated Use per-org version", value = "updateProduct")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Deprecated
    public ProductData updateProduct(
        @PathParam("product_uuid") String productUuid,
        @ApiParam(name = "product", required = true) ProductData product) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }


    /**
     * @deprecated Use per-org version
     * @return Product
     */
    @ApiOperation(notes = "Adds Content to a Product Batch mode @deprecated Use per-org version",
        value = "addBatchContent")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/batch_content")
    @Deprecated
    public ProductData addBatchContent(
        @PathParam("product_uuid") String productUuid,
        Map<String, Boolean> contentMap) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }

    /**
     * @deprecated Use per-org version
     * @return Product
     */
    @ApiOperation(notes = "Adds Content to a Product. Single mode @deprecated Use " +
        "per-org version", value = "addContent")
    @ApiResponses({  })
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/{product_uuid}/content/{content_id}")
    @Deprecated
    public ProductData addContent(
        @PathParam("product_uuid") String productUuid,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }

    /**
     * @deprecated Use per-org version
     */
    @ApiOperation(notes = "Removes Content from a Product @deprecated Use per-org version",
        value = "removeContent")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/content/{content_id}")
    @Deprecated
    public void removeContent(
        @PathParam("product_uuid") String productUuid,
        @PathParam("content_id") String contentId) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }

    /**
     * @deprecated Use per-org version
     */
    @ApiOperation(notes = "Removes a Product @deprecated Use per-org version", value = "deleteProduct")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}")
    @Deprecated
    public void deleteProduct(
        @PathParam("product_uuid") String productUuid) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are no longer supported."
        ));
    }

    @ApiOperation(notes = "Retrieves a list of Owners by Product", value = "getProductOwners",
        response = Owner.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("/owners")
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<Owner> getProductOwners(
        @ApiParam(value = "Multiple product UUIDs", required = true)
        @QueryParam("product") List<String> productUuids) {

        if (productUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        return this.ownerCurator.lookupOwnersWithProduct(productUuids);
    }

    @ApiOperation(notes = "Refreshes Pools by Product", value = "refreshPoolsForProduct")
    @PUT
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public JobDetail[] refreshPoolsForProduct(
        @ApiParam(value = "Multiple product UUIDs", required = true)
        @QueryParam("product") List<String> productUuids,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        if (productUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Ignoring refresh pools request due to standalone config.");
            return null;
        }

        // TODO:
        // Replace this with the commented out block below once the job scheduling is no longer performed
        // via PinsetterAsyncFilter
        ResultIterator<Owner> iterator = this.ownerCurator.lookupOwnersWithProduct(productUuids).iterate();
        List<JobDetail> details = new LinkedList<JobDetail>();
        while (iterator.hasNext()) {
            details.add(RefreshPoolsJob.forOwner(iterator.next(), lazyRegen));
        }
        iterator.close();

        return details.toArray(new JobDetail[0]);

        // final Boolean lazy = lazyRegen; // Necessary to deal with Java's limitations with closures
        // return this.ownerCurator.lookupOwnersWithProduct(productUuids).transform(
        //     new ElementTransform<Owner, JobDetail>() {
        //         @Override
        //         public JobDetail transform(Owner owner) {
        //             return RefreshPoolsJob.forOwner(owner, lazy);
        //         }
        //     }
        // );
    }
}

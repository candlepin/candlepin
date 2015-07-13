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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Statistic;
import org.candlepin.model.StatisticCurator;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.resource.util.ResourceDateParser;

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

/**
 * API Gateway into /product
 *
 * @version $Rev$
 */
@Path("/products")
public class ProductResource {

    private static Logger log = LoggerFactory.getLogger(ProductResource.class);
    private ProductCurator productCurator;
    private OwnerCurator ownerCurator;
    private ProductCertificateCurator productCertCurator;
    private StatisticCurator statisticCurator;
    private I18n i18n;

    @Inject
    public ProductResource(ProductCurator productCurator, OwnerCurator ownerCurator,
        ProductCertificateCurator productCertCurator, StatisticCurator statisticCurator,
        I18n i18n) {

        this.productCurator = productCurator;
        this.productCertCurator = productCertCurator;
        this.statisticCurator = statisticCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * Attempts to find a product with the specified ID. The Product returned will be the first
     * found for any owner. Subsequent calls to this method with the same product ID are not
     * guaranteed to return the same product.
     *
     * @param productId
     *  The ID of the product to find
     *
     * @throws NotFoundException
     *  if a product with the specified ID could not be found
     *
     * @return
     *  a product instance with the specified product ID
     */
    private Product findProduct(String productId) {
        Product product = null;

        for (Owner owner : this.ownerCurator.listAll()) {
            product = this.productCurator.lookupById(owner, productId);

            if (product != null) {
                break;
            }
        }

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with ID ''{0}'' could not be found.", productId)
            );
        }

        return product;
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
     * @param productId uuid of the product sought.
     * @return a Product object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public Product getProduct(@PathParam("product_id") String productId) {
        Product product = this.findProduct(productId);

        // Make sure the owner cannot be identified from the returned product
        // TODO: It may be better/cleaner to evict the object (and its related objects) from
        // Hibernate's session cache rather than creating a copy.
        Product censored = (Product) product.clone();
        censored.setOwner(null);

        return censored;
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
        @PathParam("product_id") String productId) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
    public Product createProduct(Product product) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @PathParam("product_id") String productId,
        Product product) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @PathParam("product_id") String productId) {

        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic product write operations are not supported."
        ));
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
        @Verify(Product.class) @PathParam("product_id") String productId,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {

        return this.getProductStats(productId, null, from, to, days);
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
        @Verify(Product.class) @PathParam("product_id") String productId,
        @PathParam("vtype") String valueType,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("days") String days) {

        Product product = this.findProduct(productId);

        List<Statistic> stats =  statisticCurator.getStatisticsByProduct(product.getOwner(),
             productId, valueType, ResourceDateParser.getFromDate(from, to, days),
             ResourceDateParser.parseDateString(to)
        );

        List<Statistic> censored = new LinkedList<Statistic>();

        for (Statistic src : stats) {
            Statistic dest = new Statistic(src.getEntryType(), src.getValueType(),
                src.getValueReference(), src.getValue(), null
            );

            censored.add(dest);
        }

        return censored;
    }

    /**
     * Retrieves a list of Owners by Product
     *
     * @return a list of Owner objects
     * @httpcode 200
     * @httpcode 400
     */
    @GET
    @Path("/owners")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Owner> getProductOwners(
        @Verify(Product.class) @QueryParam("product") List<String> productIds) {

        if (productIds.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        return this.ownerCurator.lookupOwnersWithProduct(productIds);
    }

    /**
     * Refreshes Pools by Product
     *
     * @param productIds
     * @param lazyRegen
     * @return a JobDetail object
     */
    @PUT
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public JobDetail[] refreshPoolsForProduct(
        @Verify(Product.class) @QueryParam("product") List<String> productIds,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        if (productIds.isEmpty()) {
            throw new BadRequestException(i18n.tr("No product IDs specified"));
        }

        List<Owner> owners = this.ownerCurator.lookupOwnersWithProduct(productIds);
        List<JobDetail> jobs = new LinkedList<JobDetail>();

        for (Owner owner : owners) {
            jobs.add(RefreshPoolsJob.forOwner(owner, lazyRegen));
        }

        return jobs.toArray(new JobDetail[0]);
    }
}

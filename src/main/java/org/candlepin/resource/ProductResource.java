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

import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductContent;
import org.candlepin.model.Statistic;
import org.candlepin.model.StatisticCurator;
import org.candlepin.pinsetter.tasks.RefreshPoolsForProductJob;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
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
@Path("/products")
public class ProductResource {

    private static Logger log = LoggerFactory.getLogger(ProductResource.class);
    private ProductServiceAdapter prodAdapter;
    private ContentCurator contentCurator;
    private StatisticCurator statisticCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    /**
     * default ctor
     *
     * @param prodAdapter
     *            Product Adapter used to interact with multiple services.
     */
    @Inject
    public ProductResource(ProductServiceAdapter prodAdapter,
                           StatisticCurator statisticCurator,
                           ContentCurator contentCurator,
                           OwnerCurator ownerCurator,
                           I18n i18n) {
        this.prodAdapter = prodAdapter;
        this.contentCurator = contentCurator;
        this.statisticCurator = statisticCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * Retrieves a list of Products
     *
     * @param productIds if specified, the list of product ids to return product info for
     * @return a list of Product objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list(@QueryParam("product") List<String> productIds) {
        return productIds.isEmpty() ?
            prodAdapter.getProducts() :
            prodAdapter.getProductsByIds(productIds);
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
     *   "reliesOn" : [ ],
     *   "href" : "/products/product_id",
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @param pid uuid of the product sought.
     * @return a Product object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public Product getProduct(@PathParam("product_uuid") String pid) {
        Product toReturn = prodAdapter.getProductById(pid);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("Product with UUID ''{0}'' could not be found.", pid));
    }

    /**
     * Retreives a Certificate for a Product
     *
     * @return a ProductCertificate object
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Path("/{product_uuid}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductCertificate getProductCertificate(
        @PathParam("product_uuid") String productId) {

        Product product = prodAdapter.getProductById(productId);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found.", productId));
        }

        return prodAdapter.getProductCertificate(product);
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
        return prodAdapter.createProduct(product);
    }

    /**
     * Updates a Product
     *
     * @return a Product object
     * @httpcode 400
     * @httpcode 200
     */
    @PUT
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Product updateProduct(
        @PathParam("product_uuid") @Verify(Product.class) String productId,
        Product product) {
        Product toUpdate = getProduct(productId);

        if (performProductUpdates(toUpdate, product)) {
            this.prodAdapter.mergeProduct(toUpdate);
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
    @Path("/{product_uuid}/batch_content")
    public Product addBatchContent(@PathParam("product_uuid") String pid,
                              Map<String, Boolean> contentMap) {
        Product product = prodAdapter.getProductById(pid);
        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            Content content = contentCurator.find(entry.getKey());
            ProductContent productContent = new ProductContent(product, content,
                entry.getValue());
            product.getProductContent().add(productContent);
        }
        return prodAdapter.getProductById((product.getId()));
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
    @Path("/{product_uuid}/content/{content_id}")
    public Product addContent(@PathParam("product_uuid") String pid,
                              @PathParam("content_id") String contentId,
                              @QueryParam("enabled") Boolean enabled) {
        Product product = prodAdapter.getProductById(pid);
        Content content = contentCurator.find(contentId);

        ProductContent productContent = new ProductContent(product, content, enabled);
        product.getProductContent().add(productContent);
        return prodAdapter.getProductById((product.getId()));
    }

    /**
     * Removes Content from a Product
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/content/{content_id}")
    public void removeContent(@PathParam("product_uuid") String pid,
                              @PathParam("content_id") String contentId) {
        prodAdapter.removeContent(pid, contentId);
    }

    /**
     * Adds a Reliance to a Product
     *
     * @return a Product object
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/reliance/{rely_product_id}")
    public Product addReliance(@PathParam("product_uuid") String pid,
                              @PathParam("rely_product_id") String relyId) {
        prodAdapter.addRely(pid, relyId);
        return prodAdapter.getProductById(pid);
    }

    /**
     * Deletes a Reliance from a Product
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/reliance/{rely_product_uuid}")
    public void removeReliance(@PathParam("product_uuid") String pid,
                              @PathParam("rely_product_uuid") String relyId) {
        prodAdapter.removeRely(pid, relyId);
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
    @Path("/{product_uuid}")
    public void deleteProduct(@PathParam("product_uuid") String pid) {
        Product product = prodAdapter.getProductById(pid);
        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found.", pid));
        }
        if (prodAdapter.productHasSubscriptions(product)) {
            throw new BadRequestException(
                i18n.tr("Product with UUID ''{0}'' cannot be deleted " +
                    "while subscriptions exist.", pid));
        }

        prodAdapter.deleteProduct(product);
    }

    /**
     * Retrieves a list of Statistics for a Product
     *
     * @return a list of Statistic objects
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("/{prod_id}/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getProductStats(@PathParam("prod_id") String id,
                            @QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("days") String days) {

        return statisticCurator.getStatisticsByProduct(id, null,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
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
    @Path("/{prod_id}/statistics/{vtype}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Statistic> getProductStats(@PathParam("prod_id") String id,
                            @PathParam("vtype") String valueType,
                            @QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("days") String days) {

        return statisticCurator.getStatisticsByProduct(id, valueType,
                                ResourceDateParser.getFromDate(from, to, days),
                                ResourceDateParser.parseDateString(to));
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
    public List<Owner> getActiveProductOwners(@QueryParam("product") String[] productIds) {
        List<String> ids = Arrays.asList(productIds);
        if (ids.isEmpty()) {
            throw new BadRequestException(i18n.tr("Must specify product ID."));
        }

        return ownerCurator.lookupOwnersByActiveProduct(ids);
    }

    /**
     * Refreshes Pools by Product
     *
     * @param pid
     * @param lazyRegen
     * @return a JobDetail object
     */
    @PUT
    @Path("/{product_uuid}/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public JobDetail refreshPoolsForProduct(
        @PathParam("product_uuid") String pid,
        @QueryParam("lazy_regen") @DefaultValue("true") Boolean lazyRegen) {

        Product product = prodAdapter.getProductById(pid);
        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found.", pid));
        }

        return RefreshPoolsForProductJob.forProduct(product, lazyRegen);
    }
}

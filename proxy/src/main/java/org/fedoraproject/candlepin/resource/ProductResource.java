/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.auth.interceptor.SecurityHole;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCertificate;
import org.fedoraproject.candlepin.model.ProductCertificateCurator;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.Statistic;
import org.fedoraproject.candlepin.model.StatisticCurator;
import org.fedoraproject.candlepin.resource.util.ResourceDateParser;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

    private ProductServiceAdapter prodAdapter;
    private ContentCurator contentCurator;
    private StatisticCurator statisticCurator;
    private I18n i18n;

    /**
     * default ctor
     *
     * @param prodAdapter
     *            Product Adapter used to interact with multiple services.
     */
    @Inject
    public ProductResource(ProductServiceAdapter prodAdapter,
                           ProductCertificateCurator productCertCurator,
                           StatisticCurator statisticCurator,
                           ContentCurator contentCurator,
                           I18n i18n) {
        this.prodAdapter = prodAdapter;
        this.contentCurator = contentCurator;
        this.statisticCurator = statisticCurator;
        this.i18n = i18n;
    }

    /**
     * returns the list of Products available.
     *
     * @return the list of available products.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> list() {
        return prodAdapter.getProducts();
    }

    /**
     * Return the Product identified by the given uuid.
     *
     * @param pid
     *            uuid of the product sought.
     * @return the product identified by the given uuid.
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
            i18n.tr("Product with UUID ''{0}'' could not be found", pid));
    }

    @GET
    @Path("/{product_uuid}/certificate")
    @Produces(MediaType.APPLICATION_JSON)
    @SecurityHole
    public ProductCertificate getProductCertificate(
        @PathParam("product_uuid") String productId) {

        Product product = prodAdapter.getProductById(productId);

        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found", productId));
        }

        return prodAdapter.getProductCertificate(product);
    }

    /**
     *
     * @param product
     * @return the newly created product, or the product that already
     *         exists
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Product createProduct(Product product) {
        return prodAdapter.createProduct(product);
    }

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
        return prodAdapter.createProduct(product);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}/content/{content_id}")
    public void removeContent(@PathParam("product_uuid") String pid,
                              @PathParam("content_id") String contentId) {
        prodAdapter.removeContent(pid, contentId);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{product_uuid}")
    public void deleteProduct(@PathParam("product_uuid") String pid) {
        Product product = prodAdapter.getProductById(pid);
        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID ''{0}'' could not be found", pid));
        }
        if (prodAdapter.productHasSubscriptions(product)) {
            throw new BadRequestException(
                i18n.tr("Product with UUID ''{0}'' cannot be deleted " +
                    "while subscriptions exist.", pid));
        }

        prodAdapter.deleteProduct(product);
    }

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
}

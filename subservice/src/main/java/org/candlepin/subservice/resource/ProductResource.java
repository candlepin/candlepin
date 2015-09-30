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
package org.candlepin.subservice.resource;

import org.candlepin.model.dto.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;



/**
 * The ProductResource class handles the product-oriented API calls for the subscription
 * service.
 */
@Path("/products")
public class ProductResource {
    private static Logger log = LoggerFactory.getLogger(ProductResource.class);

    // Things we need:
    // Backing curator
    // Translation service (I18n)
    // Authentication

    /**
     * Creates a new product from the product JSON provided. Any UUID provided in the JSON
     * will be ignored when creating the new product.
     *
     * @param product
     *  A Product object built from the JSON provided in the request
     *
     * @return
     *  The newly created Product object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Product createProduct(Product product) {
        // TODO
        log.error("createProduct:"+product);
        return product;
    }

    /**
     * Lists all known products currently maintained by the subscription service.
     *
     * @return
     *  A collection of products maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Set<Product> listProducts() {
        // TODO
        Set<Product> products = new HashSet<Product>();
        products.add(new Product());
        log.error("listProducts");
        return products;
    }

    /**
     * Retrieves the product for the specified product UUID. If the product UUID
     * cannot be found, this method returns null.
     *
     * @param productUuid
     *  The UUID of the product to retrieve
     *
     * @return
     *  The requested Product object, or null if the product could not be found
     */
    @GET
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Product getProduct(@PathParam("product_uuid") String productUuid) {
        // TODO
        log.error("getProduct:"+productUuid);
        return new Product();
    }

    /**
     * Updates the specified product with the provided product data.
     *
     * @param productUuid
     *  The UUID of the product to update
     *
     * @param product
     *  A Product object built from the JSON provided in the request; contains the data to use
     *  to update the specified product
     *
     * @return
     *  The updated Product object
     */
    @PUT
    @Path("{product_uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Product updateProduct(@PathParam("product_uuid") String productUuid,
        Product product) {
        // TODO
        log.error("updateProduct: uuid:{} product:{}", productUuid, product);
        return product;
    }

    /**
     * Deletes the specified product.
     *
     * @param productUuid
     *  The UUID of the product to delete
     *
     * @return
     *  True if the product was deleted successfully; false otherwise
     */
    @DELETE
    @Path("/{product_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteProduct(@PathParam("product_uuid") String productUuid) {
        // TODO
        log.error("deleteProduct: uuid:{}", productUuid);
        return false;
    }

}

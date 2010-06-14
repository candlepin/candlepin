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

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCertificate;
import org.fedoraproject.candlepin.model.ProductCertificateCurator;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * API Gateway into /product
 * 
 * @version $Rev$
 */
@Path("/products")
public class ProductResource {

    private ProductServiceAdapter prodAdapter;
    private ContentCurator contentCurator;
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
                           ContentCurator contentCurator,
                           I18n i18n) {
        this.prodAdapter = prodAdapter;
        this.contentCurator = contentCurator;
        this.i18n = i18n;
    }

    /**
     * returns the list of Products available.
     * 
     * @return the list of available products.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
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
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Product getProduct(@PathParam("product_uuid") String pid) {
        Product toReturn = prodAdapter.getProductById(pid);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("Product with UUID '{0}' could not be found", pid));
    }
    
    @GET
    @Path("/{product_uuid}/certificate")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ProductCertificate getProductCertificate(
        @PathParam("product_uuid") String productId) {
        
        Product product = prodAdapter.getProductById(productId);
        
        if (product == null) {
            throw new NotFoundException(
                i18n.tr("Product with UUID '{0}' could not be found", productId));
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
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @AllowRoles(roles = {Role.SUPER_ADMIN})
    public Product createProduct(Product product, 
        @QueryParam("childId") List<String> childIds) {
        //TODO: Do the bulk lookup in the product adapter?
        if (childIds != null) {
            for (String childId : childIds) {
                Product child = prodAdapter.getProductById(childId);
                product.addChildProduct(child);
            }
        }
        
        return prodAdapter.createProduct(product);
    }   
    
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @AllowRoles(roles = {Role.SUPER_ADMIN})
    @Path("/{product_uuid}/content/{content_id}")
    public Product addContent(@PathParam("product_uuid") String pid,
                              @PathParam("content_id") Long contentId, 
                              @QueryParam("enabled") Boolean enabled) {
        Product product = prodAdapter.getProductById(pid);
        Content content = contentCurator.find(contentId);
        
        ProductContent productContent = new ProductContent(content, product, enabled);
        product.getProductContent().add(productContent);
        return prodAdapter.createProduct(product);
        
    }
    
}

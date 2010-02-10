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

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.product.ProductServiceAdapter;

import com.google.inject.Inject;


/**
 * API Gateway into /product
 * @version $Rev$
 */
@Path("/product")
public class ProductResource {

    private static Logger log = Logger.getLogger(ProductResource.class);
    private ProductServiceAdapter prodAdapter;


    /**
     * default ctor
     */
    @Inject
    public ProductResource(ProductServiceAdapter prodAdapter) {
        this.prodAdapter = prodAdapter;
    }
    
    
    /**
     * returns the list of Products available.
     * @return the list of available products.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Product> list() {
        return prodAdapter.getProducts() ;
    }
}

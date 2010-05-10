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
package org.fedoraproject.candlepin.service.impl;

import java.util.List;


import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

/**
 * Default implementation of the ProductserviceAdapter.
 */
public class DefaultProductServiceAdapter implements ProductServiceAdapter {
    private ProductCurator prodCurator;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator prodCurator) {
        this.prodCurator = prodCurator;
    }

    @Override
    public Product getProductById(String id) {
        return prodCurator.lookupById(id);
    }
    
    
    @Override
    public List<Product> getProducts() {
        return prodCurator.listAll();
    }

    @Override
    public Boolean provides(String productId, String providesProductId) {
        Product p = getProductById(productId);
        Product queried = getProductById(providesProductId);
        if ((p == null) || (p.getChildProducts() == null)) {
            return Boolean.FALSE;
        }
        if (p.getChildProducts().contains(queried)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

}

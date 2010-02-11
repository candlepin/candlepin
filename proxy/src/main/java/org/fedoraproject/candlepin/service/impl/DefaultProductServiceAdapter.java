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

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

import java.util.List;

public class DefaultProductServiceAdapter implements ProductServiceAdapter {

    private ProductCurator prodCurator;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator prodCurator) {
        this.prodCurator = prodCurator;
    }

    @Override
    public Product getProductById(String id) {
        return prodCurator.lookupByLabel(id);
    }
    
    @Override
    public Product getProductByLabel(String label) {
        return prodCurator.lookupByLabel(label);
    }    

    @Override
    public List<Product> getProducts() {
        return prodCurator.findAll();
    }

}

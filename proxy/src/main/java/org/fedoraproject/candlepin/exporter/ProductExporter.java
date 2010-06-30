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
package org.fedoraproject.candlepin.exporter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;

/**
 * ProductExporter
 */
public class ProductExporter {
    
    private class ProductDto {
        private Product product;
        
        ProductDto(Product product) {
            this.product = product;
        }
        
        public String getId() {
            return product.getId();
        }
        
        public Long getMultiplier() {
            return product.getMultiplier();
        }
        
        public Set<Attribute> getAttributes() {
            return product.getAttributes();
        }

        public Set<Long> getContent() {
            Set<Long> contentIds = new HashSet<Long>();
            for (Content content : product.getContent()) {
                contentIds.add(content.getId());
            }
            return contentIds;
        }
        
    }

    public void export(ObjectMapper mapper, FileWriter writer, Product product)
        throws IOException {
        mapper.writeValue(writer, new ProductDto(product));
    }

}

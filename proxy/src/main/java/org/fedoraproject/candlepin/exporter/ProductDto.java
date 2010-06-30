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

import java.util.HashSet;
import java.util.Set;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;

/**
 * ProductDto
 */
public class ProductDto {
    private String id;
    private String name;
    private Long multiplier;
    private Set<Attribute> attributes;
    private Set<Long> content;
    
    ProductDto(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.multiplier = product.getMultiplier();
        this.attributes = product.getAttributes();
        this.content = getContent(product);
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Long getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(Long multipler) {
        this.multiplier = multipler;
    }
    
    public Set<Attribute> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Set<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Set<Long> getContent(Product product) {
        Set<Long> contentIds = new HashSet<Long>();
        for (Content content : product.getContent()) {
            contentIds.add(content.getId());
        }
        return contentIds;
    }
    
    public Set<Long> getContent() {
        return content;
    }
    
    public void setContent(Set<Long> content) {
        this.content = content;
    }
    
    public Product product() {
        Product toReturn = new Product(id, name, multiplier);
        toReturn.setAttributes(attributes);
        
        // TODO: handle content
        
        return toReturn;
    }
}

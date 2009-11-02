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
package org.fedoraproject.candlepin.model;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a Product that can be consumed and entitled.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Product extends BaseModel {

    private String label;
    private List<Product> childProducts;

    /**
     * Create product with UUID
     * @param uuid unique id for the product
     */
    public Product(String uuid) {
        super(uuid);
    }

    /**
     * Default constructor
     */
    public Product() {
    }

    /**
     * @return the childProducts
     */
    public List<Product> getChildProducts() {
        return childProducts;
    }

    /**
     * @param childProducts the childProducts to set
     */
    public void setChildProducts(List<Product> childProducts) {
        this.childProducts = childProducts;
    }

    /**
     * Add a child of this Product.
     * @param p to add
     */
    public void addChildProduct(Product p) {
        if (this.childProducts == null) {
            this.childProducts = new LinkedList<Product>();
        }
        this.childProducts.add(p);
    }
    
    /** 
     * Get the list of compatible consumer types
     * @return list of compatible consumer types
     */
    public List<String> getCompatibleConsumerTypes() {
        
        return null;
    }

    
    /**
     * @return Returns the label.
     */
    public String getLabel() {
        return label;
    }

    
    /**
     * @param labelIn The label to set.
     */
    public void setLabel(String labelIn) {
        label = labelIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Product [label=" + label + "]";
    }
}

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

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
//import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Parent;

/**
 * ProductContent
 */
@Embeddable
public class ProductContent extends AbstractHibernateObject implements
    AccessControlEnforced {

    @Parent
    private Product product;
    
    @ManyToOne
    @JoinColumn(name = "content_id", nullable = false, updatable = false)   
    private Content content;
    
    private Boolean enabled;
    private Integer flexEntitlement = 0;
    private Integer physicalEntitlement = 0;
    
    public ProductContent() {
        
    }
    
    public ProductContent(Product product, Content content, Boolean enabled) {
        this.setContent(content);
        this.setProduct(product);
        this.setEnabled(enabled);
    }
    
    public ProductContent(Product product, Content content, Boolean enabled,
            Integer flexEntitlement, Integer physicalEntitlement) {
        this.setContent(content);
        this.setProduct(product);
        this.setEnabled(enabled);
        this.flexEntitlement = flexEntitlement;
        this.physicalEntitlement = physicalEntitlement;
    }
    
    @Override
    public boolean shouldGrantAccessTo(Owner owner) {
        // TODO Auto-generated method stub
        return false;
    }

    
    @Override
    public boolean shouldGrantAccessTo(Consumer consumer) {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.model.Persisted#getId()
     */
    @Override
    public Serializable getId() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param content the content to set
     */
    public void setContent(Content content) {
        this.content = content;
    }

    /**
     * @return the content
     */
    public Content getContent() {
        return content;
    }

    /**
     * @param product the product to set
     */
    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * @return the product
     */
    public Product getProduct() {
        return product;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the enabled
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param physicalEntitlement the physicalEntitlement to set
     */
    public void setPhysicalEntitlement(Integer physicalEntitlement) {
        this.physicalEntitlement = physicalEntitlement;
    }

    /**
     * @return the physicalEntitlement
     */
    public Integer getPhysicalEntitlement() {
        return physicalEntitlement;
    }

    /**
     * @param flexEntitlement the flexEntitlement to set
     */
    public void setFlexEntitlement(Integer flexEntitlement) {
        this.flexEntitlement = flexEntitlement;
    }

    /**
     * @return the flexEntitlement
     */
    public Integer getFlexEntitlement() {
        return flexEntitlement;
    }
    

}

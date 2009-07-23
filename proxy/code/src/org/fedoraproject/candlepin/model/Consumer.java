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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Consumer extends BaseModel {

    
    private Owner owner;
    private Consumer parent;
    private List<Product> consumedProducts;
    private ConsumerInfo info;
    
    /**
     * default ctor
     */
    public Consumer() {
        this(null);
        this.info = new ConsumerInfo();
        this.info.setParent(this);
    }

    /**
     * @param uuid
     */
    public Consumer(String uuid) {
        super(uuid);
        this.info = new ConsumerInfo();
        this.info.setParent(this);
    }

    /**
     * @return the type
     */
    public String getType() {
        if (this.info == null) {
            return null;
        }
        else {
            return info.getType();
        }
    }

    /**
     * Set the type of this Consumer.  
     * @param typeIn to set
     */
    public void setType(String typeIn) {
        if (this.info == null) {
            this.info = new ConsumerInfo();
        }
        this.info.setType(typeIn);
    }
    
    /**
     * @return the parent
     */
    public Consumer getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(Consumer parent) {
        this.parent = parent;
    }

    /**
     * @return the consumedProducts
     */
    public List<Product> getConsumedProducts() {
        return consumedProducts;
    }

    /**
     * @param consumedProducts the consumedProducts to set
     */
    public void setConsumedProducts(List<Product> consumedProducts) {
        this.consumedProducts = consumedProducts;
    }

    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Add a Product to this Consumer.
     * 
     */
    public void addConsumedProduct(Product p) {
        if (this.consumedProducts == null) {
            this.consumedProducts = new LinkedList<Product>();
        }
        this.consumedProducts.add(p);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Consumer [type=" + this.getType() + ", getName()=" + getName()
                + ", getUuid()=" + getUuid() + "]";
    }

    
    /**
     * @return Returns the info.
     */
    public ConsumerInfo getInfo() {
        return info;
    }

    
    /**
     * @param infoIn The info to set.
     */
    public void setInfo(ConsumerInfo infoIn) {
        info = infoIn;
    }
    
    /**
     * Set a metadata field
     * @param name to set
     * @param value to set
     */
    public void setMetadataField(String name, String value) {
        if (this.getInfo().getMetadata() == null) {
            this.getInfo().setMetadata(new HashMap());
        }
        this.getInfo().getMetadata().put(name, value);
    }
    
    /**
     * Get a metadata field value
     * @param name of field to fetch
     * @return String field value.
     */
    public String getMetadataField(String name) {
       if (this.getInfo().getMetadata() != null) {
           return getInfo().getMetadata().get(name);
       }
       return null;
    }


}

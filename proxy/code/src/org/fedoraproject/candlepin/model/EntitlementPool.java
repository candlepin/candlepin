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

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementPool extends BaseModel {

    private Owner owner;
    private Product product;
    private long maxMembers;
    private long currentMembers;

    private Date startDate;
    private Date endDate;

    /**
     * @param uuid unique id of the pool
     */
    public EntitlementPool(String uuid) {
        super(uuid);
    }

    /**
     * Default const
     */
    public EntitlementPool() {

    }

    /**
     * @return the product
     */
    public Product getProduct() {
        return product;
    }

    /**
     * @param product the product to set
     */
    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * @return the startDate
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return the maxMembers
     */
    public long getMaxMembers() {
        return maxMembers;
    }

    /**
     * @param maxMembers the maxMembers to set
     */
    public void setMaxMembers(long maxMembers) {
        this.maxMembers = maxMembers;
    }

    /**
     * @return the currentMembers
     */
    public long getCurrentMembers() {
        return currentMembers;
    }

    /**
     * @param currentMembers the currentMembers to set
     */
    public void setCurrentMembers(long currentMembers) {
        this.currentMembers = currentMembers;
    }

    /**
     * @return the owner
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    @XmlTransient
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Add 1 to the current members.
     */
    public void bumpCurrentMembers() {
        this.currentMembers = this.currentMembers + 1;
    }

}

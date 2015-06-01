/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.gutterball.model.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;



/**
 * An abstract base class for product compliance status references
 */
@MappedSuperclass
public abstract class AbstractProductReference {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @ManyToOne
    @JoinColumn(name="comp_status_id")
    @NotNull
    private ComplianceStatus complianceStatus;

    @Column(name = "product_id")
    @NotNull
    private String productId;

    /**
     * Creates a new product reference instance with no compliance status association and no product
     * ID.
     */
    public AbstractProductReference() {
        // Intentionally left empty
    }

    /**
     * Creates a new product reference instance with the specified compliance status and product ID.
     *
     * @param status
     *  The ComplianceStatus to associate with this product reference
     *
     * @param productId
     *  The ID of the product to be referenced by this product reference
     */
    public AbstractProductReference(ComplianceStatus status, String productId) {
        this();

        this.setComplianceStatus(status);
        this.setProductId(productId);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the ComplianceStatus with which this product reference is to be associated.
     *
     * @param status
     *  The ComplianceStatus to associate with this product reference
     *
     * @return
     *  this product reference instance
     */
    public AbstractProductReference setComplianceStatus(ComplianceStatus status) {
        this.complianceStatus = status;
        return this;
    }

    /**
     * Retrieves the compliance status with which this product reference is associated. If this
     * product reference is not associated with a compliance status, this method returns null.
     *
     * @return
     *  the compliance status with which this product reference is associated, or null if it is
     *  not associated with a compliance status
     */
    public ComplianceStatus getComplianceStatus() {
        return this.complianceStatus;
    }

    /**
     * Sets the ID of the product to be referenced by this product reference.
     *
     * @param productId
     *  The ID of the product to be referenced by this product reference
     *
     * @return
     *  this product reference instance
     */
    public AbstractProductReference setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    /**
     * Retrieves the ID for the product referenced by this product reference. If a product ID has
     * not been set, this method returns null.
     *
     * @return
     *  the ID of the product referenced by this product reference, or null if an ID has not been
     *  set
     */
    public String getProductId() {
        return this.productId;
    }

}

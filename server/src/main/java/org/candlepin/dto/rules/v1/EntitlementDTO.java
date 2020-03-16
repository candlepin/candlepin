/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.CandlepinDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * A DTO representation of the Entitlement entity
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementDTO extends CandlepinDTO<EntitlementDTO> {

    private static final long serialVersionUID = 1L;

    private String id;
    private PoolDTO pool;
    private Integer quantity;
    private Date startDate;
    private Date endDate;

    /**
     * Initializes a new EntitlementDTO instance with null values.
     */
    public EntitlementDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new EntitlementDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public EntitlementDTO(EntitlementDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this EntitlementDTO object.
     *
     * @return the id field of this EntitlementDTO object.
     */
    @HateoasInclude
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this EntitlementDTO object.
     *
     * @param id the id to set on this EntitlementDTO object.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the pool of this entitlement.
     *
     * @return the pool of this entitlement.
     */
    public PoolDTO getPool() {
        return this.pool;
    }

    /**
     * Sets the pool of this entitlement.
     *
     * @param pool the pool to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setPool(PoolDTO pool) {
        this.pool = pool;
        return this;
    }

    /**
     * Retrieves the quantity of this entitlement.
     *
     * @return the quantity of this entitlement.
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity of this entitlement.
     *
     * @param quantity the quantity to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * Returns the start date of this entitlement.
     *
     * @return Returns the startDate from the pool of this entitlement.
     */
    @JsonProperty
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of this entitlement.
     *
     * @param startDate the startDate of this entitlement.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonIgnore
    public EntitlementDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Returns the end date of this entitlement.
     *
     * @return Returns the endDate of this entitlement.
     */
    @JsonProperty
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date of this entitlement.
     *
     * @param endDate the endDate of this entitlement.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonIgnore
    public EntitlementDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EntitlementDTO [id: %s, pool id: %s, quantity: %d]",
            this.getId(), this.getPool() != null ? this.getPool().getId() : null, this.getQuantity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EntitlementDTO) {
            EntitlementDTO that = (EntitlementDTO) obj;

            // Pull the nested object IDs, as we're not interested in verifying that the objects
            // themselves are equal; just so long as they point to the same object.
            String thisPoolId = this.getPool() != null ? this.getPool().getId() : null;
            String thatPoolId = that.getPool() != null ? that.getPool().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(thisPoolId, thatPoolId)
                .append(this.getQuantity(), that.getQuantity())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getStartDate(), that.getStartDate());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(this.getPool() != null ? this.getPool().getId() : null)
            .append(this.getQuantity())
            .append(this.getEndDate())
            .append(this.getStartDate());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO clone() {
        EntitlementDTO copy = super.clone();

        PoolDTO pool = this.getPool();
        copy.setPool(pool != null ? pool.clone() : null);

        Date startDate = this.getStartDate();
        copy.setStartDate(startDate != null ? (Date) startDate.clone() : null);

        Date endDate = this.getEndDate();
        copy.setEndDate(endDate != null ? (Date) endDate.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(EntitlementDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setPool(source.getPool())
            .setQuantity(source.getQuantity())
            .setEndDate(source.getEndDate())
            .setStartDate(source.getStartDate());

        return this;
    }
}

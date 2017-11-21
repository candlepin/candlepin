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
package org.candlepin.dto.api.v1;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import io.swagger.annotations.ApiModel;

import java.util.Date;

/**
 * A DTO representation of the ConsumerInstalledProduct entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a consumer installed " +
    "product")
public class ConsumerInstalledProductDTO extends TimestampedCandlepinDTO<ConsumerInstalledProductDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String productId;
    protected String productName;
    protected String version;
    protected String arch;
    protected String status;
    protected Date startDate;
    protected Date endDate;

    /**
     * Initializes a new ConsumerInstalledProductDTO instance with null values.
     */
    public ConsumerInstalledProductDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ConsumerInstalledProductDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ConsumerInstalledProductDTO(ConsumerInstalledProductDTO source) {
        super(source);
    }

    // Helper constructor for tests:
    public ConsumerInstalledProductDTO(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
    }

    /**
     * Retrieves the id field of this ConsumerInstalledProduct object.
     *
     * @return the id of the installedProduct, or null if the id has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this ConsumerInstalledProductDTO object.
     *
     * @param id the id to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the productid field of this ConsumerInstalledProduct object.
     *
     * @return the productId of the installedProduct, or null if the id has not yet been defined
     */
    public String getProductId() {
        return this.productId;
    }

    /**
     * Sets the productid on this ConsumerInstalledProductDTO object.
     *
     * @param productId the productid to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    /**
     * Retrieves the productName field of this ConsumerInstalledProduct object.
     *
     * @return the productName of the installedProduct, or null if the id has not yet been defined
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Sets the product name to set on this ConsumerInstalledProductDTO object.
     *
     * @param productName the product name to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setProductName(String productName) {
        this.productName = productName;
        return this;
    }

    /**
     * Retrieves the version field of this ConsumerInstalledProduct object.
     *
     * @return the version of the installedProduct, or null if the id has not yet been defined
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * Sets the version to set on this ConsumerInstalledProductDTO object.
     *
     * @param version the version to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Retrieves the arch field of this ConsumerInstalledProduct object.
     *
     * @return the arch of the installedProduct, or null if the id has not yet been defined
     */
    public String getArch() {
        return arch;
    }

    /**
     * Sets the arch to set on this ConsumerInstalledProductDTO object.
     *
     * @param arch the arch to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setArch(String arch) {
        this.arch = arch;
        return this;
    }

    /**
     * Retrieves the status field of this ConsumerInstalledProduct object.
     *
     * @return the status of the installedProduct, or null if the id has not yet been defined
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status to set on this ConsumerInstalledProductDTO object.
     *
     * @param status the status to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Retrieves the startDate field of this ConsumerInstalledProduct object.
     *
     * @return the start date of the installedProduct, or null if the id has not yet been defined
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the startDate to set on this ConsumerInstalledProductDTO object.
     *
     * @param startDate the startDate to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Retrieves the endDate field of this ConsumerInstalledProduct object.
     *
     * @return the end date of the installedProduct, or null if the id has not yet been defined
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the endDate to set on this ConsumerInstalledProductDTO object.
     *
     * @param endDate the endDate to set on this ConsumerInstalledProductDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerInstalledProductDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
            "ConsumerInstalledProductDTO [id: %s, productId: %s, productName: %s]",
            this.getId(), this.getProductId(), this.getProductName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ConsumerInstalledProductDTO && super.equals(obj)) {
            ConsumerInstalledProductDTO that = (ConsumerInstalledProductDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getProductId(), that.getProductId())
                .append(this.getProductName(), that.getProductName())
                .append(this.getVersion(), that.getVersion())
                .append(this.getArch(), that.getArch())
                .append(this.getStatus(), that.getStatus())
                .append(this.getStartDate(), that.getStartDate())
                .append(this.getEndDate(), that.getEndDate());

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
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getProductId())
            .append(this.getProductName())
            .append(this.getVersion())
            .append(this.getArch())
            .append(this.getStatus())
            .append(this.getStartDate())
            .append(this.getEndDate());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO clone() {
        ConsumerInstalledProductDTO copy = super.clone();
        Date startDate = this.getStartDate();
        copy.startDate = startDate != null ? (Date) startDate.clone() : null;
        Date endDate = this.getEndDate();
        copy.endDate = endDate != null ? (Date) endDate.clone() : null;

        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO populate(ConsumerInstalledProductDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setProductId(source.getProductId());
        this.setProductName(source.getProductName());
        this.setVersion(source.getVersion());
        this.setArch(source.getArch());
        this.setStatus(source.getStatus());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());

        return this;
    }
}

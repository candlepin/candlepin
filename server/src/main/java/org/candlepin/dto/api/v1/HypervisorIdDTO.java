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

/**
 * A DTO representation of the HypervisorId entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a HypervisorId entity")
public class HypervisorIdDTO extends TimestampedCandlepinDTO<HypervisorIdDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String hypervisorId;
    protected String reporterId;

    /**
     * Initializes a new HypervisorIdDTO instance with null values.
     */
    public HypervisorIdDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new HypervisorIdDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public HypervisorIdDTO(HypervisorIdDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this HypervisorIdDTO object.
     *
     * @return the id field of this HypervisorIdDTO object.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this HypervisorIdDTO object.
     *
     * @param id the id to set on this HypervisorIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorIdDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the hypervisorId field of this HypervisorIdDTO object.
     *
     * @return the hypervisorId field of this HypervisorIdDTO object.
     */
    public String getHypervisorId() {
        return this.hypervisorId;
    }

    /**
     * Sets the hypervisorID to set on this HypervisorIdDTO object.
     *
     * @param hypervisorId the hypervisorId to set on this HypervisorIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorIdDTO setHypervisorId(String hypervisorId) {
        this.hypervisorId = hypervisorId;
        return this;
    }

    /**
     * Retrieves the reporterId field of this HypervisorIdDTO object.
     *
     * @return the reporterId field of this HypervisorIdDTO object.
     */
    public String getReporterId() {
        return this.reporterId;
    }

    /**
     * Sets the reporterId to set on this HypervisorIdDTO object.
     *
     * @param reporterId the reporterId to set on this HypervisorIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorIdDTO setReporterId(String reporterId) {
        this.reporterId = reporterId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
            "HypervisorIdDTO [id: %s, hypervisorId: %s, reporterId: %s]",
            this.getId(), this.getHypervisorId(), this.getReporterId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof HypervisorIdDTO && super.equals(obj)) {
            HypervisorIdDTO that = (HypervisorIdDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getHypervisorId(), that.getHypervisorId())
                .append(this.getReporterId(), that.getReporterId());

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
            .append(this.getHypervisorId())
            .append(this.getReporterId());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO clone() {
        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO populate(HypervisorIdDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setHypervisorId(source.getHypervisorId());
        this.setReporterId(source.getReporterId());

        return this;
    }
}

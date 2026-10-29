/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Mutable consumertype data for version 2 manifests
 */
public class ConsumerTypeDTO extends TimestampedCandlepinDTO<ConsumerTypeDTO> {

    public static final long serialVersionUID = 1L;

    private String id;
    private String label;

    /**
     * Initializes an empty ConsumerTypeDTO
     */
    public ConsumerTypeDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public ConsumerTypeDTO(ConsumerTypeDTO source) {
        super(source);
    }

    /**
     * Retrieves the id
     *
     * @return the id, or null if unset
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id
     *
     * @param id
     *     the id, or null to clear it
     * @return this DTO
     */
    public ConsumerTypeDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the label
     *
     * @return the label, or null if unset
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the label
     *
     * @param label
     *     the label, or null to clear it
     * @return this DTO
     */
    public ConsumerTypeDTO setLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ConsumerTypeDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getId(), that.getId())
            .append(this.getLabel(), that.getLabel())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getLabel())
            .toHashCode();
    }

    @Override
    public ConsumerTypeDTO populate(ConsumerTypeDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setLabel(source.getLabel());

        return this;
    }
}

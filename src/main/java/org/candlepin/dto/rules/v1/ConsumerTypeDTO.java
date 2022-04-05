/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.dto.CandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;



/**
 * A DTO representation of the ConsumerType entity as used by the Rules framework.
 */
public class ConsumerTypeDTO extends CandlepinDTO<ConsumerTypeDTO> {
    public static final long serialVersionUID = 1L;

    protected String label;
    protected Boolean manifest;

    /**
     * Initializes a new ConsumerTypeDTO instance with null values.
     */
    public ConsumerTypeDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ConsumerTypeDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ConsumerTypeDTO(ConsumerTypeDTO source) {
        super(source);
    }

    public String getLabel() {
        return this.label;
    }

    public ConsumerTypeDTO setLabel(String label) {
        this.label = label;
        return this;
    }

    public Boolean isManifest() {
        return this.manifest;
    }

    public ConsumerTypeDTO setManifest(Boolean manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ConsumerTypeDTO [label: %s, manifest: %b]",
            this.getLabel(), this.isManifest());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ConsumerTypeDTO) {
            ConsumerTypeDTO that = (ConsumerTypeDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getLabel(), that.getLabel())
                .append(this.isManifest(), that.isManifest());

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
            .append(this.getLabel())
            .append(this.isManifest());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerTypeDTO populate(ConsumerTypeDTO source) {
        super.populate(source);

        this.setLabel(source.getLabel());
        this.setManifest(source.isManifest());

        return this;
    }
}

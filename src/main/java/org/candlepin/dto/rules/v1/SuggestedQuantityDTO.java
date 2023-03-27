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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.CandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * SuggestedQuantity: Just used to transfer a couple values back from the quantity rules
 * namespace in just one invocation.
 */
public class SuggestedQuantityDTO extends CandlepinDTO<SuggestedQuantityDTO> {

    private Long suggested;
    private Long increment;

    /**
     * Initializes a new SuggestedQuantityDTO instance with null values.
     */
    public SuggestedQuantityDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new SuggestedQuantityDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public SuggestedQuantityDTO(SuggestedQuantityDTO source) {
        super(source);
    }

    public Long getSuggested() {
        return suggested;
    }

    public void setSuggested(Long suggested) {
        this.suggested = suggested;
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("SuggestedQuantityDTO [suggested: %s, increment: %s]",
                this.getSuggested(), this.getIncrement());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof SuggestedQuantityDTO) {
            SuggestedQuantityDTO that = (SuggestedQuantityDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getSuggested(), that.getSuggested())
                .append(this.getIncrement(), that.getIncrement());

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
            .append(this.getSuggested())
            .append(this.getIncrement());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SuggestedQuantityDTO clone() {
        SuggestedQuantityDTO copy = super.clone();

        copy.setSuggested(this.getSuggested());
        copy.setIncrement(this.getIncrement());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SuggestedQuantityDTO populate(SuggestedQuantityDTO source) {
        super.populate(source);

        this.setIncrement(source.getIncrement());
        this.setSuggested(source.getSuggested());

        return this;
    }
}

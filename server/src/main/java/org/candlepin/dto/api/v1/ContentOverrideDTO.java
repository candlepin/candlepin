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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * The ContentOverrideDTO is used as a DTO for the ContentOverride class and the basis for its
 * subclasses.
 */
public class ContentOverrideDTO extends TimestampedCandlepinDTO<ContentOverrideDTO> {

    public static final long serialVersionUID = 1L;

    protected String contentLabel;
    protected String name;
    protected String value;

    /**
     * Initializes a new ContentOverrideDTO instance with null values.
     */
    public ContentOverrideDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ContentOverrideDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ContentOverrideDTO(ContentOverrideDTO source) {
        super(source);
    }

    /**
     * Fetches the label of the content being overridden. If the label has not been set, this method
     * returns null.
     *
     * @return
     *  The label of the content being overriden, or null if the label has not been set
     */
    public String getContentLabel() {
        return this.contentLabel;
    }

    /**
     * Sets or clears the label of the content being overridden. If the provided label is null, any
     * label currently set will be cleared.
     *
     * @param label
     *  The label of the content to override, or null to clear the label
     *
     * @return
     *  A reference to this DTO
     */
    public ContentOverrideDTO setContentLabel(String label) {
        this.contentLabel = label;
        return this;
    }

    /**
     * Fetches the name this content override. If the name has not been set, this method returns
     * null.
     *
     * @return
     *  The name of this content override, or null if the name has not been set
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets or clears the name of this content property to override. If the provided name is null,
     * any property name currently set will be cleared.
     *
     * @param name
     *  The name of the content property to override, or null clear the name
     *
     * @return
     *  A reference to this DTO
     */
    public ContentOverrideDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Fetches the value of the overridden content property. If the value has not been set, this
     * method returns null.
     *
     * @return
     *  The value of the overriden content property, or null if the value has not been set
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets or clears the value of the overridden content property. If the provided value is null,
     * any value currently set will be cleared.
     *
     * @param value
     *  The value of the overridden content property, or null to clear the value
     *
     * @return
     *  A reference to this DTO
     */
    public ContentOverrideDTO setValue(String value) {
        this.value = value;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ContentOverrideDTO [content: %s, name: %s, value: %s]",
            this.getContentLabel(), this.getName(), this.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ContentOverrideDTO && super.equals(obj)) {
            ContentOverrideDTO that = (ContentOverrideDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getContentLabel(), that.getContentLabel())
                .append(this.getName(), that.getName())
                .append(this.getValue(), that.getValue());

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
            .append(this.getContentLabel())
            .append(this.getName())
            .append(this.getValue());

        return builder.toHashCode();
    }

    // Note: clone is unnecessary so long as this class consists entirely of immutable objects

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentOverrideDTO populate(ContentOverrideDTO source) {
        super.populate(source);

        this.setContentLabel(source.getContentLabel());
        this.setName(source.getName());
        this.setValue(source.getValue());

        return this;
    }

}

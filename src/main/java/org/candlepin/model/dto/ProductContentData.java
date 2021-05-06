/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.model.dto;

import org.candlepin.model.Content;
import org.candlepin.model.ProductContent;
import org.candlepin.service.model.ProductContentInfo;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;



/**
 * DTO representing a product content mapping exposed to the API and adapter layers. Unlike the
 * ProductContent entity, this DTO does not contain a reference to the product. Instead, it is
 * implied by attaching it to a ProductData instance.
 *
 * <pre>
 * {
 *   "content" : {
 *     ...
 *   },
 *   "enabled" : false
 * }
 * </pre>
 */
@XmlRootElement
public class ProductContentData implements Cloneable, Serializable, ProductContentInfo {
    public static final long serialVersionUID = 1L;

    private ContentData content;
    private Boolean enabled;

    /**
     * Initializes a new ProductContentData instance with null values.
     */
    public ProductContentData() {
        // Intentionally left empty
    }

    public ProductContentData(ContentData content, Boolean enabled) {
        this.setContent(content);
        this.setEnabled(enabled);
    }

    /**
     * Initializes a new ProductContentData instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductContentData(ProductContentData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Initializes a new ProductContentData instance using the data contained by the given entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductContentData(ProductContent source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Retrieves the content of the content data represented by this DTO. If the content has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the content of this DTO, or null if the content has not yet been defined
     */
    public ContentData getContent() {
        return this.content;
    }

    /**
     * Sets the content of the product content represented by this DTO.
     *
     * @param content
     *  The content of the product represented by this DTO
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductContentData setContent(ContentData content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        this.content = content;
        return this;
    }

    /**
     * Retrieves the enabled flag of the content data represented by this DTO. If the enabled flag
     * has not yet been defined, this method returns null.
     *
     * @return
     *  the enabled flag of this DTO, or null if the enabled flag has not yet been set
     */
    public Boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Sets the enabled flag of the product content represented by this DTO.
     *
     * @param enabled
     *  The enabled flag of the product content represented by this DTO, or null to clear the
     *  enabled flag
     *
     * @return
     *  a reference to this DTO
     */
    public ProductContentData setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ProductContentData [content: %s, enabled: %s]", this.content, this.enabled);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ProductContentData)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        ProductContentData that = (ProductContentData) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.content, that.content)
            .append(this.enabled, that.enabled);

        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.content)
            .append(this.enabled);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ProductContentData copy;

        try {
            copy = (ProductContentData) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        copy.content = this.content != null ? (ContentData) this.content.clone() : null;

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductContentData populate(ProductContentData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.content = source.content;
        this.enabled = source.enabled;

        return this;
    }

    /**
     * Populates this DTO with data from the given source entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductContentData populate(ProductContent source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        Content content = source.getContent();

        this.content = content != null ?
            (this.content != null ? this.content.populate(content) : content.toDTO()) :
            null;

        this.enabled = source.isEnabled();

        return this;
    }
}

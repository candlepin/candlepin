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

import org.candlepin.model.ProductAttribute;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlRootElement;



/**
 * DTO representing the content data exposed to the API and adapter layers.
 *
 * <pre>
 * {
 *   "name" : "type",
 *   "value" : "SVC",
 *   "created" : "2016-06-13T14:51:02+0000",
 *   "updated" : "2016-06-13T14:51:02+0000"
 * }
 * </pre>
 */
@ApiModel(parent = CandlepinDTO.class)
@XmlRootElement
public class ProductAttributeData extends CandlepinDTO {
    public static final long serialVersionUID = 1L;

    protected String name;
    protected String value;

    /**
     * Initializes a new ProductAttributeData instance with null values.
     */
    public ProductAttributeData() {
        super();
    }

    /**
     * Initializes a new ProductAttributeData instance using the given attribute name and value.
     *
     * @param name
     *  The name of the attribute
     *
     * @param value
     *  The value of the attribute
     *
     * @throws IllegalArgumentException
     *  if name is null
     */
    public ProductAttributeData(String name, String value) {
        this.setName(name);
        this.setValue(value);
    }

    /**
     * Initializes a new ProductAttributeData instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductAttributeData(ProductAttributeData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Initializes a new ProductAttributeData instance using the data contained by the given
     * entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductAttributeData(ProductAttribute source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Retrieves the name of the attribute represented by this DTO. If the name has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the name of the attribute, or null if the name has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the attribute represented by this DTO.
     *
     * @param name
     *  The name of the attribute represented by this DTO, or null to clear the name
     *
     * @return
     *  a reference to this DTO
     */
    public ProductAttributeData setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }

        this.name = name;
        return this;
    }

    /**
     * Retrieves the value of the attribute represented by this DTO. If the value has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the value of the attribute, or null if the value has not yet been defined
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the value of the attribute represented by this DTO.
     *
     * @param value
     *  The value of the attribute represented by this DTO, or null to clear the value
     *
     * @return
     *  a reference to this DTO
     */
    public ProductAttributeData setValue(String value) {
        this.value = value;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ProductAttributeData [name: %s, value: %s]", this.name, this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ProductAttributeData)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        ProductAttributeData that = (ProductAttributeData) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.name, that.name)
            .append(this.value, that.value);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(super.hashCode())
            .append(this.name)
            .append(this.value);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ProductAttributeData copy = (ProductAttributeData) super.clone();

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
    public ProductAttributeData populate(ProductAttributeData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.name = source.name;
        this.value = source.value;

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
    public ProductAttributeData populate(ProductAttribute source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.name = source.getName();
        this.value = source.getValue();

        return this;
    }
}

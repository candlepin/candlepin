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

package org.candlepin.model;

import org.candlepin.model.dto.ProductAttributeData;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlTransient;



/**
 * See Attributes interface for documentation.
 */
@Entity
@Table(name = ProductAttribute.DB_TABLE)
@Embeddable
@JsonFilter("ProductAttributeFilter")
public class ProductAttribute extends AbstractHibernateObject implements Attribute {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_product_attributes";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column
    @Size(max = 255)
    protected String value;

    /*
     * After Jackson version is upgraded:
     * @JsonProperty(access = Access.WRITE_ONLY)
     */
    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    private Product product;


    public ProductAttribute() {
        // Intentionally left empty
    }

    public ProductAttribute(String name, String val) {
        this.name = name;
        this.value = val;
    }

    /**
     * Creates a new ProductAttribute entity, initialized using the data from the given source DTO.
     *
     * @param source
     *  The source DTO containing the data with which to initialize a new entity
     */
    public ProductAttribute(ProductAttributeData source) {
        if (source != null) {
            this.populate(source);
        }
    }

    @Override
    public String toString() {
        return String.format("ProductAttribute [name: %s, value: %s]", this.name, this.value);
    }

    @XmlTransient
    @JsonIgnore
    public Product getProduct() {
        return product;
    }

    @JsonProperty
    public void setProduct(Product product) {
        this.product = product;
    }

    public String getName() {
        return name;
    }

    @XmlTransient
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }

        if (anObject instanceof Attribute) {
            Attribute that = (Attribute) anObject;

            return new EqualsBuilder()
                .append(this.name, that.getName())
                .append(this.value, that.getValue())
                .isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(31, 73)
            .append(this.name)
            .append(this.value);

        return builder.toHashCode();
    }

    /**
     * Calculates and returns a version hash for this entity. This method operates much like the
     * hashCode method, except that it is more accurate and should have fewer collisions.
     *
     * @return
     *  a version hash for this entity
     */
    public int getEntityVersion() {
        return this.hashCode();
    }

    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
     *
     * @param dto
     *  The product attribute DTO to check for changes
     *
     * @throws IllegalArgumentException
     *  if dto is null
     *
     * @return
     *  true if this attribute would be changed by the given DTO; false otherwise
     */
    public boolean isChangedBy(ProductAttributeData dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getName() != null && !dto.getName().equals(this.name)) {
            return true;
        }

        if (dto.getValue() != null && !dto.getValue().equals(this.value)) {
            return true;
        }

        return false;
    }

    /**
     * Populates this entity with the data contained in the source DTO. Unpopulated values within
     * the DTO will be ignored.
     *
     * @param source
     *  The source DTO containing the data to use to update this entity
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  A reference to this entity
     */
    public ProductAttribute populate(ProductAttributeData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        if (source.getName() != null) {
            this.setName(source.getName());
        }

        if (source.getValue() != null) {
            this.setValue(source.getValue());
        }

        return this;
    }

    /**
     * Returns a DTO representing this entity.
     *
     * @return
     *  a DTO representing this entity
     */
    public ProductAttributeData toDTO() {
        return new ProductAttributeData(this);
    }
}

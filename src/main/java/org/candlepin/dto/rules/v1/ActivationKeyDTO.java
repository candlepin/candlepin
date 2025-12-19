/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A DTO representation of the ActivationKey entity in the Rules framework:
 * {@code
 * <tt>
 *  {
 *    "id": "string",
 *    "pools": [
 *      {
 *        "quantity": 0,
 *        "pool": {
 *          "id": "string",
 *          "attributes": [ "key1": "value1", "key1": "value2"],
 *          "productAttributes": [ "key1": "value1", "key1": "value2"]
 *        }
 *      }
 *    ]
 *  }
 * </tt>
 * }
 */
public class ActivationKeyDTO extends CandlepinDTO<ActivationKeyDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Join object DTO for joining the activation key to pools
     */
    public static class ActivationKeyPoolDTO extends CandlepinDTO<ActivationKeyPoolDTO> {
        protected InternalPoolDTO pool;
        protected Long quantity;

        public ActivationKeyPoolDTO() {
            // Intentionally left empty
        }

        public InternalPoolDTO getPool() {
            return this.pool;
        }

        public ActivationKeyPoolDTO setPool(InternalPoolDTO pool) {
            this.pool = pool;
            return this;
        }

        public Long getQuantity() {
            return this.quantity;
        }

        public ActivationKeyPoolDTO setQuantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ActivationKeyPoolDTO) {
                ActivationKeyPoolDTO that = (ActivationKeyPoolDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getPool(), that.getPool())
                    .append(this.getQuantity(), that.getQuantity());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getPool())
                .append(this.getQuantity());

            return builder.toHashCode();
        }
    }

    /**
     * Internal DTO that represents a Pool object on an ActivationKeyPool object.
     * NOTE: Using a nested class like this one to represent a field type is a non-standard solution
     * and we should not be in the habit of doing. Instead, non-nested DTO classes should be used.
     */
    public static class InternalPoolDTO extends CandlepinDTO<InternalPoolDTO> {
        private String id;
        private Map<String, String> attributes;
        private Map<String, String> productAttributes;

        /**
         * Returns the internal db id.
         *
         * @return the db id.
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the internal db id.
         *
         * @param id new db id.
         *
         * @return a reference to this InternalPoolDTO object.
         */
        public InternalPoolDTO setId(String id) {
            this.id = id;
            return this;
        }

        /**
         * Retrieves a view of the attributes for the pool represented by this DTO. If the attributes
         * have not yet been defined, this method returns null.
         * <p></p>
         * Note that the collection returned by this method is a view of the collection backing this
         * set of attributes. Elements cannot be added to the collection, but elements may be removed.
         * Changes made to the collection will be reflected by this pool DTO instance.
         *
         * @return the attributes associated with this pool, or null if they have not yet been defined.
         */
        @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
        public Map<String, String> getAttributes() {
            return this.attributes != null ? new MapView<>(this.attributes) : null;
        }

        /**
         * Sets the attributes for this pool DTO.
         *
         * @param attributes
         *  A map of attribute key, value pairs to assign to this pool DTO, or null to clear the
         *  existing ones
         *
         * @return a reference to this InternalPoolDTO object.
         */
        @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
        public InternalPoolDTO setAttributes(Map<String, String> attributes) {
            if (attributes != null) {
                if (this.attributes == null) {
                    this.attributes = new HashMap<>();
                }
                else {
                    this.attributes.clear();
                }
                this.attributes.putAll(attributes);
            }
            else {
                this.attributes = null;
            }
            return this;
        }

        /**
         * Retrieves a view of the product attributes for the pool represented by this DTO. If the product
         * attributes have not yet been defined, this method returns null.
         * <p></p>
         * Note that the collection returned by this method is a view of the collection backing this
         * set of product attributes. Elements cannot be added to the collection, but elements may be removed.
         * Changes made to the collection will be reflected by this pool DTO instance.
         *
         * @return the product attributes associated with this pool,
         * or null if they have not yet been defined.
         */
        @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
        public Map<String, String> getProductAttributes() {
            return this.productAttributes != null ? new MapView<>(this.productAttributes) : null;
        }

        /**
         * Sets the product attributes for this pool DTO.
         *
         * @param productAttributes
         *  A map of product attribute key, value pairs to assign to this pool DTO, or null to clear the
         *  existing ones
         *
         * @return a reference to this InternalPoolDTO object.
         */
        @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
        public InternalPoolDTO setProductAttributes(Map<String, String> productAttributes) {
            if (productAttributes != null) {
                if (this.productAttributes == null) {
                    this.productAttributes = new HashMap<>();
                }
                else {
                    this.productAttributes.clear();
                }
                this.productAttributes.putAll(productAttributes);
            }
            else {
                this.productAttributes = null;
            }
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof PoolDTO) {
                PoolDTO that = (PoolDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getId(), that.getId())
                    .append(this.getAttributes(), that.getAttributes())
                    .append(this.getProductAttributes(), that.getProductAttributes());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getId())
                .append(this.getAttributes())
                .append(this.getProductAttributes());

            return builder.toHashCode();
        }
    }

    private String id;
    private Set<ActivationKeyPoolDTO> pools;

    /**
     * Initializes a new ActivationKeyDTO instance with null values.
     */
    public ActivationKeyDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ActivationKeyDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ActivationKeyDTO(ActivationKeyDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this ActivationKeyDTO object.
     *
     * @return the id field of this ActivationKeyDTO object.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this ActivationKeyDTO object.
     *
     * @param id the id to set on this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves a view of the pools associated with the activation key represented by this DTO.
     * If the pools have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of pools. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool data instance.
     *
     * @return
     *  the pools associated with this key, or null if the pools have not yet been defined
     */
    public Set<ActivationKeyPoolDTO> getPools() {
        return this.pools != null ? new SetView<>(this.pools) : null;
    }

    /**
     * Sets the pools of the activation key represented by this DTO.
     *
     * @param pools
     *  A collection of activation key pool DTOs to attach to this DTO, or null to clear the content
     *
     * @throws IllegalArgumentException
     *  if the collection contains null or incomplete pool DTOs
     *
     * @return
     *  a reference to this DTO object
     */
    public ActivationKeyDTO setPools(Collection<ActivationKeyPoolDTO> pools) {
        if (pools != null) {
            this.pools = new HashSet<>();

            for (ActivationKeyPoolDTO dto : pools) {
                if (dto == null || dto.getPool() == null || dto.getPool().getId() == null ||
                    dto.getPool().getId().isEmpty()) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete activation key pools");
                }

                this.pools.add(dto);
            }
        }
        else {
            this.pools = null;
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ActivationKeyDTO [id: %s]", this.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ActivationKeyDTO) {
            ActivationKeyDTO that = (ActivationKeyDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getPools(), that.getPools());

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
            .append(this.getPools());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO clone() {
        ActivationKeyDTO copy = super.clone();

        Collection<ActivationKeyPoolDTO> pools = this.getPools();
        copy.setPools(null);
        if (pools != null) {
            copy.setPools(pools);
        }

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ActivationKeyDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setPools(source.getPools());

        return this;
    }
}

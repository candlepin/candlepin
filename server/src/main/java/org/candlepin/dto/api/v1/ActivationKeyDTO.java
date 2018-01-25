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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.jackson.SingleValueWrapSerializer;
import org.candlepin.jackson.SingleValueWrapDeserializer;
import org.candlepin.util.SetView;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A DTO representation of the ActivationKey entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an activation key")
public class ActivationKeyDTO extends TimestampedCandlepinDTO<ActivationKeyDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Join object DTO for joining the activation key to pools
     */
    public static class ActivationKeyPoolDTO {
        protected final String poolId;
        protected final Long quantity;

        @JsonCreator
        public ActivationKeyPoolDTO(
            @JsonProperty("poolId") String poolId,
            @JsonProperty("quantity") Long quantity) {
            if (poolId == null || poolId.isEmpty()) {
                throw new IllegalArgumentException("pool id is null or empty");
            }

            this.poolId = poolId;
            this.quantity = quantity;
        }

        public String getPoolId() {
            return this.poolId;
        }

        public Long getQuantity() {
            return this.quantity;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ActivationKeyPoolDTO) {
                ActivationKeyPoolDTO that = (ActivationKeyPoolDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getPoolId(), that.getPoolId())
                    .append(this.getQuantity(), that.getQuantity());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getPoolId())
                .append(this.getQuantity());

            return builder.toHashCode();
        }
    }

    /**
     * Join object DTO for joining the activation key to content overrides
     */
    public static class ActivationKeyContentOverrideDTO {
        protected final String contentLabel;
        protected final String name;
        protected final String value;

        @JsonCreator
        public ActivationKeyContentOverrideDTO(
            @JsonProperty("contentLabel") String contentLabel,
            @JsonProperty("name") String name,
            @JsonProperty("value") String value) {
            if (contentLabel == null || contentLabel.isEmpty()) {
                throw new IllegalArgumentException("contentLabel is null or empty");
            }

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is null or empty");
            }

            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("value is null or empty");
            }

            this.contentLabel = contentLabel;
            this.name = name;
            this.value = value;
        }

        public String getContentLabel() {
            return this.contentLabel;
        }

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ActivationKeyContentOverrideDTO) {
                ActivationKeyContentOverrideDTO that = (ActivationKeyContentOverrideDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getContentLabel(), that.getContentLabel())
                    .append(this.getName(), that.getName())
                    .append(this.getValue(), that.getValue());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getContentLabel())
                .append(this.getName())
                .append(this.getValue());

            return builder.toHashCode();
        }
    }

    /**
     * Serialization utility class for wrapping the 'releaseVer' field in a JSON object.
     */
    private static class ReleaseVersionWrapSerializer extends SingleValueWrapSerializer {
        public ReleaseVersionWrapSerializer() {
            super("releaseVer");
        }
    }

    /**
     * Deserialization utility class for for unwrapping the 'releaseVer' field from a JSON object.
     */
    private static class ReleaseVersionWrapDeserializer extends SingleValueWrapDeserializer {
        public ReleaseVersionWrapDeserializer() {
            super("releaseVer");
        }
    }

    /**
     * Serialization utility class for wrapping the 'productId' field in a JSON object.
     */
    private static class ProductWrapSerializer extends SingleValueWrapSerializer {
        public ProductWrapSerializer() {
            super("productId");
        }
    }

    /**
     * Deserialization utility class for for unwrapping the 'productId' field from a JSON object.
     */
    private static class ProductWrapDeserializer extends SingleValueWrapDeserializer {
        public ProductWrapDeserializer() {
            super("productId");
        }
    }

    private String id;
    private String name;
    private String description;
    private OwnerDTO owner;

    private String releaseVer;
    private String serviceLevel;
    private Boolean autoAttach;
    private Set<ActivationKeyPoolDTO> pools;

    private Set<String> products;
    private Set<ActivationKeyContentOverrideDTO> contentOverrides;

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
     * Retrieves the name of this ActivationKeyDTO object.
     *
     * @return the name of this ActivationKeyDTO object.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this ActivationKeyDTO object.
     *
     * @param name the name of this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the description of this ActivationKeyDTO object.
     *
     * @return the description of this ActivationKeyDTO object.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this ActivationKeyDTO object.
     *
     * @param description the description of this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Retrieves the owner of this ActivationKeyDTO object.
     *
     * @return the owner of this ActivationKeyDTO object.
     */
    public OwnerDTO getOwner() {
        return owner;
    }

    /**
     * Sets the owner of this ActivationKeyDTO object.
     *
     * @param owner the owner of this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Retrieves the release version of this ActivationKeyDTO object.
     *
     * @return the release version of this ActivationKeyDTO object.
     */
    @JsonSerialize(using = ReleaseVersionWrapSerializer.class,
        nullsUsing = ReleaseVersionWrapSerializer.class)
    @JsonProperty("releaseVer")
    public String getReleaseVersion() {
        return this.releaseVer;
    }

    /**
     * Sets the release version of this ActivationKeyDTO object.
     *
     * @param releaseVersion the release version of this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonDeserialize(using = ReleaseVersionWrapDeserializer.class)
    @JsonProperty("releaseVer")
    public ActivationKeyDTO setReleaseVersion(String releaseVersion) {
        this.releaseVer = releaseVersion;
        return this;
    }

    /**
     * Retrieves the service level of this ActivationKeyDTO object.
     *
     * @return the service level of this ActivationKeyDTO object.
     */
    public String getServiceLevel() {
        return serviceLevel;
    }

    /**
     * Sets the service level of this ActivationKeyDTO object.
     *
     * @param serviceLevel the service level of this ActivationKeyDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    /**
     * Returns true if auto attach is enabled for this ActivationKeyDTO object, false otherwise.
     *
     * @return true if auto attach is enabled for this ActivationKeyDTO object, false otherwise.
     */
    public Boolean isAutoAttach() {
        return autoAttach;
    }

    /**
     * Sets if auto attach is enabled or not for this ActivationKeyDTO object.
     *
     * @param autoAttach if auto attach is enabled or not for this ActivationKeyDTO object.
     * @return a reference to this DTO object.
     */
    public ActivationKeyDTO setAutoAttach(Boolean autoAttach) {
        this.autoAttach = autoAttach;
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
        return this.pools != null ? new SetView<ActivationKeyPoolDTO>(this.pools) : null;
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
    public ActivationKeyDTO setPools(Set<ActivationKeyPoolDTO> pools) {
        if (pools != null) {
            if (this.pools == null) {
                this.pools = new HashSet<ActivationKeyPoolDTO>();
            }
            else {
                this.pools.clear();
            }

            for (ActivationKeyPoolDTO dto : pools) {
                if (dto == null || dto.getPoolId() == null || dto.getPoolId().isEmpty()) {
                    throw new IllegalArgumentException("collection contains null or incomplete pools");
                }

                this.pools.add(new ActivationKeyPoolDTO(dto.getPoolId(), dto.getQuantity()));
            }
        }
        else {
            this.pools = null;
        }
        return this;
    }

    /**
     * Checks if the specified pool has been added to this activation key DTO.
     *
     * @param poolId
     *  The ID of the pool to check
     *
     * @throws IllegalArgumentException
     *  if the poolId is null
     *
     * @return
     *  true if the pool with the given ID is contained in this activation key DTO; false otherwise.
     */
    @JsonIgnore
    public boolean hasPool(String poolId) {
        if (poolId == null) {
            throw new IllegalArgumentException("poolId is null");
        }

        if (this.pools == null) {
            return false;
        }

        for (ActivationKeyPoolDTO candidate : this.getPools()) {
            if (poolId.equals(candidate.getPoolId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes the pool with the given pool ID from this activation key DTO.
     *
     * @param poolId
     *  The ID of the pool associated with this key that is to be removed.
     *
     * @throws IllegalArgumentException
     *  if the poolId is null
     *
     * @return
     *  True if the pool was removed; false otherwise.
     */
    @JsonIgnore
    public boolean removePool(String poolId) {
        if (poolId == null) {
            throw new IllegalArgumentException("poolId is null");
        }

        if (this.pools == null) {
            return false;
        }

        for (ActivationKeyPoolDTO candidate : this.getPools()) {
            if (poolId.equals(candidate.getPoolId())) {
                this.pools.remove(candidate);
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the given pool to this activation key DTO.
     *
     * @param poolDto the ActivationKeyPoolDTO to add to this activation key DTO.
     *
     * @throws IllegalArgumentException
     *  if the poolDto is null or incomplete
     *
     * @return true if this pool was not already contained in this activation key DTO.
     */
    @JsonIgnore
    public boolean addPool(ActivationKeyPoolDTO poolDto) {
        if (poolDto == null || poolDto.getPoolId() == null) {
            throw new IllegalArgumentException("poolDto is null or has null pool id.");
        }

        if (this.pools == null) {
            this.pools = new HashSet<ActivationKeyPoolDTO>();
        }

        return this.pools.add(poolDto);
    }

    /**
     * Retrieves a view of the product IDs for the activation key represented by this DTO.
     * If the product IDs have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of product IDs. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product IDs data instance.
     *
     * @return
     *  the product IDs associated with this key, or null if they have not yet been defined
     */
    @JsonSerialize(contentUsing = ProductWrapSerializer.class)
    @JsonProperty("products")
    public Set<String> getProductIds() {
        return this.products != null ? new SetView<String>(this.products) : null;
    }

    /**
     * Adds the given collection of product IDs to this ActivationKey DTO.
     *
     * @param products
     *  A set of product IDs to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    @JsonDeserialize(contentUsing = ProductWrapDeserializer.class)
    @JsonProperty("products")
    public ActivationKeyDTO setProductIds(Set<String> products) {
        if (products != null) {
            if (this.products == null) {
                this.products = new HashSet<String>();
            }
            else {
                this.products.clear();
            }

            for (String dto : products) {
                if (dto == null || dto.isEmpty()) {
                    throw new IllegalArgumentException("collection contains null or incomplete product IDs");
                }
            }

            this.products.addAll(products);
        }
        else {
            this.products = null;
        }
        return this;
    }

    /**
     * Removes the product ID from this activation key DTO.
     *
     * @param productId
     *  The product ID associated with this key that is to be removed.
     *
     * @throws IllegalArgumentException
     *  if the productId is null
     *
     * @return
     *  True if the product ID was removed; false otherwise.
     */
    @JsonIgnore
    public boolean removeProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.products == null) {
            return false;
        }

        for (String candidate : this.getProductIds()) {
            if (productId.equals(candidate)) {
                this.getProductIds().remove(candidate);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given product ID is contained in this activation key DTO.
     *
     * @param productId
     *  The product ID associated with this key to be checked.
     *
     * @throws IllegalArgumentException
     *  if the productId is null
     *
     * @return
     *  true if the product ID is contained in this activation key DTO; false otherwise.
     */
    @JsonIgnore
    public boolean hasProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.products == null) {
            return false;
        }

        for (String candidate : this.getProductIds()) {
            if (productId.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds the given product ID to this activation key DTO.
     *
     * @param
     *  productId the product ID to add to this activation key DTO.
     *
     * @throws IllegalArgumentException
     *  if the productId is null or empty
     *
     * @return
     *  true if this product ID was not already contained in this activation key DTO.
     */
    @JsonIgnore
    public boolean addProductId(String productId) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        if (this.products == null) {
            this.products = new HashSet<String>();
        }

        return this.products.add(productId);
    }

    /**
     * Retrieves a view of the content overrides associated with the activation key represented by this DTO.
     * If the content overrides have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of content overrides. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this content overrides data instance.
     *
     * @return
     *  the content overrides associated with this key,
     *  or null if the content overrideshave not yet been defined
     */
    public Set<ActivationKeyContentOverrideDTO> getContentOverrides() {
        return this.contentOverrides != null ?
            new SetView<ActivationKeyContentOverrideDTO>(this.contentOverrides) : null;
    }

    /**
     * Sets the content overrides of the activation key represented by this DTO.
     *
     * @param contentOverrides
     *  A collection of activation key content override DTOs to attach to this DTO,
     *  or null to clear the content
     *
     * @throws IllegalArgumentException
     *  if the collection contains null or incomplete content overrides DTOs
     *
     * @return
     *  a reference to this DTO
     */
    public ActivationKeyDTO setContentOverrides(Set<ActivationKeyContentOverrideDTO> contentOverrides) {
        if (contentOverrides != null) {
            if (this.contentOverrides == null) {
                this.contentOverrides = new HashSet<ActivationKeyContentOverrideDTO>();
            }
            else {
                this.contentOverrides.clear();
            }

            for (ActivationKeyContentOverrideDTO dto : contentOverrides) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException("collection contains null " +
                        "or incomplete content override");
                }

                this.contentOverrides.add(
                    new ActivationKeyContentOverrideDTO(
                    dto.getContentLabel(), dto.getName(), dto.getValue()));
            }
        }
        else {
            this.contentOverrides = null;
        }
        return this;
    }

    /**
     * Removes the given content override DTO(s) from this activation key DTO.
     * @param contentOverrideDto
     *  The key's associated content override DTO(s) to remove
     * @return
     *  True if the content override DTO(s) was removed; false otherwise.
     */
    @JsonIgnore
    public boolean removeContentOverride(ActivationKeyContentOverrideDTO contentOverrideDto) {
        if (contentOverrideDto == null) {
            throw new IllegalArgumentException("contentOverrideDto is null");
        }

        if (this.contentOverrides == null) {
            return false;
        }

        List<ActivationKeyContentOverrideDTO> remove = new LinkedList<ActivationKeyContentOverrideDTO>();

        for (ActivationKeyContentOverrideDTO candidate : this.getContentOverrides()) {
            if (candidate.getContentLabel().equals(contentOverrideDto.getContentLabel()) &&
                candidate.getName().equals(contentOverrideDto.getName())) {
                remove.add(candidate);
            }
        }

        return this.contentOverrides.removeAll(remove);
    }

    /**
     * Adds the given content override DTO to this activation key DTO.
     *
     * @param contentOverrideDto the content override DTO to add to this activation key DTO.
     *
     * @return true if this content override DTO was not already contained in this activation key DTO.
     */
    @JsonIgnore
    public boolean addContentOverride(ActivationKeyContentOverrideDTO contentOverrideDto) {
        if (isNullOrIncomplete(contentOverrideDto)) {
            throw new IllegalArgumentException("contentOverrideDto is null or incomplete");
        }

        if (this.contentOverrides == null) {
            this.contentOverrides = new HashSet<ActivationKeyContentOverrideDTO>();
        }

        return this.contentOverrides.add(contentOverrideDto);
    }

    private boolean isNullOrIncomplete(ActivationKeyContentOverrideDTO contentOverrideDto) {
        return contentOverrideDto == null ||
            contentOverrideDto.getContentLabel() == null || contentOverrideDto.getContentLabel().isEmpty() ||
            contentOverrideDto.getName() == null || contentOverrideDto.getName().isEmpty() ||
            contentOverrideDto.getValue() == null || contentOverrideDto.getValue().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ActivationKeyDTO [id: %s, name: %s, description: %s]",
            this.getId(), this.getName(), this.getDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ActivationKeyDTO && super.equals(obj)) {
            ActivationKeyDTO that = (ActivationKeyDTO) obj;

            // Pull the owner IDs, as we're not interested in verifying that the owners
            // themselves are equal; just so long as they point to the same owner.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getDescription(), that.getDescription())
                .append(thisOwnerId, thatOwnerId)
                .append(this.getReleaseVersion(), that.getReleaseVersion())
                .append(this.getServiceLevel(), that.getServiceLevel())
                .append(this.isAutoAttach(), that.isAutoAttach())
                .append(this.getPools(), that.getPools())
                .append(this.getProductIds(), that.getProductIds())
                .append(this.getContentOverrides(), that.getContentOverrides());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Like with the equals method, we are not interested in hashing nested objects; we're only
        // concerned with the reference to such an object.

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getName())
            .append(this.getDescription())
            .append(this.getOwner() != null ? this.getOwner().getId() : null)
            .append(this.getReleaseVersion())
            .append(this.getServiceLevel())
            .append(this.isAutoAttach())
            .append(this.getPools())
            .append(this.getProductIds())
            .append(this.getContentOverrides());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO clone() {
        ActivationKeyDTO copy = (ActivationKeyDTO) super.clone();

        OwnerDTO owner = this.getOwner();
        copy.owner = owner != null ? owner.clone() : null;
        copy.pools = this.getPools();
        copy.products = this.getProductIds();
        copy.contentOverrides = this.getContentOverrides();

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ActivationKeyDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setName(source.getName())
            .setDescription(source.getDescription())
            .setOwner(source.getOwner())
            .setReleaseVersion(source.getReleaseVersion())
            .setServiceLevel(source.getServiceLevel())
            .setAutoAttach(source.isAutoAttach())
            .setPools(source.getPools())
            .setProductIds(source.getProductIds())
            .setContentOverrides(source.getContentOverrides());

        return this;
    }
}

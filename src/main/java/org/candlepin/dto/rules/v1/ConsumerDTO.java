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

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.jackson.SingleValueWrapDeserializer;
import org.candlepin.jackson.SingleValueWrapSerializer;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A DTO representation of the Consumer entity, as used by the Rules framework.
 */
public class ConsumerDTO extends TimestampedCandlepinDTO<ConsumerDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Serialization utility class for wrapping the installedProducts 'productId' field in a JSON object.
     */
    private static class InstalledProductIdWrapSerializer extends SingleValueWrapSerializer {
        public InstalledProductIdWrapSerializer() {
            super("productId");
        }
    }

    /**
     * Serialization utility class for unwrapping the installedProducts 'productId' field from a JSON object.
     */
    private static class InstalledProductIdWrapDeserializer extends SingleValueWrapDeserializer {
        public InstalledProductIdWrapDeserializer() {
            super("productId");
        }
    }

    /**
     * Serialization utility class for wrapping the capabilities 'name' field in a JSON object.
     */
    private static class CapabilityNameWrapSerializer extends SingleValueWrapSerializer {
        public CapabilityNameWrapSerializer() {
            super("name");
        }
    }

    /**
     * Serialization utility class for unwrapping the capabilities 'name' field from a JSON object.
     */
    private static class CapabilityNameWrapDeserializer extends SingleValueWrapDeserializer {
        public CapabilityNameWrapDeserializer() {
            super("name");
        }
    }

    protected String uuid;
    protected String username;
    protected String serviceLevel;
    protected String role;
    protected String usage;
    protected Set<String> addOns;
    protected OwnerDTO owner;
    protected Map<String, String> facts;
    protected Set<String> installedProducts;
    protected Set<String> capabilities;
    protected ConsumerTypeDTO type;
    protected String serviceType;

    /**
     * Initializes a new ConsumerDTO instance with null values.
     */
    public ConsumerDTO() {
        // Intentionally left blank
    }

    /**
     * Initializes a new ConsumerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ConsumerDTO(ConsumerDTO source) {
        super(source);
    }

    /**
     * Retrieves the uuid field of this ConsumerDTO object.
     *
     * @return the uuid of the consumer.
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the uuid to set on this ConsumerDTO object.
     *
     * @param uuid the id to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the username field of this ConsumerDTO object.
     *
     * @return the username of the consumer.
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Sets the username to set on this ConsumerDTO object.
     *
     * @param username the username to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Retrieves the service level field of this ConsumerDTO object.
     *
     * @return the serviceLevel of the consumer.
     */
    public String getServiceLevel() {
        return this.serviceLevel;
    }

    /**
     * Sets the service level to set on this ConsumerDTO object.
     *
     * @param serviceLevel the service level to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    /**
     * Retrieves the role field of this ConsumerDTO object.
     *
     * @return the role of the consumer, or null if it has not yet been defined
     */
    public String getRole() {
        return this.role;
    }

    /**
     * Sets the role to set on this ConsumerDTO object.
     *
     * @param role the role to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setRole(String role) {
        this.role = role;
        return this;
    }


    /**
     * Retrieves the usage field of this ConsumerDTO object.
     *
     * @return the usage of the consumer, or null if it has not yet been defined
     */
    public String getUsage() {
        return this.usage;
    }

    /**
     * Sets the usage to set on this ConsumerDTO object.
     *
     * @param usage the usage to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    /**
     * Retrieves the system purpose add ons of this ConsumerDTO object.
     *
     * @return the system purpose add ons of the consumer, or null if it has not yet been defined
     */
    public Set<String> getAddOns() {
        return this.addOns != null ? new SetView<>(this.addOns) : null;
    }

    /**
     * Sets the system purpose add ons on this ConsumerDTO object.
     *
     * @param addOns the add ons to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setAddOns(Set<String> addOns) {
        if (addOns != null) {
            if (this.addOns == null) {
                this.addOns = new HashSet<>();
            }
            else {
                this.addOns.clear();
            }
            this.addOns.addAll(addOns);
        }
        else {
            this.addOns = null;
        }
        return this;
    }

    /**
     * Retrieves the owner field of this ConsumerDTO object.
     *
     * @return the owner of the consumer.
     */
    public OwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner to set on this ConsumerDTO object.
     *
     * @param owner the owner to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Retrieves the facts field of this ConsumerDTO object.
     *
     * @return the facts of the consumer, or null if it has not yet been defined
     */
    public Map<String, String> getFacts() {
        return this.facts != null ? new MapView<>(facts) : null;
    }

    /**
     * Sets the facts to set on this ConsumerDTO object.
     *
     * @param facts the facts to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setFacts(Map<String, String> facts) {
        if (facts != null) {
            if (this.facts == null) {
                this.facts = new HashMap<>();
            }
            else {
                this.facts.clear();
            }

            this.facts.putAll(facts);
        }
        else {
            this.facts = null;
        }
        return this;
    }

    /**
     * Sets a fact to set on this ConsumerDTO object.
     *
     * @param key the key of the fact to set on this ConsumerDTO object.
     *
     * @param value the value of the fact to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setFact(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (facts == null) {
            facts = new HashMap<>();
        }

        this.facts.put(key, value);
        return this;
    }

    /**
     * Retrieves the installed products field of this ConsumerDTO object.
     *
     * @return the installedProducts of the consumer, or null if it has not yet been defined
     */
    @JsonSerialize(contentUsing = InstalledProductIdWrapSerializer.class)
    public Set<String> getInstalledProducts() {
        return this.installedProducts != null ? new SetView<>(this.installedProducts) : null;
    }

    /**
     * Sets the installed products to set on this ConsumerDTO object.
     *
     * @param installedProducts the installed products to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonDeserialize(contentUsing = InstalledProductIdWrapDeserializer.class)
    public ConsumerDTO setInstalledProducts(Set<String> installedProducts) {
        if (installedProducts != null) {
            if (this.installedProducts == null) {
                this.installedProducts = new HashSet<>();
            }
            else {
                this.installedProducts.clear();
            }

            for (String dto : installedProducts) {
                if (dto == null || dto.isEmpty()) {
                    throw new IllegalArgumentException(
                        "collection contains null or empty consumer installed product");
                }
                this.installedProducts.add(dto);
            }
        }
        else {
            this.installedProducts = null;
        }
        return this;
    }

    /**
     * Adds an installedProduct to this ConsumerDTO object.
     *
     * @param installedProduct the installed product to add to this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public boolean addInstalledProduct(String installedProduct) {
        if (installedProduct == null || installedProduct.isEmpty()) {
            throw new IllegalArgumentException("null or empty consumer installed product");
        }
        if (installedProducts == null) {
            installedProducts = new HashSet<>();
        }
        return installedProducts.add(installedProduct);
    }

    /**
     * removes an installed product from this ConsumerDTO object.
     *
     * @param productId the product Id of the installed product to remove from this
     * ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public boolean removeInstalledProduct(String productId) {
        if (StringUtils.isEmpty(productId)) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        if (this.installedProducts != null) {
            for (String dto : this.installedProducts) {
                if (dto.contentEquals(productId)) {
                    return this.installedProducts.remove(dto);
                }
            }
        }
        return false;
    }

    /**
     * Retrieves the consumer capabilities field of this ConsumerDTO object.
     *
     * @return the capabilities of the consumer, or null if it has not yet been defined
     */
    @JsonSerialize(contentUsing = CapabilityNameWrapSerializer.class)
    public Set<String> getCapabilities() {
        return this.capabilities != null ? new SetView<>(capabilities) : null;
    }

    /**
     * Sets the capabilities on this ConsumerDTO object.
     *
     * @param capabilities the capabilities to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonDeserialize(contentUsing = CapabilityNameWrapDeserializer.class)
    public ConsumerDTO setCapabilities(Set<String> capabilities) {
        if (capabilities != null) {
            if (this.capabilities == null) {
                this.capabilities = new HashSet<>();
            }
            else {
                this.capabilities.clear();
            }

            for (String dto : capabilities) {
                if (dto == null || dto.isEmpty()) {
                    throw new IllegalArgumentException("null or empty consumer capability");
                }

                this.capabilities.add(dto);
            }
        }
        else {
            this.capabilities = null;
        }
        return this;
    }

    /**
     * Retrieves the type field of this ConsumerDTO object.
     *
     * @return the type of the consumer.
     */
    public ConsumerTypeDTO getType() {
        return this.type;
    }

    /**
     * Sets the consumer type to set on this ConsumerDTO object.
     *
     * @param type the type to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setType(ConsumerTypeDTO type) {
        this.type = type;
        return this;
    }

    /**
     * Returns the value of the fact with the given key.
     *
     * @param factKey specific fact to retrieve.
     *
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        if (facts != null) {
            return facts.get(factKey);
        }
        return null;
    }

    /**
     * Retrieves the service type field of this ConsumerDTO object.
     *
     * @return the service type of the consumer.
     */
    public String getServiceType() {
        return this.serviceType;
    }

    /**
     * Sets the consumer service type to set on this ConsumerDTO object.
     *
     * @param serviceType to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setServiceType(String serviceType) {
        this.serviceType = serviceType;
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ConsumerDTO [uuid: %s, username: %s]", this.getUuid(), this.getUsername());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ConsumerDTO && super.equals(obj)) {
            ConsumerDTO that = (ConsumerDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getUuid(), that.getUuid())
                .append(this.getUsername(), that.getUsername())
                .append(this.getServiceLevel(), that.getServiceLevel())
                .append(this.getRole(), that.getRole())
                .append(this.getUsage(), that.getUsage())
                .append(this.getAddOns(), that.getAddOns())
                .append(this.getOwner(), that.getOwner())
                .append(this.getFacts(), that.getFacts())
                .append(this.getInstalledProducts(), that.getInstalledProducts())
                .append(this.getCapabilities(), that.getCapabilities())
                .append(this.getType(), that.getType())
                .append(this.getServiceType(), that.getServiceType());

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
            .append(this.getUuid())
            .append(this.getUsername())
            .append(this.getServiceLevel())
            .append(this.getRole())
            .append(this.getUsage())
            .append(this.getAddOns())
            .append(this.getOwner())
            .append(this.getFacts())
            .append(this.getInstalledProducts())
            .append(this.getCapabilities())
            .append(this.getType())
            .append(this.getServiceType());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO clone() {
        ConsumerDTO copy = super.clone();

        OwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? owner.clone() : null);

        ConsumerTypeDTO ctype = this.getType();
        copy.setType(ctype != null ? ctype.clone() : null);

        copy.setFacts(this.getFacts());
        copy.setInstalledProducts(this.getInstalledProducts());
        copy.setCapabilities(this.getCapabilities());

        copy.setAddOns(this.getAddOns());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ConsumerDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setUsername(source.getUsername());
        this.setServiceLevel(source.getServiceLevel());
        this.setRole(source.getRole());
        this.setUsage(source.getUsage());
        this.setAddOns(source.getAddOns());
        this.setOwner(source.getOwner());
        this.setFacts(source.getFacts());
        this.setInstalledProducts(source.getInstalledProducts());
        this.setCapabilities(source.getCapabilities());
        this.setType(source.getType());
        this.setServiceType(source.getServiceType());

        return this;
    }
}

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

import org.candlepin.common.jackson.HateoasArrayExclude;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.jackson.SingleValueWrapDeserializer;
import org.candlepin.jackson.SingleValueWrapSerializer;
import org.candlepin.util.ListView;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;

import io.swagger.annotations.ApiModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * A DTO representation of the Consumer entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an upstream consumer")
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("ConsumerFilter")
public class ConsumerDTO extends TimestampedCandlepinDTO<ConsumerDTO> implements LinkableDTO {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String uuid;
    protected String name;
    protected String username;
    protected String entitlementStatus;
    protected String serviceLevel;
    protected String releaseVer;
    protected OwnerDTO owner;
    protected EnvironmentDTO environment;
    protected Long entitlementCount;
    protected Map<String, String> facts;
    protected Date lastCheckin;
    protected Set<ConsumerInstalledProductDTO> installedProducts;
    protected Boolean canActivate;
    protected Set<CapabilityDTO> capabilities;
    protected HypervisorIdDTO hypervisorId;
    protected Set<String> contentTags;
    protected Boolean autoheal;
    protected String recipientOwnerKey;
    protected String annotations;
    protected String contentAccessMode;
    protected ConsumerTypeDTO type;
    protected CertificateDTO idCert;
    protected List<GuestIdDTO> guestIds;

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
     * Retrieves the id field of this ConsumerDTO object.
     *
     * @return the id of the consumer, or null if the id has not yet been defined
     */
    @HateoasInclude
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this ConsumerDTO object.
     *
     * @param id the id to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the uuid field of this ConsumerDTO object.
     *
     * @return the uuid of the consumer, or null if the uuid has not yet been defined
     */
    @HateoasInclude
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
     * Retrieves the name field of this ConsumerDTO object.
     *
     * @return the name of the consumer, or null if the name has not yet been defined
     */
    @HateoasInclude
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name to set on this ConsumerDTO object.
     *
     * @param name the name to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the username field of this ConsumerDTO object.
     *
     * @return the username of the consumer, or null if the username has not yet been defined
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
     * Retrieves the entitlement status field of this ConsumerDTO object.
     *
     * @return the entitlementStatus of the consumer, or null if it has not yet been defined
     */
    public String getEntitlementStatus() {
        return entitlementStatus;
    }

    /**
     * Sets the entitlement status to set on this ConsumerDTO object.
     *
     * @param entitlementStatus the entitlement status to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setEntitlementStatus(String entitlementStatus) {
        this.entitlementStatus = entitlementStatus;
        return this;
    }

    /**
     * Retrieves the service level field of this ConsumerDTO object.
     *
     * @return the serviceLevel of the consumer, or null if it has not yet been defined
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
     * Retrieves the releaseVer field of this ConsumerDTO object.
     *
     * @return the releaseVer of the consumer, or null if it has not yet been defined
     */
    @JsonSerialize(using = ConsumerDTO.ReleaseVersionWrapSerializer.class,
        nullsUsing = ConsumerDTO.ReleaseVersionWrapSerializer.class)
    @JsonProperty("releaseVer")
    public String getReleaseVersion() {
        return releaseVer;
    }

    /**
     * Sets the releaseVer to set on this ConsumerDTO object.
     *
     * @param releaseVer the release version to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonDeserialize(using = ConsumerDTO.ReleaseVersionWrapDeserializer.class)
    @JsonProperty("releaseVer")
    public ConsumerDTO setReleaseVersion(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    /**
     * Retrieves the owner field of this ConsumerDTO object.
     *
     * @return the owner of the consumer, or null if it has not yet been defined
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
     * Retrieves the environment field of this ConsumerDTO object.
     *
     * @return the environment of the consumer, or null if it has not yet been defined
     */
    public EnvironmentDTO getEnvironment() {
        return this.environment;
    }

    /**
     * Sets the environment to set on this ConsumerDTO object.
     *
     * @param environment the environment to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setEnvironment(EnvironmentDTO environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Retrieves the entitlement count field of this ConsumerDTO object.
     *
     * @return the entitlementCount of the consumer, or null if it has not yet been defined
     */
    public Long getEntitlementCount() {
        return this.entitlementCount;
    }

    /**
     * Sets the entitlement count to set on this ConsumerDTO object.
     *
     * @param entitlementCount the entitlement count to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setEntitlementCount(Long entitlementCount) {
        this.entitlementCount = entitlementCount;
        return this;
    }

    /**
     * Retrieves the facts field of this ConsumerDTO object.
     *
     * @return the facts of the consumer, or null if it has not yet been defined
     */
    @HateoasArrayExclude
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
     * Retrieves the last checkin field of this ConsumerDTO object.
     *
     * @return the lastCheckin of the consumer, or null if it has not yet been defined
     */
    public Date getLastCheckin() {
        return this.lastCheckin;
    }

    /**
     * Sets the last checkin date to set on this ConsumerDTO object.
     *
     * @param lastCheckin the last checkin date to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
        return this;
    }

    /**
     * Retrieves the installed products field of this ConsumerDTO object.
     *
     * @return the installedProducts of the consumer, or null if it has not yet been defined
     */
    public Set<ConsumerInstalledProductDTO> getInstalledProducts() {
        return this.installedProducts != null ? new SetView<>(this.installedProducts) : null;
    }

    /**
     * Sets the installed products to set on this ConsumerDTO object.
     *
     * @param installedProducts the installed products to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setInstalledProducts(Set<ConsumerInstalledProductDTO> installedProducts) {
        if (installedProducts != null) {
            if (this.installedProducts == null) {
                this.installedProducts = new HashSet<>();
            }
            else {
                this.installedProducts.clear();
            }

            for (ConsumerInstalledProductDTO dto : installedProducts) {
                if (dto == null || StringUtils.isEmpty(dto.getProductId())) {
                    throw new IllegalArgumentException("collection contains null" +
                        "or incomplete consumer installed product");
                }
                this.installedProducts.add(new ConsumerInstalledProductDTO(dto));
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
    public boolean addInstalledProduct(ConsumerInstalledProductDTO installedProduct) {
        if (installedProduct == null || StringUtils.isEmpty(installedProduct.getProductId())) {
            throw new IllegalArgumentException("null or incomplete consumer installed product");
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
            for (ConsumerInstalledProductDTO dto : this.installedProducts) {
                if (dto.getProductId().contentEquals(productId)) {
                    return this.installedProducts.remove(dto);
                }
            }
        }
        return false;
    }

    /**
     * Retrieves the canActivate field of this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public Boolean isCanActivate() {
        return this.canActivate;
    }

    /**
     * Sets the canActivate on this ConsumerDTO object.
     *
     * @param canActivate the canActivate field to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setCanActivate(Boolean canActivate) {
        this.canActivate = canActivate;
        return this;
    }

    /**
     * Retrieves the consumer capabilities field of this ConsumerDTO object.
     *
     * @return the capabilities of the consumer, or null if it has not yet been defined
     */
    public Set<CapabilityDTO> getCapabilities() {
        return this.capabilities != null ? new SetView<>(capabilities) : null;
    }

    /**
     * Sets the capabilities on this ConsumerDTO object.
     *
     * @param capabilities the capabilities to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setCapabilities(Set<CapabilityDTO> capabilities) {
        if (capabilities != null) {
            if (this.capabilities == null) {
                this.capabilities = new HashSet<>();
            }
            else {
                this.capabilities.clear();
            }

            for (CapabilityDTO dto : capabilities) {
                if (dto == null || StringUtils.isEmpty(dto.getName())) {
                    throw new IllegalArgumentException("null or imcomplete consumer capability");
                }

                this.capabilities.add(new CapabilityDTO(dto));
            }
        }
        else {
            this.capabilities = null;
        }
        return this;
    }

    /**
     * Retrieves the hypervisor id field of this ConsumerDTO object.
     *
     * @return the hypervisorId of the consumer, or null if it has not yet been defined
     */
    public HypervisorIdDTO getHypervisorId() {
        return this.hypervisorId;
    }

    /**
     * Sets the hypervisor id on this ConsumerDTO object.
     *
     * @param hypervisorId the hypervisor id to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setHypervisorId(HypervisorIdDTO hypervisorId) {
        this.hypervisorId = hypervisorId;
        return this;
    }

    /**
     * Retrieves the content tags field of this ConsumerDTO object.
     *
     * @return the content tags of the consumer, or null if it has not yet been defined
     */
    public Set<String> getContentTags() {
        return this.contentTags != null ? new SetView<>(this.contentTags) : null;
    }

    /**
     * Sets the contentTags on this ConsumerDTO object.
     *
     * @param contentTags the content tags to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setContentTags(Set<String> contentTags) {
        if (contentTags != null) {
            if (this.contentTags == null) {
                this.contentTags = new HashSet<>();
            }
            else {
                this.contentTags.clear();
            }
            this.contentTags.addAll(contentTags);
        }
        else {
            this.contentTags = null;
        }
        return this;
    }

    /**
     * Retrieves the auto heal field of this ConsumerDTO object.
     *
     * @return true if consumer is set to be auto healed, false otherwise
     */
    public Boolean getAutoheal() {
        return autoheal;
    }

    /**
     * Sets the autoHeal to set on this ConsumerDTO object.
     *
     * @param autoheal the auto heal to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setAutoheal(Boolean autoheal) {
        this.autoheal = autoheal;
        return this;
    }

    /**
     * Retrieves the recipient owner key field of this ConsumerDTO object.
     *
     * @return the recipient owner key of the consumer, or null if it has not yet been defined
     */
    public String getRecipientOwnerKey() {
        return recipientOwnerKey;
    }

    /**
     * Sets the recipient owner key to set on this ConsumerDTO object.
     *
     * @param recipientOwnerKey the recipient owner key to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setRecipientOwnerKey(String recipientOwnerKey) {
        this.recipientOwnerKey = recipientOwnerKey;
        return this;
    }

    /**
     * Retrieves the annotations field of this ConsumerDTO object.
     *
     * @return the annotations of the consumer, or null if it has not yet been defined
     */
    public String getAnnotations() {
        return annotations;
    }

    /**
     * Sets the annotations to set on this ConsumerDTO object.
     *
     * @param annotations the annotations to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setAnnotations(String annotations) {
        this.annotations = annotations;
        return this;
    }

    /**
     * Retrieves the content access mode field of this ConsumerDTO object.
     *
     * @return the content access modes of the consumer, or null if it has not yet been defined
     */
    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    /**
     * Sets the content access mode to set on this ConsumerDTO object.
     *
     * @param contentAccessMode the content access mode to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public ConsumerDTO setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    /**
     * Retrieves the type field of this ConsumerDTO object.
     *
     * @return the type of the consumer, or null if it has not yet been defined
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
     * Retrieves the ID certificate field of this ConsumerDTO object.
     *
     * @return the ID cert of the consumer, or null if it has not yet been defined
     */
    @HateoasArrayExclude
    @JsonProperty("idCert")
    public CertificateDTO getIdCertificate() {
        return this.idCert;
    }

    /**
     * Sets the identity certificate for this ConsumerDTO object.
     *
     * @param idCert the idCert to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonProperty("idCert")
    public ConsumerDTO setIdCertificate(CertificateDTO idCert) {
        this.idCert = idCert;
        return this;
    }

    /**
     * Retrieves the guest ID field of this ConsumerDTO object.
     *
     * @return the guestIds the consumer, or null if it has not yet been defined
     * The rest call however will never return a guestId list, we only accept guestIds.
     */
    public List<GuestIdDTO> getGuestIds() {
        return this.guestIds != null ? new ListView<>(this.guestIds) : null;
    }

    /**
     * Sets the guestIds to set on this ConsumerDTO object.
     *
     * @param guestIds the guestIds to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonProperty
    public ConsumerDTO setGuestIds(List<GuestIdDTO> guestIds) {
        if (guestIds != null) {
            if (this.guestIds == null) {
                this.guestIds = new LinkedList<>();
            }
            else {
                this.guestIds.clear();
            }

            for (GuestIdDTO dto : guestIds) {
                if (dto == null || dto.getGuestId() == null) {
                    throw new IllegalArgumentException("guest is null or incomplete");
                }
                this.guestIds.add(new GuestIdDTO(dto));
            }
        }
        else {
            this.guestIds = null;
        }
        return this;
    }

    /**
     * Adds a guestId to this ConsumerDTO object.
     *
     * @param guestId the guestId to set on this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public boolean addGuestId(GuestIdDTO guestId) {
        if (guestId == null || StringUtils.isEmpty(guestId.getGuestId())) {
            throw new IllegalArgumentException("guestId is null");
        }
        if (guestIds == null) {
            guestIds = new ArrayList<>();
        }
        boolean exists = false;
        for (GuestIdDTO guestIdDTO : guestIds) {
            if (guestIdDTO.getGuestId().contentEquals(guestId.getGuestId())) {
                exists = true;
            }
        }
        if (!exists) {
            return this.guestIds.add(guestId);
        }
        return false;
    }

    /**
     * Removes a guestId from this ConsumerDTO object.
     *
     * @param guestId the guestId to remove from this ConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public boolean removeGuestId(String guestId) {
        if (StringUtils.isEmpty(guestId)) {
            throw new IllegalArgumentException("guestId is null");
        }

        if (this.guestIds != null) {
            for (GuestIdDTO guestIdDTO : this.getGuestIds()) {
                if (guestIdDTO.getGuestId().contentEquals(guestId)) {
                    return this.guestIds.remove(guestIdDTO);
                }
            }
        }
        return false;
    }

    /**
     * Returns true if ConsumerDTO object is a guest, false otherwise.
     *
     * @return true if this consumer is a guest, false otherwise
     */
    @JsonIgnore
    public boolean isGuest() {
        return "true".equalsIgnoreCase(this.getFact("virt.is_guest"));
    }

    /**
     * Returns the value of the fact with the given key.
     * @param factKey specific fact to retrieve.
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        if (facts != null) {
            return facts.get(factKey);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @HateoasInclude
    public String getHref() {
        return this.getUuid() != null ? String.format("/consumers/%s", this.getUuid()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        String ownerId = this.getOwner() != null ? this.getOwner().getId() : null;
        return String.format("ConsumerDTO [uuid: %s, name: %s, owner id: %s]",
            this.getUuid(), this.getName(), ownerId);
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

            String thisOid = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOid = that.getOwner() != null ? that.getOwner().getId() : null;

            String thisEnvId = this.getEnvironment() != null ? this.getEnvironment().getId() : null;
            String thatEnvId = that.getEnvironment() != null ? that.getEnvironment().getId() : null;

            String thisHypervisorId = this.getHypervisorId() != null ? this.getHypervisorId().getId() : null;
            String thatHypervisorId = that.getHypervisorId() != null ? that.getHypervisorId().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getUuid(), that.getUuid())
                .append(this.getName(), that.getName())
                .append(this.getUsername(), that.getUsername())
                .append(this.getEntitlementStatus(), that.getEntitlementStatus())
                .append(this.getServiceLevel(), that.getServiceLevel())
                .append(this.getReleaseVersion(), that.getReleaseVersion())
                .append(thisOid, thatOid)
                .append(thisEnvId, thatEnvId)
                .append(this.getEntitlementCount(), that.getEntitlementCount())
                .append(this.getFacts(), that.getFacts())
                .append(this.getLastCheckin(), that.getLastCheckin())
                .append(this.getInstalledProducts(), that.getInstalledProducts())
                .append(this.isCanActivate(), that.isCanActivate())
                .append(this.getCapabilities(), that.getCapabilities())
                .append(thisHypervisorId, thatHypervisorId)
                .append(this.getContentTags(), that.getContentTags())
                .append(this.getAutoheal(), that.getAutoheal())
                .append(this.getRecipientOwnerKey(), that.getRecipientOwnerKey())
                .append(this.getAnnotations(), that.getAnnotations())
                .append(this.getContentAccessMode(), that.getContentAccessMode())
                .append(this.getType(), that.getType())
                .append(this.getIdCertificate(), that.getIdCertificate())
                .append(this.getGuestIds(), that.getGuestIds());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        String oid = this.getOwner() != null ? this.getOwner().getId() : null;
        String envId = this.getEnvironment() != null ? this.getEnvironment().getId() : null;
        String hypervisorId = this.getHypervisorId() != null ? this.getHypervisorId().getId() : null;

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getUuid())
            .append(this.getName())
            .append(this.getUsername())
            .append(this.getEntitlementStatus())
            .append(this.getServiceLevel())
            .append(this.getReleaseVersion())
            .append(oid)
            .append(envId)
            .append(this.getEntitlementCount())
            .append(this.getFacts())
            .append(this.getLastCheckin())
            .append(this.getInstalledProducts())
            .append(this.isCanActivate())
            .append(this.getCapabilities())
            .append(hypervisorId)
            .append(this.getContentTags())
            .append(this.getAutoheal())
            .append(this.getRecipientOwnerKey())
            .append(this.getAnnotations())
            .append(this.getContentAccessMode())
            .append(this.getType())
            .append(this.getIdCertificate())
            .append(this.getGuestIds());

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

        EnvironmentDTO environment = this.getEnvironment();
        copy.setEnvironment(environment != null ? environment.clone() : null);

        HypervisorIdDTO hid = this.getHypervisorId();
        copy.setHypervisorId(hid != null ? hid.clone() : null);

        ConsumerTypeDTO ctype = this.getType();
        copy.setType(ctype != null ? ctype.clone() : null);

        CertificateDTO idCert = this.getIdCertificate();
        copy.setIdCertificate(idCert != null ? idCert.clone() : null);

        copy.setFacts(this.getFacts());
        copy.setInstalledProducts(this.getInstalledProducts());
        copy.setCapabilities(this.getCapabilities());
        copy.setContentTags(this.getContentTags());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ConsumerDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setUuid(source.getUuid());
        this.setName(source.getName());
        this.setUsername(source.getUsername());
        this.setEntitlementStatus(source.getEntitlementStatus());
        this.setServiceLevel(source.getServiceLevel());
        this.setReleaseVersion(source.getReleaseVersion());
        this.setOwner(source.getOwner());
        this.setEnvironment(source.getEnvironment());
        this.setEntitlementCount(source.getEntitlementCount());
        this.setFacts(source.getFacts());
        this.setLastCheckin(source.getLastCheckin());
        this.setInstalledProducts(source.getInstalledProducts());
        this.setCanActivate(source.isCanActivate());
        this.setCapabilities(source.getCapabilities());
        this.setGuestIds(source.getGuestIds());
        this.setHypervisorId(source.getHypervisorId());
        this.setContentTags(source.getContentTags());
        this.setAutoheal(source.getAutoheal());
        this.setRecipientOwnerKey(source.getRecipientOwnerKey());
        this.setAnnotations(source.getAnnotations());
        this.setContentAccessMode(source.getContentAccessMode());
        this.setType(source.getType());
        this.setIdCertificate(source.getIdCertificate());

        return this;
    }
}

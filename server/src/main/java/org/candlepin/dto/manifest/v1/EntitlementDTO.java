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
package org.candlepin.dto.manifest.v1;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;



/**
 * A DTO representation of the Entitlement entity as used by the manifest import/export framework.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("EntitlementFilter")
public class EntitlementDTO extends TimestampedCandlepinDTO<EntitlementDTO> {

    private static final long serialVersionUID = 1L;

    /* TODO: Some fields that used to be exported, are no longer, so they are missing from this DTO.
     * A decision needs to be made if any of them will need to be included.
     *
     * Fields we don't export any more
     * (because they are not accessed or they are overriden during import anyway):
     * - entitlement.href
     * - entitlement.startDate
     * - entitlement.endDate
     * - entitlement.created
     * - entitlement.updated
     *
     * - entitlement.certificates.created
     * - entitlement.certificates.updated
     *
     * - entitlement.certificates.serial.revoked
     * - entitlement.certificates.serial.serial
     *
     * - entitlement.pool.created
     * - entitlement.pool.updated
     * - entitlement.pool.type
     * - entitlement.pool.owner
     * - entitlement.pool.activeSubscription
     * - entitlement.pool.createdByShare
     * - entitlement.pool.hasSharedAncestor
     * - entitlement.pool.sourceEntitlement
     * - entitlement.pool.quantity
     * - entitlement.pool.startDate
     * - entitlement.pool.endDate
     * - entitlement.pool.attributes
     * - entitlement.pool.restrictedToUsername
     * - entitlement.pool.consumed
     * - entitlement.pool.exported
     * - entitlement.pool.shared
     * - entitlement.pool.calculatedAttributes
     * - entitlement.pool.upstreamPoolId
     * - entitlement.pool.upstreamEntitlementId
     * - entitlement.pool.upstreamConsumerId
     * - entitlement.pool.productAttributes
     * - entitlement.pool.derivedProductAttributes
     * - entitlement.pool.derivedProductName
     * - entitlement.pool.stacked
     * - entitlement.pool.stackId
     * - entitlement.pool.developmentPool
     * - entitlement.pool.sourceStackId
     * - entitlement.pool.subscriptionSubKey
     * - entitlement.pool.subscriptionId
     *
     * - entitlement.pool.branding.id (not to be confused with productId)
     * - entitlement.pool.branding.created
     * - entitlement.pool.branding.updated
     *
     */

    private String id;
    private OwnerDTO owner;
    private ConsumerDTO consumer;
    private PoolDTO pool;
    private Integer quantity;
    private Boolean deletedFromPool;
    private Set<CertificateDTO> certificates;
    private Date startDate;
    private Date endDate;

    /**
     * Initializes a new EntitlementDTO instance with null values.
     */
    public EntitlementDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new EntitlementDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public EntitlementDTO(EntitlementDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this EntitlementDTO object.
     *
     * @return the id field of this EntitlementDTO object.
     */
    @HateoasInclude
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this EntitlementDTO object.
     *
     * @param id the id to set on this EntitlementDTO object.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the owner of this entitlement.
     *
     * @return the owner of this entitlement.
     */
    @JsonIgnore
    public OwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner of this entitlement.
     *
     * @param owner the owner to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Returns the consumer of this entitlement.
     *
     * @return return the associated Consumer.
     */
    public ConsumerDTO getConsumer() {
        return consumer;
    }

    /**
     * Associates the given consumer with this entitlement.
     *
     * @param consumer consumer to associate.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setConsumer(ConsumerDTO consumer) {
        this.consumer = consumer;
        return this;
    }

    /**
     * Returns the pool of this entitlement.
     *
     * @return the pool of this entitlement.
     */
    public PoolDTO getPool() {
        return this.pool;
    }

    /**
     * Sets the pool of this entitlement.
     *
     * @param pool the pool to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setPool(PoolDTO pool) {
        this.pool = pool;
        return this;
    }

    /**
     * Retrieves the quantity of this entitlement.
     *
     * @return the quantity of this entitlement.
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity of this entitlement.
     *
     * @param quantity the quantity to set.
     *
     * @return a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * Returns true if this entitlement is deleted from the pool, or false otherwise.
     *
     * @return if this entitlement is deleted from the pool or not.
     */
    @JsonIgnore
    public Boolean isDeletedFromPool() {
        return deletedFromPool;
    }

    /**
     * Sets if this entitlement is is deleted from the pool or not.
     *
     * @param deletedFromPool if this entitlement is deleted from the pool or not.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonProperty
    public EntitlementDTO setDeletedFromPool(Boolean deletedFromPool) {
        this.deletedFromPool = deletedFromPool;
        return this;
    }

    /**
     * Retrieves a view of the certificates associated with the entitlement represented by this DTO.
     * If the certificates have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of certificates. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this certificates data instance.
     *
     * @return
     *  the certificates associated with this key, or null if the certificates have not yet been defined
     */
    public Set<CertificateDTO> getCertificates() {
        return this.certificates != null ? new SetView<>(this.certificates) : null;
    }

    /**
     * Sets the certificates of the entitlement represented by this DTO.
     *
     * @param certificates
     *  A collection of certificate DTOs to attach to this entitlement DTO, or null to clear the content
     *
     * @throws IllegalArgumentException
     *  if the collection contains null or incomplete certificate DTOs
     *
     * @return
     *  a reference to this EntitlementDTO object.
     */
    public EntitlementDTO setCertificates(Set<CertificateDTO> certificates) {
        if (certificates != null) {
            if (this.certificates == null) {
                this.certificates = new HashSet<>();
            }
            else {
                this.certificates.clear();
            }

            for (CertificateDTO dto : certificates) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete certificates");
                }
            }
            this.certificates.addAll(certificates);
        }
        else {
            this.certificates = null;
        }
        return this;
    }

    /**
     * Adds the given certificate to this entitlement DTO.
     *
     * @param certificate the certificate DTO to add to this entitlement DTO.
     *
     * @throws IllegalArgumentException
     *  if the certificate is null or incomplete
     *
     * @return true if this certificate was not already contained in this entitlement DTO.
     */
    @JsonIgnore
    public boolean addCertificate(CertificateDTO certificate) {
        if (isNullOrIncomplete(certificate)) {
            throw new IllegalArgumentException("certificate is null or incomplete");
        }

        if (this.certificates == null) {
            this.certificates = new HashSet<>();
        }

        return this.certificates.add(certificate);
    }

    /*
     * Utility method to validate certificate input.
     */
    private boolean isNullOrIncomplete(CertificateDTO certificate) {
        return certificate == null || certificate.getSerial() == null ||
            certificate.getId() == null || certificate.getId().isEmpty() ||
            certificate.getKey() == null || certificate.getKey().isEmpty() ||
            certificate.getCert() == null || certificate.getCert().isEmpty();
    }

    /**
     * Returns the start date of this entitlement.
     *
     * @return Returns the startDate from the pool of this entitlement.
     */
    @JsonProperty
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of this entitlement.
     *
     * @param startDate the startDate of this entitlement.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonIgnore
    public EntitlementDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Returns the end date of this entitlement.
     *
     * @return Returns the endDate of this entitlement.
     */
    @JsonProperty
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date of this entitlement.
     *
     * @param endDate the endDate of this entitlement.
     *
     * @return a reference to this EntitlementDTO object.
     */
    @JsonIgnore
    public EntitlementDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EntitlementDTO [id: %s, product id: %s, pool id: %s, consumer uuid: %s]",
            this.id,
            this.pool != null ? pool.getProductId() : null,
            this.pool != null ? pool.getId() : null,
            this.consumer != null ? consumer.getUuid() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EntitlementDTO && super.equals(obj)) {
            EntitlementDTO that = (EntitlementDTO) obj;

            // Pull the nested object IDs, as we're not interested in verifying that the objects
            // themselves are equal; just so long as they point to the same object.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            String thisPoolId = this.getPool() != null ? this.getPool().getId() : null;
            String thatPoolId = that.getPool() != null ? that.getPool().getId() : null;

            String thisConsumerId = this.getConsumer() != null ? this.getConsumer().getUuid() : null;
            String thatConsumerId = that.getConsumer() != null ? that.getConsumer().getUuid() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(thisOwnerId, thatOwnerId)
                .append(thisPoolId, thatPoolId)
                .append(thisConsumerId, thatConsumerId)
                .append(this.getQuantity(), that.getQuantity())
                .append(this.isDeletedFromPool(), that.isDeletedFromPool())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getStartDate(), that.getStartDate());

            // Note that we're using the boolean operator here as a shorthand way to skip checks
            // when the equality check has already failed.
            boolean equals = builder.isEquals();

            equals = equals && Util.collectionsAreEqual(this.getCertificates(), that.getCertificates(),
                (c1, c2) -> {
                    if (c1 == c2) {
                        return 0;
                    }

                    if (c1 != null && c1.getId() != null) {
                        return c1.getId().compareTo(c2.getId());
                    }

                    return 1;
                });

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int certsHashCode = 0;
        Collection<CertificateDTO> certificateDTOs = this.getCertificates();

        if (certificateDTOs != null) {
            for (CertificateDTO dto : certificateDTOs) {
                certsHashCode = 31 * certsHashCode +
                    (dto != null && dto.getId() != null ? dto.getId().hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getOwner() != null ? this.getOwner().getId() : null)
            .append(this.getPool() != null ? this.getPool().getId() : null)
            .append(this.getConsumer() != null ? this.getConsumer().getUuid() : null)
            .append(this.getQuantity())
            .append(this.isDeletedFromPool())
            .append(this.getEndDate())
            .append(this.getStartDate())
            .append(certsHashCode);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO clone() {
        EntitlementDTO copy = (EntitlementDTO) super.clone();

        OwnerDTO owner = this.getOwner();
        copy.owner = owner != null ? owner.clone() : null;

        PoolDTO pool = this.getPool();
        copy.pool = pool != null ? pool.clone() : null;

        ConsumerDTO consumer = this.getConsumer();
        copy.consumer = consumer != null ? consumer.clone() : null;

        copy.setCertificates(this.getCertificates());

        copy.endDate = this.endDate != null ? (Date) this.endDate.clone() : null;
        copy.startDate = this.startDate != null ? (Date) this.startDate.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(EntitlementDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setOwner(source.getOwner())
            .setPool(source.getPool())
            .setConsumer(source.getConsumer())
            .setQuantity(source.getQuantity())
            .setDeletedFromPool(source.isDeletedFromPool())
            .setCertificates(source.getCertificates())
            .setEndDate(source.getEndDate())
            .setStartDate(source.getStartDate());

        return this;
    }
}

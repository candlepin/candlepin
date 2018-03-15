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
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;



/**
 * A DTO representation of the Entitlement entity as used by the manifest import/export framework.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("EntitlementFilter")
public class EntitlementDTO extends CandlepinDTO<EntitlementDTO> {

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
    private PoolDTO pool;
    private Integer quantity;
    private Set<CertificateDTO> certificates;

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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EntitlementDTO [id: %s, product id: %s, pool id: %s]",
            this.id,
            this.pool != null ? pool.getProductId() : null,
            this.pool != null ? pool.getId() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EntitlementDTO) {
            EntitlementDTO that = (EntitlementDTO) obj;

            String thisPoolId = this.getPool() != null ? this.getPool().getId() : null;
            String thatPoolId = that.getPool() != null ? that.getPool().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(thisPoolId, thatPoolId)
                .append(this.getQuantity(), that.getQuantity());

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
            .append(this.getId())
            .append(this.getPool() != null ? this.getPool().getId() : null)
            .append(this.getQuantity())
            .append(certsHashCode);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO clone() {
        EntitlementDTO copy = super.clone();

        PoolDTO pool = this.getPool();
        copy.pool = pool != null ? pool.clone() : null;

        copy.setCertificates(this.getCertificates());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(EntitlementDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setPool(source.getPool())
            .setQuantity(source.getQuantity())
            .setCertificates(source.getCertificates());

        return this;
    }
}

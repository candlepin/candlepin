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

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * The UeberCertificateDTO is a DTO representing an ueber certificate
 */
@ApiModel(parent = CertificateDTO.class, description = "DTO representing an ueber certificate")
public class UeberCertificateDTO extends AbstractCertificateDTO<UeberCertificateDTO> {
    public static final long serialVersionUID = 1L;

    protected NestedOwnerDTO owner;


    /**
     * Initializes a new UeberCertificateDTO instance with null values.
     */
    public UeberCertificateDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new UeberCertificateDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public UeberCertificateDTO(UeberCertificateDTO source) {
        this.populate(source);
    }

    /**
     * Fetches the owner set for this certificate. If the owner has not yet been set, this method
     * returns null.
     *
     * @return
     *  The owner of this certificate, or null if the owner has not been set
     */
    public NestedOwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets or clears the owner of this certificate.
     *
     * @param owner
     *  The owner to set as the owner of this certificate, or null to clear the owner
     *
     * @return
     *  a reference to this DTO
     */
    public UeberCertificateDTO  setOwner(NestedOwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        CertificateSerialDTO serial = this.getSerial();
        NestedOwnerDTO owner = this.getOwner();

        return String.format("UeberCertificateDTO [id: %s, owner id: %s, key: %s, serial id: %s]",
            this.getId(), owner != null ? owner.getId() : null, this.getKey(),
            serial != null ? serial.getId() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof UeberCertificateDTO && super.equals(obj)) {
            UeberCertificateDTO that = (UeberCertificateDTO) obj;

            NestedOwnerDTO thisOwner = this.getOwner();
            NestedOwnerDTO thatOwner = that.getOwner();
            String thisOID = thisOwner != null ? thisOwner.getId() : null;
            String thatOID = thatOwner != null ? thatOwner.getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(thisOID, thatOID);

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        NestedOwnerDTO owner = this.getOwner();

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(owner != null ? owner.getId() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO clone() {
        UeberCertificateDTO copy = (UeberCertificateDTO) super.clone();

        NestedOwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? new NestedOwnerDTO()
            .id(owner.getId())
            .displayName(owner.getDisplayName())
            .href(owner.getHref())
            .key(owner.getKey()) : null);

        return copy;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO populate(UeberCertificateDTO source) {
        super.populate(source);

        this.setOwner(source.getOwner());

        return this;
    }
}

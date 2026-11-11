/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.service.model.CertificateSerialInfo;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.math.BigInteger;
import java.util.Date;


/**
 * A DTO representation of the CertificateSerial entity for version 2 manifests.
 */
public class CertificateSerialDTO extends TimestampedCandlepinDTO<CertificateSerialDTO>
    implements CertificateSerialInfo {

    public static final long serialVersionUID = 1L;

    private Long id;
    private BigInteger serial;
    private Date expiration;
    private Boolean revoked;
    // we export this to false by default now that the CRL functionality is removed to avoid breaking old
    // candlepin imports.
    private Boolean collected;

    /**
     * Initializes an empty certificate serial DTO with collected set to false
     */
    public CertificateSerialDTO() {
        this.collected = Boolean.FALSE;
    }

    /**
     * Initializes a new CertificateSerialDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CertificateSerialDTO(CertificateSerialDTO source) {
        super(source);
    }

    /**
     * Id value for the exported certificate
     *
     * @return the id value, or null if unset
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Sets the id value
     *
     * @param id
     *     value to store, or null to clear it
     * @return this DTO
     */
    public CertificateSerialDTO setId(Long id) {
        this.id = id;
        return this;
    }

    /**
     * Serial value for the exported certificate
     *
     * @return the serial value, or null if unset
     */
    public BigInteger getSerial() {
        return this.serial;
    }

    /**
     * Sets the serial value
     *
     * @param serial
     *     value to store, or null to clear it
     * @return this DTO
     */
    public CertificateSerialDTO setSerial(BigInteger serial) {
        this.serial = serial;
        return this;
    }

    /**
     * Expiration value for the exported certificate
     *
     * @return the expiration value, or null if unset
     */
    public Date getExpiration() {
        return this.expiration;
    }

    /**
     * Sets the expiration value
     *
     * @param expiration
     *     value to store, or null to clear it
     * @return this DTO
     */
    public CertificateSerialDTO setExpiration(Date expiration) {
        this.expiration = expiration;
        return this;
    }

    /**
     * Revoked value for the exported certificate
     *
     * @return the revoked value, or null if unset
     */
    public Boolean isRevoked() {
        return this.revoked;
    }

    /**
     * Sets the revoked value
     *
     * @param revoked
     *     value to store, or null to clear it
     * @return this DTO
     */
    public CertificateSerialDTO setRevoked(Boolean revoked) {
        this.revoked = revoked;
        return this;
    }
    /**
     * Collected value for the exported certificate
     *
     * @return the collected value, or null if unset
     */
    public Boolean isCollected() {
        return this.collected;
    }

    /**
     * Sets the collected value
     *
     * @param collected
     *     collected state, or null to clear it; manifest translation resets this value to false
     * @return this DTO
     */
    public CertificateSerialDTO setCollected(Boolean collected) {
        this.collected = collected;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        Date expiration = this.getExpiration();
        String date = expiration != null ? String.format("%1$tF %1$tT%1$tz", expiration) : null;

        return String.format(
            "CertificateSerialDTO [id: %s, serial: %s, expiration: %s, collected: %s, revoked: %s]",
            this.getId(), this.getSerial(), date, this.isCollected(), this.isRevoked());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CertificateSerialDTO && super.equals(obj)) {
            CertificateSerialDTO that = (CertificateSerialDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getSerial(), that.getSerial())
                .append(this.getExpiration(), that.getExpiration())
                .append(this.isCollected(), that.isCollected())
                .append(this.isRevoked(), that.isRevoked());

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
            .append(this.getId())
            .append(this.getSerial())
            .append(this.getExpiration())
            .append(this.isCollected())
            .append(this.isRevoked());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO clone() {
        CertificateSerialDTO copy = super.clone();

        Date expiration = this.getExpiration();
        copy.setExpiration(expiration != null ? (Date) expiration.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO populate(CertificateSerialDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setSerial(source.getSerial());
        this.setExpiration(source.getExpiration());
        this.setRevoked(source.isRevoked());
        this.setCollected(source.isCollected());

        return this;
    }
}

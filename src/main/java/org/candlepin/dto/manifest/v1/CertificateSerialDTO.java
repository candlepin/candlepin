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

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.service.model.CertificateSerialInfo;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.math.BigInteger;
import java.util.Date;


/**
 * A DTO representation of the CertificateSerial entity as used by the manifest import/export framework.
 */
public class CertificateSerialDTO extends TimestampedCandlepinDTO<CertificateSerialDTO>
    implements CertificateSerialInfo {

    public static final long serialVersionUID = 1L;

    protected Long id;
    protected BigInteger serial;
    protected Date expiration;
    protected Boolean revoked;

    /**
     * Initializes a new CertificateSerialDTO instance with null values.
     */
    public CertificateSerialDTO() {
        // Intentionally left empty
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

    public Long getId() {
        return this.id;
    }

    public CertificateSerialDTO setId(Long id) {
        this.id = id;
        return this;
    }

    public BigInteger getSerial() {
        return this.serial;
    }

    public CertificateSerialDTO setSerial(BigInteger serial) {
        this.serial = serial;
        return this;
    }

    public Date getExpiration() {
        return this.expiration;
    }

    public CertificateSerialDTO setExpiration(Date expiration) {
        this.expiration = expiration;
        return this;
    }

    public Boolean isRevoked() {
        return this.revoked;
    }

    public CertificateSerialDTO setRevoked(Boolean revoked) {
        this.revoked = revoked;
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
            "CertificateSerialDTO [id: %s, serial: %s, expiration: %s, revoked: %s]",
            this.getId(), this.getSerial(), date, this.isRevoked());
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

        return this;
    }
}

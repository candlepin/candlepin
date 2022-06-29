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
import org.candlepin.service.model.CertificateInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;



/**
 * The CertificateDTO is a DTO representing most Candlepin certificates
 * as used by the manifest import/export framework.
 */
public class CertificateDTO extends TimestampedCandlepinDTO<CertificateDTO> implements CertificateInfo {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String key;
    protected String cert;
    protected CertificateSerialDTO serial;

    /**
     * Initializes a new CertificateDTO instance with null values.
     */
    public CertificateDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CertificateDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CertificateDTO(CertificateDTO source) {
        super(source);
    }

    public String getId() {
        return this.id;
    }

    public CertificateDTO setId(String id) {
        this.id = id;
        return this;
    }

    public String getKey() {
        return this.key;
    }

    public CertificateDTO setKey(String key) {
        this.key = key;
        return this;
    }

    @Override
    @JsonIgnore
    public byte[] getPrivateKey() {
        return this.key != null ? this.key.getBytes() : null;
    }

    @JsonProperty("cert")
    public String getCert() {
        return this.cert;
    }

    @JsonProperty("cert")
    public CertificateDTO setCert(String cert) {
        this.cert = cert;
        return this;
    }

    @Override
    @JsonIgnore
    public byte[] getCertificate() {
        return this.cert != null ? this.cert.getBytes() : null;
    }

    @Override
    @JsonIgnore
    public byte[] getPayload() {
        // We didn't make this distiction before, so we'll never have a payload here
        return null;
    }

    @JsonProperty("serial")
    public CertificateSerialDTO getSerialDTO() {
        return this.serial;
    }

    @JsonProperty("serial")
    public CertificateDTO setSerialDTO(CertificateSerialDTO serial) {
        this.serial = serial;
        return this;
    }

    @Override
    @JsonIgnore
    public BigInteger getSerial() {
        return this.serial != null ? this.serial.getSerial() : null;
    }

    @Override
    @JsonIgnore
    public Boolean isRevoked() {
        return this.serial != null ? this.serial.isRevoked() : false;
    }

    @Override
    @JsonIgnore
    public Instant getExpiration() {
        if (this.serial == null) {
            return null;
        }

        Date expiration = this.serial.getExpiration();
        return expiration != null ? expiration.toInstant() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("CertificateDTO [id: %s, key: %s, serial: %s]",
            this.getId(), this.getKey(), this.getSerial());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CertificateDTO && super.equals(obj)) {
            CertificateDTO that = (CertificateDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getKey(), that.getKey())
                .append(this.getCert(), that.getCert())
                .append(this.getSerialDTO(), that.getSerialDTO());

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
            .append(this.getKey())
            .append(this.getCert())
            .append(this.getSerialDTO());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO clone() {
        CertificateDTO copy = super.clone();

        CertificateSerialDTO serial = this.getSerialDTO();
        copy.setSerialDTO(serial != null ? serial.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(CertificateDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setKey(source.getKey());
        this.setCert(source.getCert());
        this.setSerialDTO(source.getSerialDTO());

        return this;
    }
}

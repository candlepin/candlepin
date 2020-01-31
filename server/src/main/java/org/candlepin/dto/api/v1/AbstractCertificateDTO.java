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

import org.candlepin.dto.TimestampedCandlepinDTO;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * The AbstractCertificateDTO is a DTO representing the base of most Candlepin certificates
 * presented to the API (exceptions include ProductCertificate which has its own DTO).
 *
 * @param <T>
 *  The DTO type extending this class; should be the name of the subclass
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a certificate")
public abstract class AbstractCertificateDTO<T extends AbstractCertificateDTO>
    extends TimestampedCandlepinDTO<T> {

    public static final long serialVersionUID = 1L;

    protected String id;
    protected String key;
    protected String cert;
    protected CertificateSerialDTO serial;


    /**
     * Initializes a new CertificateDTO instance with null values.
     */
    public AbstractCertificateDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CertificateDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public AbstractCertificateDTO(T source) {
        super(source);
    }

    public String getId() {
        return this.id;
    }

    public T setId(String id) {
        this.id = id;
        return (T) this;
    }

    public String getKey() {
        return this.key;
    }

    public T setKey(String key) {
        this.key = key;
        return (T) this;
    }

    public String getCert() {
        return this.cert;
    }

    public T setCert(String cert) {
        this.cert = cert;
        return (T) this;
    }

    public CertificateSerialDTO getSerial() {
        return this.serial;
    }

    public T setSerial(CertificateSerialDTO serial) {
        this.serial = serial;
        return (T) this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        CertificateSerialDTO serial = this.getSerial();

        return String.format("AbstractCertificateDTO [id: %s, key: %s, serial id: %s]",
            this.getId(), this.getKey(), serial != null ? serial.getId() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof AbstractCertificateDTO && super.equals(obj)) {
            AbstractCertificateDTO that = (AbstractCertificateDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getKey(), that.getKey())
                .append(this.getCert(), that.getCert())
                .append(this.getSerial(), that.getSerial());

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
            .append(this.getSerial());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T clone() {
        T copy = (T) super.clone();

        CertificateSerialDTO serial = this.getSerial();
        if (serial != null) {
            copy.setSerial(
                new CertificateSerialDTO()
                .id(serial.getId())
                .serial(serial.getSerial())
                .expiration(serial.getExpiration())
                .collected(serial.getCollected())
                .revoked(serial.getRevoked())
                .created(serial.getCreated())
                .updated(serial.getUpdated()));
        }

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T populate(T source) {
        super.populate(source);

        this.setId(source.getId());
        this.setKey(source.getKey());
        this.setCert(source.getCert());
        this.setSerial(source.getSerial());

        return (T) this;
    }
}

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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * A DTO representation of the UpstreamConsumer entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an upstream consumer")
public class UpstreamConsumerDTO extends TimestampedCandlepinDTO<UpstreamConsumerDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String uuid;
    protected String name;
    protected String apiUrl;
    protected String webUrl;
    protected String ownerId;
    protected String contentAccessMode;
    protected ConsumerTypeDTO consumerType;
    protected CertificateDTO identityCert;

    /**
     * Initializes a new UpstreamConsumerDTO instance with null values.
     */
    public UpstreamConsumerDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new UpstreamConsumerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public UpstreamConsumerDTO(UpstreamConsumerDTO source) {
        super(source);
    }

    public String getId() {
        return this.id;
    }

    public UpstreamConsumerDTO setId(String id) {
        this.id = id;
        return this;
    }

    public String getUuid() {
        return this.uuid;
    }

    public UpstreamConsumerDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public UpstreamConsumerDTO setName(String name) {
        this.name = name;
        return this;
    }

    public String getApiUrl() {
        return this.apiUrl;
    }

    public UpstreamConsumerDTO setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        return this;
    }

    public String getWebUrl() {
        return this.webUrl;
    }

    public UpstreamConsumerDTO setWebUrl(String webUrl) {
        this.webUrl = webUrl;
        return this;
    }

    public String getOwnerId() {
        return this.ownerId;
    }

    public UpstreamConsumerDTO setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    @JsonProperty("type")
    public ConsumerTypeDTO getConsumerType() {
        return this.consumerType;
    }

    @JsonProperty("type")
    public UpstreamConsumerDTO setConsumerType(ConsumerTypeDTO consumerType) {
        this.consumerType = consumerType;
        return this;
    }

    @JsonProperty("idCert")
    public CertificateDTO getIdentityCertificate() {
        return this.identityCert;
    }

    @JsonProperty("idCert")
    public UpstreamConsumerDTO setIdentityCertificate(CertificateDTO identityCert) {
        this.identityCert = identityCert;
        return this;
    }

    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    public UpstreamConsumerDTO setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("UpstreamConsumerDTO [uuid: %s, name: %s, owner id: %s]",
            this.getUuid(), this.getName(), this.getOwnerId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof UpstreamConsumerDTO && super.equals(obj)) {
            UpstreamConsumerDTO that = (UpstreamConsumerDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getUuid(), that.getUuid())
                .append(this.getName(), that.getName())
                .append(this.getApiUrl(), that.getApiUrl())
                .append(this.getWebUrl(), that.getWebUrl())
                .append(this.getOwnerId(), that.getOwnerId())
                .append(this.getConsumerType(), that.getConsumerType())
                .append(this.getIdentityCertificate(), that.getIdentityCertificate());

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
            .append(this.getUuid())
            .append(this.getName())
            .append(this.getApiUrl())
            .append(this.getWebUrl())
            .append(this.getOwnerId())
            .append(this.getConsumerType())
            .append(this.getIdentityCertificate());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTO clone() {
        UpstreamConsumerDTO copy = super.clone();

        ConsumerTypeDTO type = this.getConsumerType();
        copy.consumerType = type != null ? (ConsumerTypeDTO) type.clone() : null;

        CertificateDTO cert = this.getIdentityCertificate();
        copy.identityCert = cert != null ? (CertificateDTO) cert.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTO populate(UpstreamConsumerDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setUuid(source.getUuid());
        this.setName(source.getName());
        this.setApiUrl(source.getApiUrl());
        this.setWebUrl(source.getWebUrl());
        this.setOwnerId(source.getOwnerId());
        this.setConsumerType(source.getConsumerType());
        this.setIdentityCertificate(source.getIdentityCertificate());

        return this;
    }
}

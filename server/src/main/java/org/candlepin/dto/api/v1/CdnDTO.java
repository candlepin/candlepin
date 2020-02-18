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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A DTO representation of the Cdn entity
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a Cdn")
public class CdnDTO extends TimestampedCandlepinDTO<CdnDTO> {
    public static final long serialVersionUID = 1L;

    private String id;
    private String label;
    private String name;
    private String url;
    private CertificateDTO cert;

    /**
     * Initializes a new CdnDTO instance with null values.
     */
    public CdnDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CdnDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CdnDTO(CdnDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this CdnDTO object.
     *
     * @return the id field of this CdnDTO object.
     */
    @JsonProperty
    public String getId() {
        return id;
    }

    /**
     * Sets the id to set on this CdnDTO object.
     *
     * @param id the id to set on this CdnDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonIgnore
    public CdnDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the label of this CdnDTO object.
     *
     * @return the label of this CdnDTO object.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label of this CdnDTO object.
     *
     * @param label the label of this CdnDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CdnDTO setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Retrieves the name of this CdnDTO object.
     *
     * @return the name of this CdnDTO object.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this CdnDTO object.
     *
     * @param name the name of this CdnDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CdnDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the url of this CdnDTO object.
     *
     * @return the url of this CdnDTO object.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the url of this CdnDTO object.
     *
     * @param url the url of this CdnDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CdnDTO setUrl(String url) {
        this.url = url;
        return this;
    }

    /**
     * Retrieves the CdnCertificate of this CdnDTO object.
     *
     * @return the CdnCertificate of this CdnDTO object.
     */
    public CertificateDTO getCertificate() {
        return cert;
    }

    /**
     * Sets the CdnCertificate of this CdnDTO object.
     *
     * @param cert the CdnCertificate of this CdnDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CdnDTO setCertificate(CertificateDTO cert) {
        this.cert = cert;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("CdnDTO [id: %s, name: %s, label: %s]",
            this.getId(), this.getName(), this.getLabel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CdnDTO && super.equals(obj)) {
            CdnDTO that = (CdnDTO) obj;

            String thisCdnCertId = this.getCertificate() != null ? this.getCertificate().getId() : null;
            String thatCdnCertId = that.getCertificate() != null ? that.getCertificate().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getLabel(), that.getLabel())
                .append(thisCdnCertId, thatCdnCertId)
                .append(this.getUrl(), that.getUrl());

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
            .append(this.getName())
            .append(this.getLabel())
            .append(this.getCertificate() != null ? this.getCertificate().getId() : null)
            .append(this.getUrl());

        return builder.toHashCode();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO clone() {
        CdnDTO copy = super.clone();
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO populate(CdnDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setName(source.getName())
            .setLabel(source.getLabel())
            .setUrl(source.getUrl())
            .setCertificate(source.getCertificate());

        return this;
    }
}

/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import org.candlepin.common.jackson.HateoasArrayExclude;
import org.candlepin.common.jackson.HateoasInclude;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * UpstreamConsumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = UpstreamConsumer.DB_TABLE)
@JsonFilter("ApiHateoas")
public class UpstreamConsumer extends AbstractHibernateObject<UpstreamConsumer> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_upstream_consumer";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @OneToOne
    @JoinColumn(name = "consumer_idcert_id")
    private IdentityCertificate idCert;

    @ManyToOne
    @JoinColumn(nullable = false)
    @ForeignKey(name = "fk_upstream_consumer_type")
    @NotNull
    private ConsumerType type;

    @Column(name = "owner_id", length = 32, nullable = false)
    @NotNull
    private String ownerId;

    @Column(name = "prefix_url_web")
    @Size(max = 255)
    private String prefixUrlWeb;

    @Column(name = "prefix_url_api")
    @Size(max = 255)
    private String prefixUrlApi;

    @Column(name = "content_access_mode")
    @Size(max = 255)
    private String contentAccessMode;

    public UpstreamConsumer(String name, Owner owner, ConsumerType type, String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid is null");
        }

        this.name = name;
        if (owner != null) {
            this.ownerId = owner.getId();
        }
        this.type = type;
        this.uuid = uuid;
    }

    public UpstreamConsumer(String uuid) {
        this(null, null, null, uuid);
    }

    public UpstreamConsumer() {
        // needed for Hibernate
        this("");
    }

    /**
     * @return the Consumer's UUID
     */
    @HateoasInclude
    public String getUuid() {
        return uuid;
    }

    /**
     * @param uuid the UUID of this consumer.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the db id.
     */
    public void setId(String id) {
        this.id = id;
    }

    @HateoasArrayExclude
    public IdentityCertificate getIdCert() {
        return idCert;
    }

    public void setIdCert(IdentityCertificate idCert) {
        this.idCert = idCert;
    }

    /**
     * @return the name of this consumer.
     */
    @HateoasInclude
    public String getName() {
        return name;
    }

    /**
     * @param name the name of this consumer.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return this consumers type.
     */
    public ConsumerType getType() {
        return type;
    }

    /**
     * @param typeIn consumer type
     */
    public void setType(ConsumerType typeIn) {
        type = typeIn;
    }

    /**
     * @return the owner of this Consumer.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Associates an owner to this Consumer.
     * @param oid owner to associate to this Consumer.
     */
    public void setOwnerId(String oid) {
        this.ownerId = oid;
    }

    /**
     * @return Prefix for web URL
     */
    public String getWebUrl() {
        return prefixUrlWeb;
    }

    /**
     * @param url
     */
    public void setWebUrl(String url) {
        prefixUrlWeb = url;
    }

    /**
     *
     * @return the API URL
     */
    public String getApiUrl() {
        return prefixUrlApi;
    }

    /**
     *
     * @param url
     */
    public void setApiUrl(String url) {
        prefixUrlApi = url;
    }

    /**
     *
     * @return the Content Access Mode
     */
    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    /**
     *
     * @param contentAccessMode
     */
    public void setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("UpstreamConsumer [uuid: %s, name: %s, owner id: %s]",
            this.getUuid(), this.getName(), this.getOwnerId());
    }
}

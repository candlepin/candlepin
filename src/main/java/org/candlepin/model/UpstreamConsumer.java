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

import org.candlepin.jackson.HateoasArrayExclude;
import org.candlepin.jackson.HateoasInclude;
import org.candlepin.util.Util;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonFilter;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * UpstreamConsumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_upstream_consumer")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("ApiHateoas")
public class UpstreamConsumer extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(nullable = false)
    private String name;

    @OneToOne
    @JoinColumn(name = "upstream_consumer_idcert_id")
    private IdentityCertificate idCert;

    @ManyToOne
    @JoinColumn(nullable = false)
    @ForeignKey(name = "fk_consumer_consumer_type")
    private ConsumerType type;

    @ManyToOne
    @ForeignKey(name = "fk_upstream_consumer_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_upstream_consumer_owner_fk_idx")
    private Owner owner;

    @OneToOne(cascade = CascadeType.ALL)
    private KeyPair keyPair;

    @Column(length = 255)
    private String prefixUrlWeb;

    @Column(length = 255)
    private String prefixUrlApi;

    public UpstreamConsumer(String name, Owner owner, ConsumerType type) {
        this();

        this.name = name;
        this.owner = owner;
        this.type = type;
    }

    public UpstreamConsumer() {
        // This constructor is for creating a new UpstreamConsumer in the DB,
        // so we'll generate a UUID at this point.
        this.ensureUUID();
    }

    /**
     * @return the Consumer's uuid
     */
    @HateoasInclude
    public String getUuid() {
        return uuid;
    }

    public void ensureUUID() {
        if (uuid == null  || uuid.length() == 0) {
            this.uuid = Util.generateUUID();
        }
    }

    /**
     * @param uuid the uuid of this consumer.
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
    public Owner getOwner() {
        return owner;
    }

    /**
     * Associates an owner to this Consumer.
     * @param owner owner to associate to this Consumer.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @XmlTransient
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * @param keyPair
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * @return Prefix for web url
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
     * @return the api url
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

}

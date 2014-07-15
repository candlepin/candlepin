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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.jackson.HateoasInclude;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ImportUpstreamConsumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_import_upstream_consumer")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("ApiHateoas")
public class ImportUpstreamConsumer extends AbstractHibernateObject {

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

    @ManyToOne
    @JoinColumn(nullable = false)
    @ForeignKey(name = "fk_import_upstream_cnsmr_type")
    @NotNull
    private ConsumerType type;

    @Column(nullable = false, name = "owner_id")
    @NotNull
    private String ownerId;

    @Column(length = 255, name = "prefix_url_web")
    @Size(max = 255)
    private String prefixUrlWeb;

    @Column(length = 255, name = "prefix_url_api")
    @Size(max = 255)
    private String prefixUrlApi;

    public ImportUpstreamConsumer() {
        // needed for Hibernate
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

    /**
     * @return the name of the consumer.
     */
    @HateoasInclude
    public String getName() {
        return name;
    }

    /**
     * @param name the name of the consumer.
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
}

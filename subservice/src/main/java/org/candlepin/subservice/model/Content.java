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
package org.candlepin.subservice.model;

import org.candlepin.model.AbstractHibernateObject;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
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



/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cps_content")
public class Content extends AbstractHibernateObject {

    // Internal RH content ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Size(max = 32)
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String type;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    protected String label;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String vendor;

    @Column(nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    protected String contentUrl;

    @Column(nullable = true)
    @Size(max = 255)
    protected String requiredTags;

    // for selecting Y/Z stream
    @Column(nullable =  true)
    @Size(max = 255)
    protected String releaseVer;

    // attribute?
    @Column(nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    protected String gpgUrl;

    @Column(nullable = true)
    protected Long metadataExpire;

    @ElementCollection
    @CollectionTable(name = "cpo_content_modified_products",
                     joinColumns = @JoinColumn(name = "content_uuid"))
    @Column(name = "element")
    @Size(max = 255)
    protected Set<String> modifiedProductIds;

    @Column(nullable = true)
    @Size(max = 255)
    protected String arches;

    public Content() {
        this.modifiedProductIds = new HashSet<String>();
    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public Content setId(String id) {
        this.id = id;
        return this;
    }

    public String getType() {
        return this.type;
    }

    public Content setType(String type) {
        this.type = type;
        return this;
    }

    public String getLabel() {
        return this.label;
    }

    public Content setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Content setName(String name) {
        this.name = name;
        return this;
    }

    public String getVendor() {
        return this.vendor;
    }

    public Content setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    public String getContentUrl() {
        return this.contentUrl;
    }

    public Content setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
        return this;
    }

    public String getRequiredTags() {
        return this.requiredTags;
    }

    public Content setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    public String getReleaseVer() {
        return this.releaseVer;
    }

    public Content setReleaseVer(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    public String getGpgUrl() {
        return this.gpgUrl;
    }

    public Content setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    public Long getMetadataExpire() {
        return this.metadataExpire;
    }

    public Content setMetadataExpire(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    public Set<String> getModifiedProductIds() {
        return this.modifiedProductIds;
    }

    public Content setModifiedProductIds(Set<String> modifiedProductIds) {
        this.modifiedProductIds.clear();

        if (modifiedProductIds != null) {
            this.modifiedProductIds.addAll(modifiedProductIds);
        }

        return this;
    }

    public String getArches() {
        return this.arches;
    }

    public Content setArches(String arches) {
        this.arches = arches;
        return this;
    }

    public org.candlepin.model.Content toCandlepinModel() {
        org.candlepin.model.Content output = new org.candlepin.model.Content();

        output.setId(this.getId());
        output.setType(this.getType());
        output.setLabel(this.getLabel());
        output.setName(this.getName());
        output.setVendor(this.getVendor());
        output.setContentUrl(this.getContentUrl());
        output.setRequiredTags(this.getRequiredTags());
        output.setReleaseVer(this.getReleaseVer());
        output.setGpgUrl(this.getGpgUrl());
        output.setMetadataExpire(this.getMetadataExpire());
        output.setModifiedProductIds(this.getModifiedProductIds());
        output.setArches(this.getArches());
        output.setCreated(this.getCreated());
        output.setUpdated(this.getUpdated());

        return output;
    }
}

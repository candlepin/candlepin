/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Content
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Content {

    private String id;
    private String type;
    private String name;
    private String label;
    private String vendor;
    private String path;
    @JsonProperty("gpg_url")
    private String gpgUrl;
    private Boolean enabled;
    @JsonProperty("metadata_expire")
    private Long metadataExpire;
    @JsonProperty("required_tags")
    private List<String> requiredTags;
    private List<String> arches;

    /**
     * @param id
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * @param type
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * @param name
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param label
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * @param vendor
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    /**
     * @param path
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setPath(String path) {
        this.path = path;
        return this;
    }

    /**
     *@return path
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @param gpgUrl
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * @param metadataExpire
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setMetadataExpiration(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    /**
     * @param requiredTags
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setRequiredTags(List<String> requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    /**
     * @return the arches
     */
    public List<String> getArches() {
        return arches;
    }

    /**
     * @param arches the arches to set
     *
     * @return
     *  a reference to this Content DTO
     */
    public Content setArches(List<String> arches) {
        this.arches = arches;
        return this;
    }
}

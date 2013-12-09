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
package org.candlepin.json.model;

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
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @param vendor
     */
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    /**
     * @param path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     *@return path
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @param gpgUrl
     */
    public void setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
    }

    /**
     * @param enabled
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param metadataExpire
     */
    public void setMetadataExpire(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
    }

    /**
     * @param requiredTags
     */
    public void setRequiredTags(List<String> requiredTags) {
        this.requiredTags = requiredTags;
    }

    /**
     * @return the arches
     */
    public List<String> getArches() {
        return arches;
    }

    /**
     * @param arches the arches to set
     */
    public void setArches(List<String> arches) {
        this.arches = arches;
    }
}

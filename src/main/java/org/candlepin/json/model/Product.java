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
 * Product
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Product {

    private String id;
    private String name;
    private String version;
    @JsonProperty("brand_type")
    private String brandType;
    private List<String> architectures;
    private List<Content> content;

    /**
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @param isOs
     */
    public void setBrandType(String brandType) {
        this.brandType = brandType;
    }
    /**
     * @param archList
     */
    public void setArchitectures(List<String> architectures) {
        this.architectures = architectures;
    }

    /**
     * @param mapContent
     */
    public void setContent(List<Content> content) {
        this.content = content;
    }

    public List<Content> getContent() {
        return this.content;
    }
}

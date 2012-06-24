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
package org.candlepin.client.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.candlepin.client.PemUtil;

/**
 * Consumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Consumer extends TimeStampedEntity {

    protected String name;
    protected ConsumerType type;
    protected String uuid;
    protected IdentityCertificate idCert;
    private Consumer parent;
    private Long id;
    private Map<String, String> facts;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Consumer getParent() {
        return parent;
    }

    public void setParent(Consumer parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConsumerType getType() {
        return type;
    }

    public void setType(ConsumerType type) {
        this.type = type;
    }

    @JsonIgnore
    public void setType(String type) {
        this.type = new ConsumerType(type, null);
    }

    @JsonIgnore
    public String getUuid() {
        return PemUtil.extractUUID(PemUtil.createCert(this.idCert.getCert()));
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public IdentityCertificate getIdCert() {
        return idCert;
    }

    public void setIdCert(IdentityCertificate idCert) {
        this.idCert = idCert;
    }

    public Map<String, String> getFacts() {
        return this.facts;
    }

    // @JsonIgnore
    public void setFacts(Map<String, String> facts) {
        this.facts = facts;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @JsonIgnore
    public void setUsername(String username) {
    }
}

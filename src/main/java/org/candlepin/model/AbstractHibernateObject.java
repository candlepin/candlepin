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

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.candlepin.jackson.DynamicFilterable;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * Abstract class for hibernate entities
 */
@MappedSuperclass
@XmlType(name = "CandlepinObject")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractHibernateObject implements Persisted,
        Serializable, DynamicFilterable {
    public static final String DEFAULT_SORT_FIELD = "created";

    private Date created;
    private Date updated;

    @Transient
    private Set<String> filterBlacklist;
    @Transient
    private Set<String> filterWhitelist;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();

        setCreated(now);
        setUpdated(now);
    }

    @PreUpdate
    protected void onUpdate() {
        setUpdated(new Date());
    }

    @XmlElement
    @Column(nullable = false, unique = true)
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlElement
    @Column(nullable = false, unique = true)
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    @XmlTransient
    public boolean isAttributeControlled(String attribute) {
        return isAttributeAllowed(attribute) || isAttributeFiltered(attribute);
    }

    @XmlTransient
    public boolean isAttributeAllowed(String attribute) {
        return filterWhitelist != null && filterWhitelist.contains(attribute);
    }

    @XmlTransient
    public boolean isAttributeFiltered(String attribute) {
        return filterBlacklist != null && filterBlacklist.contains(attribute);
    }

    @XmlTransient
    public void filterAttribute(String attribute) {
        if (filterBlacklist == null) {
            filterBlacklist = new HashSet<String>();
        }
        if (this.isAttributeAllowed(attribute)) {
            filterWhitelist.remove(attribute);
        }
        filterBlacklist.add(attribute);
    }

    @XmlTransient
    public void allowAttribute(String attribute) {
        if (filterWhitelist == null) {
            filterWhitelist = new HashSet<String>();
        }
        if (this.isAttributeFiltered(attribute)) {
            filterBlacklist.remove(attribute);
        }
        filterWhitelist.add(attribute);
    }
}

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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * DistributorVersionCapability
 */
@XmlRootElement(name = "distributorversioncapability")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_dist_version_capability")
public class DistributorVersionCapability {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(nullable = false, name = "capability_name")
    private String capabilityName;

    @ManyToOne
    @ForeignKey(name = "fk_dist_vrsn_cpblty")
    @JoinColumn(name = "dist_version_id", nullable = false)
    @Index(name = "cp_capability_distver_fk_idx")
    private DistributorVersion distributorVersion;

    public DistributorVersionCapability() {
    }

    public DistributorVersionCapability(DistributorVersion distributorVersion,
        String capabilityName) {
        this.distributorVersion = distributorVersion;
        this.capabilityName = capabilityName;
    }

    /**
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the capability name
     */
    public String getName() {
        return capabilityName;
    }

    /**
     * @param name the capability name to set
     */
    public void setName(String name) {
        this.capabilityName = name;
    }

    /**
     * @param distributorVersion version to set
     */
    public void setDistributorVersion(DistributorVersion distributorVersion) {
        this.distributorVersion = distributorVersion;
    }

    /**
     * @return the distributor version
     *
     */
    @XmlTransient
    public DistributorVersion getDistributorVersion() {
        return this.distributorVersion;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof DistributorVersionCapability)) {
            return false;
        }
        DistributorVersionCapability another = (DistributorVersionCapability) anObject;
        return capabilityName.equals(another.getName());
    }

    @Override
    public int hashCode() {
        return capabilityName.hashCode();
    }
}

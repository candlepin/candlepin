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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

/**
 * DistributorVersion
 *
 * Used as a capability seed list for consumers. A consumer created with
 * a specific distributor version name will be assigned the list of
 * capabilities related to the distributor version
 *
 */
@XmlRootElement(name = "distributorversion")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_dist_version")

public class DistributorVersion extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true, name = "display_name")
    private String displayName;

    @OneToMany(mappedBy = "distributorVersion", targetEntity =
        DistributorVersionCapability.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<DistributorVersionCapability> capabilities;

    public DistributorVersion() {
    }

    public DistributorVersion(String name) {
        this.name = name;
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
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the display name to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the capabilities
     */
    public Set<DistributorVersionCapability> getCapabilities() {
        return capabilities;
    }

    /**
     * @param capabilities the capabilities to set
     */
    public void setCapabilities(Set<DistributorVersionCapability> capabilities) {
        if (this.capabilities == null) {
            this.capabilities = new HashSet<DistributorVersionCapability>();
        }
        if (!this.capabilities.equals(capabilities)) {
            this.capabilities.clear();
            this.capabilities.addAll(capabilities);
            this.setUpdated(new Date());
            for (DistributorVersionCapability dvc : this.capabilities) {
                dvc.setDistributorVersion(this);
            }
        }
    }
}

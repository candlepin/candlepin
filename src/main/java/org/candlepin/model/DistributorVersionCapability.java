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
package org.candlepin.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * DistributorVersionCapability
 */
@Entity
@Table(name = DistributorVersionCapability.DB_TABLE)
public class DistributorVersionCapability {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_dist_version_capability";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @ManyToOne
    @JoinColumn(name = "dist_version_id", nullable = false)
    private DistributorVersion distributorVersion;

    public DistributorVersionCapability() {
    }

    public DistributorVersionCapability(DistributorVersion distributorVersion,
        String name) {
        this.distributorVersion = distributorVersion;
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
     * @return the capability name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the capability name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param distributorVersion version to set
     */
    public void setDistributorVersion(DistributorVersion distributorVersion) {
        this.distributorVersion = distributorVersion;
    }

    /**
     * @return the distributor version
     */
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
        return name.equals(another.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.GenericGenerator;

/**
 * ExporterMetadata
 */
@Entity
@Table(name = "cp_export_metadata")
public class ExporterMetadata extends AbstractHibernateObject {

    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_PER_USER = "per_user";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String type;

    @Column(nullable = false)
    @NotNull
    private Date exported;

    @OneToOne
    @JoinColumn(name = "owner_id", nullable = true)
    private Owner owner;

    public ExporterMetadata() {
        id = null;
        type = TYPE_SYSTEM;
        exported = new Date();
    }

    public ExporterMetadata(String type, Date exported, Owner owner) {
        this(null, type, exported, owner);
    }

    public ExporterMetadata(String id, String type, Date exported, Owner owner) {
        this.id = id;
        this.type = type;
        this.exported = exported;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public void setId(String anId) {
        id = anId;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the exported
     */
    public Date getExported() {
        return exported;
    }

    /**
     * @param exported the exported to set
     */
    public void setExported(Date exported) {
        this.exported = exported;
    }

    /**
     * @return the owner
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }
}

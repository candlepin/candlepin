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

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.service.model.PermissionBlueprintInfo;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;



/**
 * A representation of a permission to be stored in the database. Used by the
 * PermissionFactory to determine which actual Java class to create, and any relevant
 * information needed to do so.
 *
 * These are only used in development/QE setups, generally other user services create
 * the required permissions for the authenticating principal.
 */
@Entity
@Table(name = PermissionBlueprint.DB_TABLE)
public class PermissionBlueprint extends AbstractHibernateObject implements PermissionBlueprintInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_permission";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Role role;

    @Column(name = "access_level")
    @Enumerated(EnumType.STRING)
    private Access access;

    @Column(name = "permission_type")
    @Enumerated(EnumType.STRING)
    @NotNull
    private PermissionType type;

    public PermissionBlueprint(PermissionType type, Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
        this.type = type;
    }

    public PermissionBlueprint() {
        // JPA
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Access getAccess() {
        return access;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public String getAccessLevel() {
        Access access = this.getAccess();
        return access != null ? access.name() : null;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    @XmlTransient
    public Role getRole() {
        return this.role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * @return key used by PermissionFactory to determine what type of permission to create.
     */
    public PermissionType getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public String getTypeName() {
        PermissionType type = this.getType();
        return type != null ? type.name() : null;
    }

    public void setType(PermissionType type) {
        this.type = type;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }
}

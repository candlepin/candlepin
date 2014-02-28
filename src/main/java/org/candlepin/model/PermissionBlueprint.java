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
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * A representation of a permission to be stored in the database. Used by the
 * PermissionFactory to determine which actual Java class to create, and any relevant
 * information needed to do so.
 *
 * These are only used in development/QE setups, generally other user services create
 * the required permissions for the authenticating principal.
 */
@Entity
@Table(name = "cp_permission")
public class PermissionBlueprint extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_permission_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_permission_owner_fk_idx")
    private Owner owner;

    @ManyToOne
    @ForeignKey(name = "fk_permission_role")
    @JoinColumn(nullable = false)
    @Index(name = "cp_permission_role_fk_idx")
    private Role role;

    @Column(name = "access_level")
    @Enumerated(EnumType.STRING)
    private Access access;

    @Column(name = "permission_type")
    @Enumerated(EnumType.STRING)
    private PermissionType type;

    public PermissionBlueprint(PermissionType type, Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
        this.type = type;
    }

    protected PermissionBlueprint() {
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

    public void setAccess(Access access) {
        this.access = access;
    }

    @XmlTransient
    public Role getRole() {
        return role;
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

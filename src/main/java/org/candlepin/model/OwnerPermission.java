/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.Permission;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permissions is granting.
 */
@Entity
@Table(name = "cp_owner_permission")
public class OwnerPermission extends AbstractHibernateObject implements Permission {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
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

    private Access access;

    public OwnerPermission(Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
    }

    protected OwnerPermission() {
        // JPA
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
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

    @Override
    public boolean canAccess(Object target, Access requiredAccess) {
        if (target instanceof Owned) {
            // First make sure the owner matches:
            if (owner.getKey().equals(((Owned) target).getOwner().getKey()) &&
                providesAccess(requiredAccess)) {
                return true;
            }
        }

        // If asked to verify access to an object that does not implement Owned,
        // as far as this permission goes, we probably have to deny access.
        return false;
    }

    /**
     * Return true if this permission provides the requested access type.
     * If we have ALL, assume a match, otherwise do an explicit comparison.
     *
     * @return true if we provide the given access level.
     */
    public boolean providesAccess(Access requiredAccess) {
        return (this.access == Access.ALL || this.access == requiredAccess);
    }

    @XmlTransient
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

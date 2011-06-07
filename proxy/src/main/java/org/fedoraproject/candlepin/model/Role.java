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
package org.fedoraproject.candlepin.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

/**
 * Roles represent the relationship between users and the permissions they have. 
 */
@Entity
@Table(name = "cp_role")
public class Role extends AbstractHibernateObject implements Linkable {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @OneToMany(mappedBy = "role", cascade = {CascadeType.ALL})
    private Set<RoleUser> roleUsers = new HashSet<RoleUser>();

    @ManyToMany(
        targetEntity = OwnerPermission.class,
        cascade = { CascadeType.PERSIST})
    @ForeignKey(
        name = "fk_permission_id",
        inverseName = "fk_role_id")
    @JoinTable(
        name = "cp_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<OwnerPermission> permissions = new HashSet<OwnerPermission>();

    @Column(unique = true)
    private String name;

    public Role(String name, Set<RoleUser> roleUsers, Set<OwnerPermission> memberships) {
        this.name = name;
        this.roleUsers = roleUsers;
        this.permissions = memberships;
    }

    public Role(String name) {
        this.name = name;
    }

    public Role() {
        // JPA
    }

    public String getHref() {
        return "/roles/" + getId();
    }

    @Override
    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects
         * that were originally sent down to the client in HATEOAS form.
         */
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<RoleUser> getRoleUsers() {
        return roleUsers;
    }
    
    public void setRoleUsers(Set<RoleUser> roleUsers) {
        this.roleUsers = roleUsers;
    }

    public void addUser(User u) {
        RoleUser ru = new RoleUser(this, u);
        this.roleUsers.add(ru);
    }
    
    public Set<OwnerPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<OwnerPermission> permissions) {
        this.permissions = permissions;
    }
    
    public void addPermission(OwnerPermission p) {
        this.permissions.add(p);
    }
}

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

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;

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

/**
 * Roles represent the relationship between users and the permissions they have.
 */
@Entity
@Table(name = "cp_role")
public class Role extends AbstractHibernateObject implements Linkable {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @ManyToMany(targetEntity = User.class)
    @ForeignKey(
        name = "fk_user_id",
        inverseName = "fk_role_id")
    @JoinTable(
        name = "cp_role_users",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> users = new HashSet<User>();

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    private Set<PermissionBlueprint> permissions = new HashSet<PermissionBlueprint>();

    @Column(unique = true, nullable = false)
    private String name;

    public Role(String name) {
        this.name = name;
    }

    public Role(String name, Set<User> users, Set<PermissionBlueprint> memberships) {
        this.name = name;
        this.users = users;
        this.permissions = memberships;
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

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    public void addUser(User u) {
        if (this.users.add(u)) {
            u.addRole(this);
        }
    }

    public void removeUser(User u) {
        if (this.users.remove(u)) {
            u.removeRole(this);
        }
    }

    public Set<PermissionBlueprint> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<PermissionBlueprint> permissions) {
        this.permissions = permissions;
    }

    public void addPermission(PermissionBlueprint p) {
        this.permissions.add(p);
        p.setRole(this);
    }
}

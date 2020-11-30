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

import org.candlepin.service.model.RoleInfo;

import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Roles represent the relationship between users and the permissions they have.
 */
@Entity
@Table(name = Role.DB_TABLE)
public class Role extends AbstractHibernateObject implements Linkable, RoleInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_role";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    // TODO: spring-guice FetchType.EAGER was added to avoid the lazy initialization error
    @ManyToMany(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinTable(
        name = "cp_role_users",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> users = new HashSet<>();

    // TODO: spring-guice FetchType.EAGER was added to avoid the lazy initialization error
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL,  fetch = FetchType.EAGER)
    private Set<PermissionBlueprint> permissions = new HashSet<>();

    @Column(unique = true, nullable = false)
    @Size(max = 255)
    @NotNull
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

    public void clearUsers() {
        Set<User> cleared = this.users;

        if (cleared != null) {
            this.users = new HashSet<>();

            for (User user : cleared) {
                user.removeRole(this);
            }
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
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

    public void clearPermissions() {
        this.permissions.clear();
    }
}

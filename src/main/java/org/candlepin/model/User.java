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

import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.UserInfo;

import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Represents the user.
 *
 * A user is more akin to an account within an owner. (i.e. organization)
 */
@Entity
@Table(name = User.DB_TABLE)
public class User extends AbstractHibernateObject implements UserInfo {

    /**
     * Name of the table backing this object in the database
     */
    public static final String DB_TABLE = "cp_user";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToMany(targetEntity = Role.class, mappedBy = "users")
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String username;

    @Size(max = 255)
    private String hashedPassword;

    @NotNull
    private boolean superAdmin;

    @Transient
    private OwnerInfo primaryOwner;


    public User() {
        this.roles = new HashSet<>();
    }

    public User(String login, String password) {
        this(login, password, false, null);
    }

    public User(String login, String password, boolean superAdmin) {
        this(login, password, superAdmin, null);
    }

    public User(String login, String password, boolean superAdmin, OwnerInfo primaryOwner) {
        this();

        this.username = login;
        this.hashedPassword = password;
        this.superAdmin = superAdmin;
        this.primaryOwner = primaryOwner;
    }

    /**
     * @return the id
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     *
     * @return
     *  a reference to this User
     */
    public User setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the login to set
     *
     * @return
     *  a reference to this User
     */
    public User setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * @return the hashed password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    /**
     * Sets the hashed password value.
     *
     * @return
     *  a reference to this User
     */
    public User setHashedPassword(String password) {
        this.hashedPassword = password;
        return this;
    }

    /**
     * @return the roles
     */
    public Set<Role> getRoles() {
        return roles;
    }

    public void addRole(Role r) {
        if (this.roles.add(r)) {
            r.addUser(this);
        }
    }

    public void removeRole(Role r) {
        if (this.roles.remove(r)) {
            r.removeUser(this);
        }
    }

    /**
     * Clears any existing roles for this user.
     */
    public void clearRoles() {
        Set<Role> cleared = this.roles;

        if (cleared != null) {
            this.roles = new HashSet<>();

            for (Role role : cleared) {
                role.removeUser(this);
            }
        }
    }

    /**
     * @return if the user has the SUPER_ADMIN role
     */
    public Boolean isSuperAdmin() {
        return superAdmin;
    }

    /**
     * @param superAdmin if the user should have the SUPER_ADMIN role
     *
     * @return
     *  a reference to this User
     */
    public User setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
        return this;
    }

    /**
     * Determines which of the owners related to this user is the designated primary
     *
     * @return OwnerInfo the primary owner
     */
    public OwnerInfo getPrimaryOwner() {
        return this.primaryOwner;
    }


    /**
     * @param primaryOwner the owner related to this user that is the designated primary
     *
     * @return a reference to this User
     */
    public User setPrimaryOwner(OwnerInfo primaryOwner) {
        this.primaryOwner = primaryOwner;
        return this;
    }

    /**
     * Return string representation of the user object
     *
     * @return string representation of the user object
     */
    @Override
    public String toString() {
        return String.format("User [login: %s, password: %s]", username, hashedPassword);
    }

}

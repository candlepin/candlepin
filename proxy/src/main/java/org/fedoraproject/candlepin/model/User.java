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

import java.util.Formatter;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.GenericGenerator;

import org.fedoraproject.candlepin.util.Util;

/**
 * Represents the user.
 *
 * A user is more akin to an account within an owner. (i.e. organization)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_user")
public class User extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @ManyToMany(targetEntity = Role.class, mappedBy = "users")
    private Set<Role> roles = new HashSet<Role>();

    @Column(nullable = false, unique = true)
    private String username;

    private String hashedPassword;

    private boolean superAdmin;

    public User() {
        this.roles = new HashSet<Role>();
    }

    public User(String login, String password) {
        this(login, password, false);
    }

    public User(String login, String password, boolean superAdmin) {
        this();

        this.username = login;
        this.hashedPassword = Util.hash(password);
        this.superAdmin = superAdmin;
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
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    /**
     * @param username the login to set
     */
    public void setUsername(String username) {
        this.username = username;
    }
    /**
     * @return the hashed password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }
    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.hashedPassword = Util.hash(password);
    }

    /**
     * Looks up permissions to find associated owners.
     *
     * @return associated owners
     *
     * @deprecated use {@link #getMemberships()} instead
     */
    @Deprecated
    @XmlTransient
    public Set<Owner> getOwners() {
        Set<Owner> owners = new HashSet<Owner>();
        for (Role role : getRoles()) {
            for (OwnerPermission p : role.getPermissions()) {
                owners.add(p.getOwner());
            }
        }

        return owners;
    }

    /**
     * @return the roles
     */
    @XmlTransient
    public Set<Role> getRoles() {
        return roles;
    }
    
    public void addRole(Role r) {
        if (this.roles.add(r)) {
            r.addUser(this);
        }
    }
    
    /**
     * Iterates user's roles and returns all unique permissions.
     * @return all of this user's unique permissions.
     */
    @XmlTransient
    public Set<OwnerPermission> getPermissions() {
        Set<OwnerPermission> perms = new HashSet<OwnerPermission>();
        for (Role r : getRoles()) {
            perms.addAll(r.getPermissions());
        }
        return perms;
    }

    /**
     * @return if the user has the SUPER_ADMIN role
     */
    public boolean isSuperAdmin() {
        return superAdmin;
    }

    /**
     * @param superAdmin if the user should have the SUPER_ADMIN role
     */
    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    /**
     * Return string representation of the user object
     * @return string representation of the user object
     */
    @Override
    public String toString() {
        return new Formatter().format("User :{login: %s, password: %s}",
                username, hashedPassword).toString();
    }

}

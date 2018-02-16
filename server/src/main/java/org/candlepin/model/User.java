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
import org.candlepin.auth.SubResource;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.hibernate.annotations.GenericGenerator;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents the user.
 *
 * A user is more akin to an account within an owner. (i.e. organization)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = User.DB_TABLE)
public class User extends AbstractHibernateObject {

    /**
     * This class only exists so that Swagger can generate a separate model.  Users are not
     * symmetrical when serialized versus deserialized (e.g. the plaintext password is never deserialized);
     * accordingly, Swagger requires two separate models. The UserCreationRequest class below is to allow
     * swagger-codegen to generate a model object that will allow us to set the password on a request.
     *
     * See https://github.com/swagger-api/swagger-core/issues/1214
     */
    @ApiModel("UserCreationRequest")
    public static final class UserCreationRequest {
        @ApiModelProperty(required = true) private String username;
        @ApiModelProperty(required = true) private String password;
        @ApiModelProperty private boolean superAdmin;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isSuperAdmin() {
            return superAdmin;
        }

        public void setSuperAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
        }
    }

    /**
     * Name of the table backing this object in the database
     */
    public static final String DB_TABLE = "cp_user";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @ApiModelProperty(readOnly = true)
    private String id;

    @ManyToMany(targetEntity = Role.class, mappedBy = "users")
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String username;

    @ApiModelProperty(readOnly = true)
    @Size(max = 255)
    private String hashedPassword;

    @NotNull
    private boolean superAdmin;

    /*
     * Users are also used as a return value from a UserServiceAdapter, which does not
     * necessarily mean they are stored in our database. We allow permissions to be added
     * to the user for this situation, so other adapters do not have to fake roles and
     * permission blueprints. See getPermissions for behavior when a user has both roles
     * and permissions.
     */
    @Transient
    private Set<Permission> permissions;

    public User() {
        this.roles = new HashSet<>();
        this.permissions = new HashSet<>();
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
     * Sets the hashed password value.
     */
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    /**
     * @param password the password to set
     */
    @JsonProperty
    public void setPassword(String password) {
        this.hashedPassword = Util.hash(password);
    }

    /**
     * Password is a "split property" in Jackson's terminology.  The JsonProperty annotation on the
     * setter means it can be written, but the @JsonIgnore on the getter means it will not be included when
     * a User is serialized.
     * @return the hashed password
     */
    @JsonIgnore
    public String getPassword() {
        return getHashedPassword();
    }

    /**
     * Looks up permissions to find associated owners. We return any owner we find
     * on a permission, regardless of permission type or access level. You can use this
     * API call to list the owners a user should be able to see or use in some capacity.
     *
     * @return associated owners
     */
    @XmlTransient
    public Set<Owner> getOwners(SubResource sub, Access accessLevel) {
        Set<Owner> owners = new HashSet<>();
        if (sub == null) {
            sub = SubResource.NONE;
        }
        for (Permission p : this.getPermissions()) {
            if (p.canAccess(p.getOwner(), sub, accessLevel)) {
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

    public void removeRole(Role r) {
        if (this.roles.remove(r)) {
            r.removeUser(this);
        }
    }

    /**
     * Full list of permissions for this user.
     *
     * Includes those from roles stored in the database, as well as those explicitly added
     * by the user service adapter.
     *
     * @return full list of permissions for this user.
     */
    @XmlTransient
    public Set<Permission> getPermissions() {
        PermissionFactory permFactory = new PermissionFactory();
        Set<Permission> perms = new HashSet<>();
        for (Role r : getRoles()) {
            perms.addAll(permFactory.createPermissions(this, r.getPermissions()));
        }
        perms.addAll(this.permissions);
        return perms;
    }

    public void addPermissions(Permission permission) {
        this.permissions.add(permission);
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
     *
     * @return string representation of the user object
     */
    @Override
    public String toString() {
        return String.format("User [login: %s, password: %s]", username, hashedPassword);
    }

}

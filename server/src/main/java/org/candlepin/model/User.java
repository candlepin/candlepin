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

import org.candlepin.service.model.UserInfo;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
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
public class User extends AbstractHibernateObject implements UserInfo {

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

    // TODO: spring-guice FetchType.EAGER was added to avoid the lazy initialization error
    @ManyToMany(targetEntity = Role.class, mappedBy = "users", fetch = FetchType.EAGER)
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

    public User() {
        this.roles = new HashSet<>();
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

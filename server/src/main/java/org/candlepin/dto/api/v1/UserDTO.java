/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;



/**
 * DTO representing the user data exposed to the API layer.
 *
 * <pre>
 * {
 *   "id": "string",
 *   "username": "string",
 *   "superAdmin": false,
 *   "created": "2018-04-02T19:47:31.758Z",
 *   "updated": "2018-04-02T19:47:31.758Z"
 * }
 * </pre>
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "User information for a given user")
public class UserDTO extends TimestampedCandlepinDTO<UserDTO> {

    @ApiModelProperty(required = true, example = "ff808081554a3e4101554a3e9033005d")
    protected String id;
    @ApiModelProperty(required = true, example = "admin")
    protected String username;
    @ApiModelProperty(required = true, example = "secret_banana")
    protected String password;
    @ApiModelProperty(required = false, example = "true")
    protected Boolean superAdmin;

    /**
     * Initializes a new UserDTO instance with null values.
     */
    public UserDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new UserDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public UserDTO(UserDTO source) {
        super(source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDTO populate(UserDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setUsername(source.getUsername());
        this.setPassword(source.getPassword());
        this.setSuperAdmin(source.isSuperAdmin());

        return this;
    }

    /**
     * Fetches this user's ID. If the ID has not been set, this method returns null.
     *
     * @return
     *  This user's ID, or null if the ID has not been set
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets or clears the ID for this user.
     *
     * @param id
     *  The ID to set for this user, or null to clear any previously set ID
     *
     * @return
     *  a reference to this DTO
     */
    public UserDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Fetches this user's username. If the username has not been set, this method returns null.
     *
     * @return
     *  This user's username, or null if the username has not been set
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Sets or clears the username for this user.
     *
     * @param username
     *  The username to set for this user, or null to clear any previously set username
     *
     * @return
     *  a reference to this DTO
     */
    public UserDTO setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Fetches the password set for this user. If the password hasn't been set, this method returns
     * null.
     * <p></p>
     * <strong>
     *  Note that for obvious reasons, this will not be serialized for output under any sane
     *  circumstances.
     * </strong>
     *
     * @return
     *  The password set for this user, or null if the password hasn't been set.
     */
    @JsonIgnore
    public String getPassword() {
        return this.password;
    }

    /**
     * Sets or clears the plain-text password to use for this user.
     *
     * @param password
     *  The password, in plain text, to set for this user, or null to clear any previously set
     *  password
     *
     * @return
     *  a reference to this DTO
     */
    @JsonProperty
    public UserDTO setPassword(String password) {
        // Impl note:
        // While this would be a horrid practice in any other language, in Java, there's not a whole
        // lot we can do about this. As soon as the plain-text password is in the JVM, we have no
        // way of guaranteeing a (shadow) copy of that variable won't be stored in the garbage
        // collector or whatever goofy optimization mapping Java is doing under the hood.
        // As such, what is typically a very unsafe practice in most languages is par for the course
        // here in Java land. This is really not a great deal worse than instantly hashing it as we
        // do in the User entity given how it was used.

        this.password = password;
        return this;
    }

    /**
     * Checks if this user is an super admin or not. If the admin flag has not been set, this method
     * returns null.
     *
     * @return
     *  A boolean value representing this user's super admin status, or null if the super admin
     *  status has not been defined
     */
    public Boolean isSuperAdmin() {
        return this.superAdmin;
    }

    /**
     * Sets or clears the super admin flag for this user.
     *
     * @param superAdmin
     *  The super admin status to set for this user, or null to clear any previously set super admin
     *  flag
     *
     * @return
     *  a reference to this DTO
     */
    public UserDTO setSuperAdmin(Boolean superAdmin) {
        this.superAdmin = superAdmin;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof UserDTO && super.equals(obj)) {
            UserDTO that = (UserDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getUsername(), that.getUsername())
                .append(this.getPassword(), that.getPassword())
                .append(this.isSuperAdmin(), that.isSuperAdmin());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getUsername())
            .append(this.getPassword())
            .append(this.isSuperAdmin());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("UserDTO [id: %s, username: %s, super admin: %b]",
            this.getId(), this.getUsername(), this.isSuperAdmin());
    }
}

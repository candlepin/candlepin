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
import org.candlepin.service.model.UserInfo;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;



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
public class UserDTO extends TimestampedCandlepinDTO<UserDTO> implements UserInfo {

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
        this.setHashedPassword(source.getHashedPassword());
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
    @Override
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
     * Fetches the hashed password set for this user. If the password hasn't been set, this method
     * returns null.
     * <p></p>
     * <strong>
     *  Note that for obvious reasons, this will not be serialized for output under any sane
     *  circumstances.
     * </strong>
     *
     * @return
     *  The hashed password set for this user, or null if the password hasn't been set.
     */
    @Override
    @JsonIgnore
    public String getHashedPassword() {
        return this.password;
    }

    /**
     * Sets or clears the hashed password to use for this user.
     *
     * @param hash
     *  The password hash to set for this user, or null to clear any previously set password
     *
     * @return
     *  a reference to this DTO
     */
    @JsonIgnore
    public UserDTO setHashedPassword(String hash) {
        this.password = hash;
        return this;
    }

    /**
     * Sets or clears the plain-text password to use for this user. The password will be stored as
     * a hash, and will be be retrievable in plain-text from this instance once set.
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
        this.password = password != null ? Util.hash(password) : null;
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
    @Override
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
     * Always returns null.
     *
     * @return null
     */
    @Override
    @JsonIgnore
    public Collection<RoleDTO> getRoles() {
        // We don't export roles, nor do we allow them to be provided via API. This is only present
        // for compatibility with the UserInfo interface.
        return null;
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
                .append(this.getHashedPassword(), that.getHashedPassword())
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
            .append(this.getHashedPassword())
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

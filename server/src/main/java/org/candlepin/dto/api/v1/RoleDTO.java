/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import org.candlepin.util.Util;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;



/**
 * A DTO representation of the Role entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "Role information for a given role")
public class RoleDTO extends TimestampedCandlepinDTO<RoleDTO> {

    public static final long serialVersionUID = 1L;

    @ApiModelProperty(required = true, example = "ff808081554a3e4101554a3e9033005d")
    protected String id;
    @ApiModelProperty(required = true, example = "admin-all")
    protected String name;
    @ApiModelProperty(required = false)
    protected Map<String, UserDTO> users;
    @ApiModelProperty(required = false)
    protected Map<String, PermissionBlueprintDTO> permissions;

    /**
     * Initializes a new RoleDTO instance with null values.
     */
    public RoleDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new RoleDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public RoleDTO(RoleDTO source) {
        super(source);
    }

    /**
     * Fetches the ID set for this role. If the ID has not yet been set, this method returns
     * null.
     *
     * @return
     *  The ID set for this role, or null if the ID has not yet been set
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets or clears the ID for this role.
     *
     * @param id
     *  The ID to set for this role, or null to clear any previously set ID
     *
     * @return
     *  a reference to this DTO
     */
    public RoleDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Fetches the name set for this role. If the name has not yet been set, this method returns
     * null.
     *
     * @return
     *  The name set for this role, or null if the name has not yet been set
     */
    public String getName() {
        return name;
    }

    /**
     * Sets or clears the name for this role.
     *
     * @param name
     *  The name to set for this role, or null to clear any previously set name
     *
     * @return
     *  a reference to this DTO
     */
    public RoleDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Adds the specified user to this role. The user cannot be null and must have a valid ID.
     *
     * @param user
     *  The user to add to this role
     *
     * @throws IllegalArgumentException
     *  if user is null or lacks an ID
     *
     * @return
     *  true if this role was changed by the call to this method; false otherwise
     */
    public boolean addUser(UserDTO user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (user.getId() == null) {
            throw new IllegalArgumentException("user is incomplete");
        }

        if (this.users == null) {
            this.users = new HashMap<>();
        }

        UserDTO previous = this.users.put(user.getId(), user);
        return previous != user;
    }

    /**
     * Removes the specified user from this role. If the user has not yet been added to this role,
     * no change is made to the role.
     *
     * @param user
     *  The user to remove from this role
     *
     * @throws IllegalArgumentException
     *  if user is null or lacks an ID
     *
     * @return
     *  true if the user was found and removed successfully; false otherwise
     */
    public boolean removeUser(UserDTO user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (user.getId() == null) {
            throw new IllegalArgumentException("user is incomplete");
        }

        return this.removeUser(user.getId());
    }

    /**
     * Removes any user from this role with the specified user ID. If no matching user has been
     * added, no change is made to this role.
     *
     * @param userId
     *  The ID of the user to remove from this role
     *
     * @throws IllegalArgumentException
     *  if userId is null
     *
     * @return
     *  true if a user with the specified ID was found and removed successfully; false otherwise
     */
    public boolean removeUser(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        return this.users != null && this.users.remove(userId) != null;
    }

    /**
     * Retrieves a view of the users currently associated with this role.
     * <p></p>
     * Note that the collection returned by this method is a view. It can be iterated as normal, and
     * elements may be removed from the collection, but new elements may not be added. Changes made
     * to the collection or its elements will be reflected in other operations on this role.
     *
     * @return
     *  A collection of the users currently associated with this role, or null if the users have not
     *  been set
     */
    public Collection<UserDTO> getUsers() {
        return this.users != null ? this.users.values() : null;
    }

    /**
     * Sets or clears the users to be associated with this role. If the specified user collection is
     * null, any previously set users will be cleared. Note that if any of the users provided in the
     * collection are null or incomplete, this method will throw an exception.
     *
     * @param users
     *  The users to associate with this role, or null to clear any previously linked users
     *
     * @return
     *  a reference to this DTO
     */
    public RoleDTO setUsers(Collection<UserDTO> users) {


        if (users != null) {
            this.users = new HashMap<>();

            for (UserDTO user : users) {
                this.addUser(user);
            }
        }
        else {
            this.users = null;
        }

        return this;
    }


    /**
     * Adds the specified permission to this role. The permission cannot be null and must have a
     * valid ID.
     *
     * @param permission
     *  The permission to add to this role
     *
     * @throws IllegalArgumentException
     *  if permission is null or lacks an ID
     *
     * @return
     *  true if this role was changed by the call to this method; false otherwise
     */
    public boolean addPermission(PermissionBlueprintDTO permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission is null");
        }

        if (permission.getId() == null) {
            throw new IllegalArgumentException("permission is incomplete");
        }

        if (this.permissions == null) {
            this.permissions = new HashMap<>();
        }

        PermissionBlueprintDTO previous = this.permissions.put(permission.getId(), permission);
        return previous != permission;
    }

    /**
     * Removes the specified permission from this role. If the permission has not yet been added to
     * this role, no change is made to the role.
     *
     * @param permission
     *  The permission to remove from this role
     *
     * @throws IllegalArgumentException
     *  if permission is null or lacks an ID
     *
     * @return
     *  true if the permission was found and removed successfully; false otherwise
     */
    public boolean removePermission(PermissionBlueprintDTO permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission is null");
        }

        if (permission.getId() == null) {
            throw new IllegalArgumentException("permission is incomplete");
        }

        return this.removePermission(permission.getId());
    }

    /**
     * Removes any permission from this role with the specified permission ID. If no matching
     * permission has been added, no change is made to this role.
     *
     * @param permissionId
     *  The ID of the permission to remove from this role
     *
     * @throws IllegalArgumentException
     *  if permissionId is null
     *
     * @return
     *  true if a permission with the specified ID was found and removed successfully; false
     *  otherwise
     */
    public boolean removePermission(String permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("permissionId is null");
        }

        return this.permissions != null && this.permissions.remove(permissionId) != null;
    }

    /**
     * Retrieves a view of the permissions currently associated with this role.
     * <p></p>
     * Note that the collection returned by this method is a view. It can be iterated as normal, and
     * elements may be removed from the collection, but new elements may not be added. Changes made
     * to the collection or its elements will be reflected in other operations on this role.
     *
     * @return
     *  A collection of the permissions currently associated with this role, or null if the
     *  permissions have not been set
     */
    public Collection<PermissionBlueprintDTO> getPermissions() {
        return this.permissions != null ? this.permissions.values() : null;
    }

    /**
     * Sets or clears the permissions to be associated with this role. If the specified permission
     * collection is null, any previously set permissions will be cleared. Note that if any of the
     * permissions provided in the collection are null or incomplete, this method will throw an
     * exception.
     *
     * @param permissions
     *  The permissions to associate with this role, or null to clear any previously linked
     *  permissions
     *
     * @return
     *  a reference to this DTO
     */
    public RoleDTO setPermissions(Collection<PermissionBlueprintDTO> permissions) {
        if (permissions != null) {
            this.permissions = new HashMap<>();

            for (PermissionBlueprintDTO permission : permissions) {
                this.addPermission(permission);
            }
        }
        else {
            this.permissions = null;
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("RoleDTO [id: %s, name: %s]", this.getId(), this.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof RoleDTO && super.equals(obj)) {
            RoleDTO that = (RoleDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName());

            // Note that we're using the boolean operator here as a shorthand way to skip checks
            // when the equality check has already failed.
            boolean equals = builder.isEquals();

            equals = equals && Util.collectionsAreEqual(this.getUsers(), that.getUsers(),
                new Comparator<UserDTO>() {
                    @Override
                    public int compare(UserDTO c1, UserDTO c2) {
                        if (c1 == c2) {
                            return 0;
                        }

                        return c1 != null && c2 != null && c1.getId() != null ?
                            c1.getId().compareTo(c2.getId()) :
                            1;
                    }
                });

            equals = equals && Util.collectionsAreEqual(this.getPermissions(), that.getPermissions(),
                new Comparator<PermissionBlueprintDTO>() {
                    @Override
                    public int compare(PermissionBlueprintDTO c1, PermissionBlueprintDTO c2) {
                        if (c1 == c2) {
                            return 0;
                        }

                        return c1 != null && c2 != null && c1.getId() != null ?
                            c1.getId().compareTo(c2.getId()) :
                            1;
                    }
                });

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int usersHash = 0;
        int permissionsHash = 0;

        Collection<UserDTO> users = this.getUsers();
        Collection<PermissionBlueprintDTO> permissions = this.getPermissions();

        if (users != null) {
            for (UserDTO dto : users) {
                usersHash += 31 * (dto != null && dto.getId() != null ? dto.getId().hashCode() : 0);
            }
        }

        if (permissions != null) {
            for (PermissionBlueprintDTO dto : permissions) {
                permissionsHash += 31 * (dto != null && dto.getId() != null ? dto.getId().hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getName())
            .append(usersHash)
            .append(permissionsHash);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO populate(RoleDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setName(source.getName());
        this.setUsers(source.getUsers());
        this.setPermissions(source.getPermissions());

        return this;
    }
}

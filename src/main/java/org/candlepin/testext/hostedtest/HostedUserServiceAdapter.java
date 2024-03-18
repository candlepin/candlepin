package org.candlepin.testext.hostedtest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.util.Util;

import com.google.inject.Inject;

public class HostedUserServiceAdapter implements UserServiceAdapter {

    private final HostedTestDataStore datastore;

    @Inject
    public HostedUserServiceAdapter(HostedTestDataStore dataStore) {
        this.datastore = Objects.requireNonNull(dataStore);
    }

    @Override
    public boolean validateUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        UserInfo user = datastore.getUser(username);

        return password.equals(user.getHashedPassword());

    }

    @Override
    public UserInfo createUser(UserInfo user) {
        if (user == null) {
            return null;
        }

        User userEntity = new User();
        userEntity.setUsername(user.getUsername());
        userEntity.setHashedPassword(Util.hashPassword(Util.generateBcryptSalt(), user.getHashedPassword()));
        userEntity.setSuperAdmin(user.isSuperAdmin() != null ? user.isSuperAdmin() : false);

        if (user.getRoles() != null) {
            for (RoleInfo role : user.getRoles()) {
                Role roleEntity = this.datastore.getRole(role.getName());
                if (roleEntity == null) {
                    throw new IllegalStateException("Role does not exist: " + role.getName());
                }

                userEntity.addRole(roleEntity);
            }
        }

        return datastore.addUser(userEntity);

    }

    @Override
    public UserInfo updateUser(String username, UserInfo user) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        User userEntity = this.datastore.getUser(username);
        if (userEntity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        // If the username is changing, verify that the new username isn't already in use
        if (user.getUsername() != null && !username.equals(user.getUsername()) &&
            this.datastore.getUser(user.getUsername()) != null) {

            throw new IllegalStateException("Username already in use: " + user.getUsername());
        }

        // Check if the inbound entity is not the same instance we would update here. If it is,
        // we have nothing to do, so we'll just skip everything.
        if (userEntity != user) {
            Set<Role> roles = null;

            // Convert roles
            if (user.getRoles() != null) {
                roles = new HashSet<>();

                for (RoleInfo role : user.getRoles()) {
                    // If this ends up being a bottleneck, we can optimize this a tad by bulking this lookup
                    Role roleEntity = this.datastore.getRole(role.getName());
                    if (roleEntity == null) {
                        throw new IllegalStateException("Role does not exist: " + role.getName());
                    }

                    roles.add(roleEntity);
                }
            }

            // If our sub-objects validated, set the rest of the properties now
            if (user.getUsername() != null) {
                userEntity.setUsername(user.getUsername());
            }

            if (user.getHashedPassword() != null) {
                userEntity.setHashedPassword(Util.hashPassword(Util.generateBcryptSalt(), user.getHashedPassword()));
            }

            if (user.isSuperAdmin() != null) {
                userEntity.setSuperAdmin(user.isSuperAdmin());
            }

            if (roles != null) {
                userEntity.clearRoles();
                for (Role role : roles) {
                    userEntity.addRole(role);
                }
            }
        }

        return this.datastore.addUser(userEntity);
    }

    @Override
    public void deleteUser(String username) {
        if (username == null) {
            return;
        }

        this.datastore.removeUser(username);
    }

    @Override
    public UserInfo findByLogin(String username) {
        if (username == null) {
            return null;
        }

        return this.datastore.getUser(username);
    }

    @Override
    public Collection<? extends UserInfo> listUsers() {
        return this.datastore.getAllUsers();
    }

    @Override
    public Collection<? extends OwnerInfo> getAccessibleOwners(String username) {
        if (username == null) {
            return List.of();
        }

        return this.datastore.getAccessibleOwners(username);
    }

    @Override
    public RoleInfo createRole(RoleInfo role) {
        if (role == null || role.getName() == null) {
            return null;
        }

        if (this.datastore.getRole(role.getName()) != null) {
            throw new IllegalStateException("Role already exists: " + role.getName());
        }

        Role entity = new Role();
        entity.setName(role.getName());

        if (role.getUsers() != null) {
            for (UserInfo user : role.getUsers()) {
                User userEntity = this.datastore.getUser(user.getUsername());

                if (userEntity == null) {
                    throw new IllegalStateException("User does not exist: " + user.getUsername());
                }

                entity.addUser(userEntity);
            }
        }

        if (role.getPermissions() != null) {
            for (PermissionBlueprintInfo permission : role.getPermissions()) {
                PermissionBlueprint pentity = new PermissionBlueprint(null, null, null);

                if (permission.getOwner() == null) {
                    throw new IllegalArgumentException("Permission does not define an owner: " + permission);
                }

                pentity.setOwner(this.resolveOwnerInfo(permission.getOwner()));

                pentity.setType(permission.getTypeName() != null ?
                    PermissionType.valueOf(permission.getTypeName()) :
                    null);

                pentity.setAccess(permission.getAccessLevel() != null ?
                    Access.valueOf(permission.getAccessLevel()) :
                    null);

                entity.addPermission(pentity);
            }
        }

        return this.datastore.addRole(entity);
    }

    @Override
    public RoleInfo updateRole(String roleName, RoleInfo role) {
        if (role == null || role.getName() == null) {
            return null;
        }

        Role entity = this.datastore.getRole(roleName);
        if (entity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        // If the role name is changing, verify that the new role name isn't already in use
        if (role.getName() != null && !roleName.equals(role.getName()) &&
            this.datastore.getRole(role.getName()) != null) {

            throw new IllegalStateException("Role name already in use: " + role.getName());
        }

        // Check if the inbound entity is not the same instance we would update here. If it is,
        // we have nothing to do, so we'll just skip everything.
        if (entity != role) {
            Set<User> users = null;
            Set<PermissionBlueprint> permissions = null;

            if (role.getUsers() != null) {
                users = new HashSet<>();

                for (UserInfo user : role.getUsers()) {
                    User userEntity = this.datastore.getUser(user.getUsername());

                    if (userEntity == null) {
                        throw new IllegalStateException("User does not exist: " + user.getUsername());
                    }

                    users.add(userEntity);
                }
            }

            if (role.getPermissions() != null) {
                permissions = new HashSet<>();

                for (PermissionBlueprintInfo permission : role.getPermissions()) {
                    PermissionBlueprint pentity = new PermissionBlueprint(null, null, null);

                    if (permission.getOwner() == null) {
                        throw new IllegalArgumentException("Permission does not define an owner: " +
                            permission);
                    }

                    pentity.setOwner(this.resolveOwnerInfo(permission.getOwner()));

                    pentity.setType(permission.getTypeName() != null ?
                        PermissionType.valueOf(permission.getTypeName()) :
                        null);

                    pentity.setAccess(permission.getAccessLevel() != null ?
                        Access.valueOf(permission.getAccessLevel()) :
                        null);

                    permissions.add(pentity);
                }
            }

            // If everything validated, update the entity now:
            if (role.getName() != null) {
                entity.setName(role.getName());
            }

            if (users != null) {
                entity.clearUsers();
                for (User user : users) {
                    entity.addUser(user);
                }
            }

            if (permissions != null) {
                entity.clearPermissions();
                for (PermissionBlueprint permission : permissions) {
                    entity.addPermission(permission);
                }
            }
        }

        return this.datastore.addRole(entity);
    }

    @Override
    public RoleInfo addUserToRole(String roleName, String username) {
        Role role = this.datastore.getRole(roleName);
        if (role == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        User user = this.datastore.getUser(username);
        if (user == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        role.addUser(user);
        datastore.addRole(role);

        return role;
    }

    @Override
    public RoleInfo removeUserFromRole(String roleName, String username) {
        Role roleEntity = this.datastore.getRole(roleName);
        if (roleEntity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        User userEntity = this.datastore.getUser(username);
        if (userEntity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        roleEntity.removeUser(userEntity);
        return roleEntity;
    }

    @Override
    public RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission) {
        PermissionBlueprint permissionBlueprint = new PermissionBlueprint();
        if (permission.getOwner() == null) {
            throw new IllegalArgumentException("Permission does not define an owner: " +
                permission);
        }

        permissionBlueprint.setId(UUID.randomUUID().toString());
        permissionBlueprint.setOwner((Owner) permission.getOwner());
        permissionBlueprint.setType(PermissionType.valueOf(permission.getTypeName()));

        if (permission.getAccessLevel() != null) {
            permissionBlueprint.setAccess(Access.valueOf(permission.getAccessLevel()));
        }

        return this.datastore.addPermissionToRole(roleName, permissionBlueprint);
    }

    @Override
    public RoleInfo removePermissionFromRole(String roleName, String permissionId) {
        return this.datastore.removePermissionFromRole(roleName, permissionId);
    }

    @Override
    public void deleteRole(String roleName) {
        if (roleName == null) {
            return;
        }

        this.datastore.removeRole(roleName);
    }

    @Override
    public RoleInfo getRole(String roleName) {
        if (roleName == null) {
            return null;
        }

        return this.datastore.getRole(roleName);
    }

    @Override
    public Collection<? extends RoleInfo> listRoles() {
        return this.listRoles();
    }

    /**
     * Resolves the owner represented by a given OwnerInfo instance. If the owner cannot be
     * resolved, this method throws an exception.
     *
     * @throws IllegalStateException
     *  if the provided OwnerInfo does not represent a valid owner
     *
     * @return
     *  The Owner instance represented by the provided OwnerInfo
     */
    private Owner resolveOwnerInfo(OwnerInfo ownerInfo) {
        if (ownerInfo != null) {
            Owner owner = (Owner) this.datastore.getOwner(ownerInfo.getKey());

            if (owner == null) {
                throw new IllegalStateException("No such owner: " + ownerInfo.getKey());
            }

            return owner;
        }

        return null;
    }
}


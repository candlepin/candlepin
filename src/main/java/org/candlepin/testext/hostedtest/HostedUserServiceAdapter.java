package org.candlepin.testext.hostedtest;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.util.Util;

import com.google.inject.Inject;

// TODO: Add JavaDoc
public class HostedUserServiceAdapter implements UserServiceAdapter {

    private HostedTestDataStore datastore;
    private PermissionFactory permissionFactory;

    @Inject
    public HostedUserServiceAdapter(HostedTestDataStore dataStore, PermissionFactory permissionFactory) {
        this.datastore = Objects.requireNonNull(dataStore);
        this.permissionFactory = Objects.requireNonNull(permissionFactory);
    }

    @Override
    public boolean validateUser(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is null or blank");
        }

        UserInfo user = this.datastore.getUser(username);
        if (user == null) {
            return false;
        }

        return OpenBSDBCrypt.checkPassword(user.getHashedPassword(), password.toCharArray());
    }

    @Override
    public UserInfo createUser(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (this.datastore.getUser(user.getUsername()) != null) {
            throw new IllegalStateException("user already exists: " + user.getUsername());
        }

        User userToCreate = new User();
        userToCreate.setId(Util.generateUUID());
        userToCreate.setUsername(user.getUsername());
        userToCreate.setHashedPassword(Util.hashPassword(Util.generateBcryptSalt(), user.getHashedPassword()));
        userToCreate.setSuperAdmin(userToCreate.isSuperAdmin() != null ? user.isSuperAdmin() : false);

        if (user.getRoles() != null) {
            for (RoleInfo role : user.getRoles()) {
                Role existingRole = this.datastore.getRole(role.getName());
                if (existingRole == null) {
                    throw new IllegalStateException("role does not exist: " + role.getName());
                }

                userToCreate.addRole(existingRole);
            }
        }

        Owner owner = resolveOwnerInfo(user.getPrimaryOwner());
        userToCreate.setPrimaryOwner(owner);

        return this.datastore.addUser(userToCreate);
    }

    @Override
    public UserInfo updateUser(String username, UserInfo user) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        User existing = this.datastore.getUser(username);
        if (existing == null) {
            throw new IllegalStateException("user does not exist: " + username);
        }

        if (existing != null && !username.equals(user.getUsername()) && this.datastore.getUser(user.getUsername()) != null) {
            throw new IllegalStateException("username already in use: " + user.getUsername());
        }

        // Check if the inbound entity is not the same instance we would update here. If it is,
        // we have nothing to do, so we'll just skip everything.
        if (existing != user) {
            Set<Role> roles = null;

            // Convert roles
            if (user.getRoles() != null) {
                roles = new HashSet<>();

                for (RoleInfo role : user.getRoles()) {
                    Role existingRole = this.datastore.getRole(role.getName());
                    if (existingRole == null) {
                        throw new IllegalStateException("role does not exist: " + role.getName());
                    }

                    roles.add(existingRole);
                }
            }

            // If our sub-objects validated, set the rest of the properties now
            if (user.getUsername() != null) {
                existing.setUsername(user.getUsername());
            }

            if (user.getHashedPassword() != null) {
                existing.setHashedPassword(Util.hashPassword(Util.generateBcryptSalt(), user.getHashedPassword()));
            }

            if (user.isSuperAdmin() != null) {
                existing.setSuperAdmin(user.isSuperAdmin());
            }

            if (roles != null) {
                existing.clearRoles();
                for (Role role : roles) {
                    existing.addRole(role);
                }
            }

            Owner owner = resolveOwnerInfo(user.getPrimaryOwner());
            existing.setPrimaryOwner(owner);
        }

        return this.datastore.updateUser(username, existing);
    }

    @Override
    public void deleteUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        this.datastore.removeUser(username);
    }

    @Override
    public UserInfo findByLogin(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        return (UserInfo) this.datastore.getUser(username);
    }

    @Override
    public Collection<? extends UserInfo> listUsers() {
        return this.datastore.getAllUsers();
    }

    @Override
    public Collection<? extends OwnerInfo> getAccessibleOwners(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        User entity = this.datastore.getUser(username);
        if (entity == null) {
            throw new IllegalStateException("user does not exist: " + username);
        }

        if (entity.isSuperAdmin() != null && entity.isSuperAdmin()) {
            // Super admins can see everything
            return this.datastore.listOwners();
        }
        else {
            // We need to pair this down to just accessible owners...
            Collection<Permission> permissions = this.permissionFactory.createPermissions(entity);
            Set<OwnerInfo> owners = new HashSet<>();

            for (Permission permission : permissions) {
                if (permission.canAccess(permission.getOwner(), SubResource.CONSUMERS, Access.CREATE)) {
                    owners.add(permission.getOwner());
                }
            }

            return owners;
        }
    }

    @Override
    public RoleInfo createRole(RoleInfo role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is null");
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
                    throw new IllegalStateException("user does not exist: " + user.getUsername());
                }

                entity.addUser(userEntity);
            }
        }

        if (role.getPermissions() != null) {
            for (PermissionBlueprintInfo permission : role.getPermissions()) {
                PermissionBlueprint pentity = new PermissionBlueprint(null, null, null);

                if (permission.getOwner() == null) {
                    throw new IllegalArgumentException("permission does not define an owner: " + permission);
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
        if (role == null) {
            throw new IllegalArgumentException("role is null");
        }

        if (role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("role name is null or blank");
        }

        if (this.datastore.getRole(role.getName()) == null) {
            throw new IllegalStateException("Role does not exists: " + role.getName());
        }

        return this.datastore.updateRole(roleName, (Role) role);
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

        return this.datastore.addUserToRole(username, roleName);
    }

    @Override
    public RoleInfo removeUserFromRole(String roleName, String username) {
        Role role = this.datastore.getRole(roleName);
        if (role == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        User user = this.datastore.getUser(username);
        if (user == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        return this.datastore.removeUserFromRole(username, roleName);
    }

    @Override
    public RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission) {
        return (RoleInfo) this.datastore.addPermissionToRole(roleName, (PermissionBlueprint) permission);
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

    private Owner resolveOwnerInfo(OwnerInfo ownerInfo) {
        if (ownerInfo == null) {
            return null;
        }

        Owner owner = (Owner) this.datastore.getOwner(ownerInfo.getKey());
        if (owner == null) {
            // TODO: Is this right and should we be generating an ID here?
            owner = new Owner();
            // owner.setId(Util.generateUUID());
            owner.setKey(ownerInfo.getKey());
            owner.setCreated(ownerInfo.getCreated());
            owner.setUpdated(ownerInfo.getUpdated());

            owner = (Owner) this.datastore.createOwner(owner);
        }

        return owner;
    }

}


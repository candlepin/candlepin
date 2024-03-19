package org.candlepin.testext.hostedtest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
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

// TODO: Add JavaDoc
public class HostedUserServiceAdapter implements UserServiceAdapter {

    private final HostedTestDataStore datastore;

    @Inject
    public HostedUserServiceAdapter(HostedTestDataStore dataStore) {
        this.datastore = Objects.requireNonNull(dataStore);
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

        return OpenBSDBCrypt.checkPassword(user.getHashedPassword(), password.toCharArray());
    }

    @Override
    public UserInfo createUser(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        return this.datastore.addUser((HostedTestUser) user);
    }

    @Override
    public UserInfo updateUser(String username, UserInfo user) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is null or blank");
        }

        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (this.datastore.getUser(username) == null) {
            throw new IllegalStateException("user does not exist");
        }

        return this.datastore.updateUser((HostedTestUser) user);
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

        return this.datastore.getUser(username);
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

        // TODO: Implement
        return null;
    }

    @Override
    public RoleInfo createRole(RoleInfo role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is null");
        }

        if (this.datastore.getRole(role.getName()) != null) {
            throw new IllegalStateException("Role already exists: " + role.getName());
        }

        return this.datastore.addRole((HostedTestRole) role);
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

        return this.datastore.updateRole((HostedTestRole) role);
    }

    @Override
    public RoleInfo addUserToRole(String roleName, String username) {
        HostedTestRole role = this.datastore.getRole(roleName);
        if (role == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        HostedTestUser user = this.datastore.getUser(username);
        if (user == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        return this.datastore.addUserToRole(username, roleName);
    }

    @Override
    public RoleInfo removeUserFromRole(String roleName, String username) {
        HostedTestRole role = this.datastore.getRole(roleName);
        if (role == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        HostedTestUser user = this.datastore.getUser(username);
        if (user == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        return this.datastore.removeUserFromRole(username, roleName);
    }

    @Override
    public RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission) {
        return this.datastore.addPermissionToRole(roleName, (HostedTestPermission) permission);
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

}


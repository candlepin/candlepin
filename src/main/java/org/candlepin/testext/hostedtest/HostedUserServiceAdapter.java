package org.candlepin.testext.hostedtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.model.OwnerCurator;
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

import com.google.inject.Inject;

// TODO: Add JavaDoc
public class HostedUserServiceAdapter implements UserServiceAdapter {

    private HostedTestDataStore datastore;
    private DefaultUserServiceAdapter defaultUserService;

    @Inject
    public HostedUserServiceAdapter(HostedTestDataStore dataStore, UserCurator userCurator, RoleCurator roleCurator,
        PermissionBlueprintCurator permissionCurator, OwnerCurator ownerCurator,
        PermissionFactory permissionFactory) {
        this.datastore = Objects.requireNonNull(dataStore);
        Objects.requireNonNull(userCurator);
        Objects.requireNonNull(roleCurator);
        Objects.requireNonNull(permissionCurator);
        Objects.requireNonNull(ownerCurator);
        Objects.requireNonNull(permissionFactory);

        // TODO: Honestly this might not even be needed.
        this.defaultUserService = new DefaultUserServiceAdapter(userCurator, roleCurator, permissionCurator, ownerCurator, permissionFactory);
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
            // TODO: This might not be needed. Might just be able to return false here
            // return defaultUserService.validateUser(username, password);
            return false;
        }

        return OpenBSDBCrypt.checkPassword(user.getHashedPassword(), password.toCharArray());
    }

    @Override
    public UserInfo createUser(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        HostedTestUser createdUser = this.datastore.addUser(new HostedTestUser(user));

        return convertToUser(createdUser);
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

        HostedTestUser updatedUser = this.datastore.updateUser(username, new HostedTestUser(user));

        return convertToUser(updatedUser);
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

        HostedTestUser user = this.datastore.getUser(username);

        return convertToUser(user);
    }

    @Override
    public Collection<? extends UserInfo> listUsers() {
        return convertToUsers(this.datastore.getAllUsers());
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

        return this.datastore.addRole(new HostedTestRole(role));
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

        return this.datastore.updateRole(new HostedTestRole(role));
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

    private List<User> convertToUsers(Collection<HostedTestUser> users) {
        List<User> converted  = new ArrayList<>();

        for (HostedTestUser user : users) {
            User convertedUser = convertToUser(user);
            if (convertedUser != null) {
                converted.add(convertedUser);
            }
        }

        return converted;
    }

    private User convertToUser(HostedTestUser user) {
        if (user == null) {
            return null;
        }

        User converted = new User();
        converted.setId(user.getId());
        converted.setUsername(user.getUsername());
        converted.setHashedPassword(user.getHashedPassword());
        converted.setCreated(user.getCreated());
        converted.setUpdated(user.getUpdated());
        converted.setPrimaryOwner(user.getPrimaryOwner());
        converted.setSuperAdmin(user.isSuperAdmin());

        return converted;
    }

}


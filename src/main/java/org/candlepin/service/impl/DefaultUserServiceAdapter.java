/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.impl;

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
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;



/**
 * A {@link UserServiceAdapter} implementation backed by a {@link UserCurator}
 * for user creation and persistence.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultUserServiceAdapter.class);
    // Bcrypt uses 128-bit (16 bytes) salts
    private static final int SALT_LENGTH = 16;
    // Recommended cost for the bcrypt based study from April 2023 is between 10 and 12, but setting
    // the cost that high destroys throughput, and since this is only for testing and dev purposes,
    // we aren't terribly concerned about the security implications.
    private static final int BCRYPT_COST = 4;

    private UserCurator userCurator;
    private RoleCurator roleCurator;
    private PermissionBlueprintCurator permissionCurator;
    private OwnerCurator ownerCurator;
    private PermissionFactory permissionFactory;

    @Inject
    public DefaultUserServiceAdapter(UserCurator userCurator, RoleCurator roleCurator,
        PermissionBlueprintCurator permissionCurator, OwnerCurator ownerCurator,
        PermissionFactory permissionFactory) {

        this.userCurator = Objects.requireNonNull(userCurator);
        this.roleCurator = Objects.requireNonNull(roleCurator);
        this.permissionCurator = Objects.requireNonNull(permissionCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.permissionFactory = Objects.requireNonNull(permissionFactory);

        this.createDefaultAdminUser();
    }

    /**
     * Creates the default super-admin user "admin" if no other users exist in the backing user data
     * store.
     */
    private void createDefaultAdminUser() {
        if (this.userCurator.getUserCount() == 0) {
            log.info("Creating default super-admin user");

            User adminUser = new User("admin", "admin", true);
            this.createUser(adminUser);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInfo createUser(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username is null or empty");
        }

        if (this.userCurator.findByLogin(user.getUsername()) != null) {
            throw new IllegalStateException("User already exists: " + user.getUsername());
        }

        User entity = new User();

        entity.setUsername(user.getUsername());
        entity.setHashedPassword(hashPassword(generateBcryptSalt(), user.getHashedPassword()));
        entity.setSuperAdmin(user.isSuperAdmin() != null ? user.isSuperAdmin() : false);

        // Convert roles
        if (user.getRoles() != null) {
            for (RoleInfo role : user.getRoles()) {
                // If this ends up being a bottleneck, we can optimize this a tad by bulking this lookup
                Role roleEntity = this.roleCurator.getByName(role.getName());

                if (roleEntity == null) {
                    throw new IllegalStateException("Role does not exist: " + role.getName());
                }

                entity.addRole(roleEntity);
            }
        }

        entity = this.userCurator.create(entity);

        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInfo updateUser(String username, UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("username is null or empty");
        }

        User entity = this.userCurator.findByLogin(username);
        if (entity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        // If the username is changing, verify that the new username isn't already in use
        if (user.getUsername() != null && !username.equals(user.getUsername()) &&
            this.userCurator.findByLogin(user.getUsername()) != null) {

            throw new IllegalStateException("Username already in use: " + user.getUsername());
        }

        // Check if the inbound entity is not the same instance we would update here. If it is,
        // we have nothing to do, so we'll just skip everything.
        if (entity != user) {
            Set<Role> roles = null;

            // Convert roles
            if (user.getRoles() != null) {
                roles = new HashSet<>();

                for (RoleInfo role : user.getRoles()) {
                    // If this ends up being a bottleneck, we can optimize this a tad by bulking this lookup
                    Role roleEntity = this.roleCurator.getByName(role.getName());

                    if (roleEntity == null) {
                        throw new IllegalStateException("Role does not exist: " + role.getName());
                    }

                    roles.add(roleEntity);
                }
            }

            // If our sub-objects validated, set the rest of the properties now
            if (user.getUsername() != null) {
                entity.setUsername(user.getUsername());
            }

            if (user.getHashedPassword() != null) {
                entity.setHashedPassword(hashPassword(generateBcryptSalt(), user.getHashedPassword()));
            }

            if (user.isSuperAdmin() != null) {
                entity.setSuperAdmin(user.isSuperAdmin());
            }

            if (roles != null) {
                entity.clearRoles();
                for (Role role : roles) {
                    entity.addRole(role);
                }
            }
        }

        return this.userCurator.merge(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<? extends UserInfo> listUsers() {
        return this.userCurator.listAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateUser(String username, String password) {
        User user = this.userCurator.findByLogin(username);

        if (user != null && password != null) {
            return OpenBSDBCrypt.checkPassword(user.getHashedPassword(), password.toCharArray());
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUser(String username) {
        User entity = this.userCurator.findByLogin(username);

        if (entity != null) {
            entity.clearRoles();
            this.userCurator.delete(entity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInfo findByLogin(String login) {
        return userCurator.findByLogin(login);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends OwnerInfo> getAccessibleOwners(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null");
        }

        User entity = this.userCurator.findByLogin(username);
        if (entity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        if (entity.isSuperAdmin() != null && entity.isSuperAdmin()) {
            // Super admins can see everything
            return this.ownerCurator.listAll();
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
            Owner owner = this.ownerCurator.getByKey(ownerInfo.getKey());

            if (owner == null) {
                throw new IllegalStateException("No such owner: " + ownerInfo.getKey());
            }

            return owner;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo createRole(RoleInfo role) {
        if (role == null) {
            throw new IllegalArgumentException("role is null");
        }

        if (role.getName() == null || role.getName().isEmpty()) {
            throw new IllegalArgumentException("Role name is null or empty");
        }

        if (this.roleCurator.getByName(role.getName()) != null) {
            throw new IllegalStateException("Role already exists: " + role.getName());
        }

        Role entity = new Role();

        entity.setName(role.getName());

        if (role.getUsers() != null) {
            for (UserInfo user : role.getUsers()) {
                User userEntity = this.userCurator.findByLogin(user.getUsername());

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

                log.debug("CREATE -- PERMISSION ROLE: {}", pentity.getRole());
            }
        }

        return this.roleCurator.create(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo updateRole(String roleName, RoleInfo role) {
        if (role == null) {
            throw new IllegalArgumentException("role is null");
        }

        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("Role name is null or empty");
        }

        Role entity = this.roleCurator.getByName(roleName);
        if (entity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        // If the role name is changing, verify that the new role name isn't already in use
        if (role.getName() != null && !roleName.equals(role.getName()) &&
            this.roleCurator.getByName(role.getName()) != null) {

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
                    User userEntity = this.userCurator.findByLogin(user.getUsername());

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

                // Impl note: Orphan removal should handle the cleanup of the old permissions for us
            }
        }

        return this.roleCurator.merge(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo addUserToRole(String roleName, String username) {
        Role roleEntity = this.roleCurator.getByName(roleName);
        if (roleEntity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        User userEntity = this.userCurator.findByLogin(username);
        if (userEntity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        roleEntity.addUser(userEntity);
        return this.roleCurator.merge(roleEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo removeUserFromRole(String roleName, String username) {
        Role roleEntity = this.roleCurator.getByName(roleName);
        if (roleEntity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        User userEntity = this.userCurator.findByLogin(username);
        if (userEntity == null) {
            throw new IllegalStateException("User does not exist: " + username);
        }

        roleEntity.removeUser(userEntity);
        return this.roleCurator.merge(roleEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission) {
        Role roleEntity = this.roleCurator.getByName(roleName);
        if (roleEntity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        PermissionBlueprint pentity = new PermissionBlueprint(null, null, null);

        if (permission.getOwner() == null) {
            throw new IllegalArgumentException("Permission does not define an owner: " +
                permission);
        }

        pentity.setOwner(this.resolveOwnerInfo(permission.getOwner()));
        pentity.setType(PermissionType.valueOf(permission.getTypeName()));

        if (permission.getAccessLevel() != null) {
            pentity.setAccess(Access.valueOf(permission.getAccessLevel()));
        }

        roleEntity.addPermission(pentity);

        return this.roleCurator.merge(roleEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo removePermissionFromRole(String roleName, String permissionId) {
        Role roleEntity = this.roleCurator.getByName(roleName);
        if (roleEntity == null) {
            throw new IllegalStateException("Role does not exist: " + roleName);
        }

        if (permissionId == null) {
            throw new IllegalArgumentException("permissionId is null");
        }

        if (roleEntity.getPermissions() != null) {
            boolean removed = false;

            Set<PermissionBlueprint> permissions = roleEntity.getPermissions();
            Iterator<PermissionBlueprint> iterator = permissions.iterator();

            while (iterator.hasNext()) {
                PermissionBlueprint permission = iterator.next();

                if (permissionId.equals(permission.getId())) {
                    iterator.remove();

                    permission.setRole(null);
                    this.permissionCurator.delete(permission);

                    removed = true;
                }
            }

            if (removed) {
                roleEntity.setPermissions(permissions);
                roleEntity = this.roleCurator.merge(roleEntity);
            }
        }

        return roleEntity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteRole(String role) {
        Role entity = this.roleCurator.getByName(role);

        if (entity != null) {
            entity.clearUsers();
            this.roleCurator.delete(entity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleInfo getRole(String roleName) {
        return this.roleCurator.getByName(roleName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<? extends RoleInfo> listRoles() {
        return this.roleCurator.listAll();
    }

    /**
     * Hashes a password using a specified salt and the Bcrypt hashing algorithm.
     * This method generates a hashed version of the password, which can be stored
     * in a database for secure password management. The bcrypt cost factor used
     * in the hashing process determines the amount of computing power required to
     * hash the password, making it more difficult for attackers to crack the hash.
     *
     * @param salt
     *  The salt to be used in the hashing process. A salt is a random value
     *  that is used to ensure that the hash output is unique even for identical passwords. If the salt is
     *  {@code null}, it will generate random salt.
     *
     * @param password
     *  The password to be hashed. If the password is {@code null}, an empty
     *  string will be used instead. This is to handle cases where null values might be
     *  passed from tests or in situations where the password field is optional.
     *
     * @return A hashed string representation of the password, using the provided salt and
     * the Bcrypt hashing algorithm. The bcrypt cost factor defined by {@code BCRYPT_COST}
     * is used in the hashing process.
     *
     * @note It's important to compare hashed passwords using secure methods provided
     * by cryptographic libraries to prevent timing attacks. Direct comparison of
     * hashed values using standard string comparison methods is not recommended, as
     * it may be vulnerable to timing attacks. Libraries like OpenBSDBCrypt
     * provide their own comparison methods, such as {@code OpenBSDBCrypt.checkPassword},
     * which should be used to compare a stored hash with a hash of the provided password
     * during authentication processes.
     */
    private static String hashPassword(byte[] salt, String password) {
        // We need to include a condition for an empty string, because in our tests we are using null values
        // in the password field.
        return OpenBSDBCrypt.generate(password == null ? "".toCharArray() : password.toCharArray(),
            salt == null ? generateBcryptSalt() : salt, BCRYPT_COST);
    }

    /**
     * Generate salt for Bcrypt hash algorithm.
     *
     * @return
     *  The 16 bytes random salt
     */
    private static byte[] generateBcryptSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }
}

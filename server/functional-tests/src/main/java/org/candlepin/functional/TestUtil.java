/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional;

import org.candlepin.client.ApiClient;
import org.candlepin.client.model.NestedOwnerDTO;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.model.PermissionBlueprintDTO;
import org.candlepin.client.model.RoleDTO;
import org.candlepin.client.model.UserCreationRequest;
import org.candlepin.client.model.UserDTO;
import org.candlepin.client.resources.OwnersApi;
import org.candlepin.client.resources.RolesApi;
import org.candlepin.client.resources.UsersApi;

import org.springframework.web.client.RestClientException;

import java.security.SecureRandom;

/** Utility class to perform rote tasks like owner creation */
public class TestUtil {
    private static final String ALPHABET = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static SecureRandom rnd = new SecureRandom();

    private ApiClient apiClient;
    private TestManifest manifest;

    public TestUtil(ApiClient apiClient, TestManifest manifest) {
        this.apiClient = apiClient;
        this.manifest = manifest;
    }

    /**
     * @return A nine digit random alphanumeric string
     */
    public static String randomString() {
        return randomString(9);
    }

    /**
     * @param prefix String to be appended to the front of the random string
     * @return A nine digit random alphanumeric string with a chosen prefix added to the front
     */
    public static String randomString(String prefix) {
        return prefix + randomString();
    }

    /**
     * @param prefix String to be appended to the front of the random string
     * @param length The length of the random string to generate
     * @return A given digit random alphanumeric string with a chosen prefix added to the front
     */
    public static String randomString(String prefix, int length) {
        return prefix + randomString(length);
    }

    /**
     * @param length The length of the random string to generate
     * @return A given digit random alphanumeric string
     */
    public static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public OwnerDTO trivialOwner() throws RestClientException {
        String ownerKey = "owner-" + randomString();
        return trivialOwner(ownerKey);
    }

    public OwnerDTO trivialOwner(String ownerKey) throws RestClientException {
        OwnersApi ownersApi = new OwnersApi(apiClient);
        OwnerDTO owner = new OwnerDTO();
        owner.setKey(ownerKey);
        owner.setDisplayName("Display Name " + ownerKey);
        owner = ownersApi.createOwner(owner);
        manifest.push(owner);
        return owner;
    }

    public RoleDTO createRole(String ownerKey, String access, String roleName) throws RestClientException {
        PermissionBlueprintDTO permission = createOwnerPermission(ownerKey, access);
        RolesApi rolesApi = new RolesApi(apiClient);
        RoleDTO role = new RoleDTO();
        role.addPermissionsItem(permission);
        role.setName(roleName);
        role = rolesApi.createRole(role);
        manifest.push(role);
        return role;
    }

    public RoleDTO createRole(String ownerKey, String access) throws RestClientException {
        return createRole(ownerKey, access, TestUtil.randomString());
    }

    public RoleDTO createAllAccessRoleForUser(String ownerKey, UserDTO user) throws RestClientException {
        return createRoleForUser(user, ownerKey, "ALL");
    }

    public RoleDTO createReadOnlyAccessRoleForUser(String ownerKey, UserDTO user) throws RestClientException {
        return createRoleForUser(user, ownerKey, "READ_ONLY");
    }

    public RoleDTO createRoleForUser(UserDTO user, String ownerKey, String access)
        throws RestClientException {
        PermissionBlueprintDTO permission = createOwnerPermission(ownerKey, access);
        RolesApi rolesApi = new RolesApi(apiClient);
        RoleDTO role = new RoleDTO();
        role.setName(ownerKey + "_" + user.getUsername() + "_" + access);
        role.addUsersItem(user);
        role.addPermissionsItem(permission);

        role = rolesApi.createRole(role);
        manifest.push(role);
        return role;
    }

    public PermissionBlueprintDTO createOwnerPermission(String ownerKey, String access)
        throws RestClientException {
        return createPermission(ownerKey, access, "OWNER");
    }

    public PermissionBlueprintDTO createPermission(String ownerKey, String access, String type)
        throws RestClientException {
        NestedOwnerDTO nestedOwner = new NestedOwnerDTO();
        nestedOwner.setKey(ownerKey);

        PermissionBlueprintDTO permission = new PermissionBlueprintDTO();
        permission.setOwner(nestedOwner);
        permission.setType(type);
        permission.setAccess(access);

        return permission;
    }

    public RoleDTO addUserToRole(String rolename, UserDTO user) throws RestClientException {
        return addUserToRole(rolename, user.getUsername());
    }

    public RoleDTO addUserToRole(RoleDTO role, String username) throws RestClientException {
        return addUserToRole(role.getName(), username);
    }

    public RoleDTO addUserToRole(RoleDTO role, UserDTO user) throws RestClientException {
        return addUserToRole(role.getName(), user.getUsername());
    }

    public RoleDTO addUserToRole(String roleName, String username) throws RestClientException {
        RolesApi rolesApi = new RolesApi(apiClient);
        return rolesApi.addUserToRole(roleName, username);
    }

    public UserDTO createUser(String username) {
        String password = TestUtil.randomString();
        return createUser(username, password);
    }

    public UserDTO createUser(String username, String password) {
        UserCreationRequest userReq = new UserCreationRequest();
        userReq.setUsername(username);
        userReq.setPassword(password);
        userReq.setSuperAdmin(false);
        return createUser(userReq);
    }

    public UserDTO createSuperadminUser(String username) {
        String password = TestUtil.randomString();
        return createSuperadminUser(username, password);
    }

    public UserDTO createSuperadminUser(String username, String password) {
        UserCreationRequest userReq = new UserCreationRequest();
        userReq.setUsername(username);
        userReq.setPassword(password);
        userReq.setSuperAdmin(true);
        return createUser(userReq);
    }

    public UserDTO createUser(UserCreationRequest userReq) {
        UsersApi usersApi = new UsersApi(apiClient);
        UserDTO user = usersApi.createUser(userReq);
        manifest.push(user);
        return user;
    }
}

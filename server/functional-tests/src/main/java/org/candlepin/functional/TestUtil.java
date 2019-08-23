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
import org.candlepin.client.ApiException;
import org.candlepin.client.model.NestedOwnerDTO;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.model.PermissionBlueprintDTO;
import org.candlepin.client.model.RoleDTO;
import org.candlepin.client.model.UserDTO;
import org.candlepin.client.resources.OwnersApi;
import org.candlepin.client.resources.RolesApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Utility class to perform rote tasks like owner creation */
@Component
public class TestUtil {
    private static final String ALPHABET = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static SecureRandom rnd = new SecureRandom();

    private ApiClient apiClient;

    public TestUtil(ApiClientBuilder apiClientBuilder) {
        this.apiClient = apiClientBuilder.build();
    }

    @Autowired
    public TestUtil(@Qualifier("adminApiClient") ApiClient adminApiClient) {
        this.apiClient = adminApiClient;
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

    public OwnerDTO trivialOwner() throws ApiException {
        String ownerKey = randomString();
        return trivialOwner(ownerKey);
    }

    public OwnerDTO trivialOwner(String ownerKey) throws ApiException {
        OwnersApi ownersApi = new OwnersApi();
        OwnerDTO owner = new OwnerDTO();
        owner.setKey(ownerKey);
        owner.setDisplayName("Display Name " + ownerKey);
        return ownersApi.createOwner(owner);
    }

    public RoleDTO createRole(String ownerKey, String access) throws ApiException {
        return createRoleForUser(ownerKey, null, access);
    }

    public RoleDTO createAllAccessRoleForUser(String ownerKey, UserDTO user) throws ApiException {
        return createRoleForUser(ownerKey, user, "ALL");
    }

    public RoleDTO createRoleForUser(String ownerKey, UserDTO user, String access)
        throws ApiException {
        PermissionBlueprintDTO permission = createOwnerPermission(ownerKey, access);
        RolesApi rolesApi = new RolesApi(apiClient);
        RoleDTO role = new RoleDTO();
        role.setName(ownerKey + "_" + access);

        if (user != null) {
            role.addUsersItem(user);
        }
        role.addPermissionsItem(permission);

        return rolesApi.createRole(role);
    }

    public PermissionBlueprintDTO createOwnerPermission(String ownerKey, String access) throws ApiException {
        return createPermission(ownerKey, access, "OWNER");
    }

    public PermissionBlueprintDTO createPermission(String ownerKey, String access, String type)
        throws ApiException {
        NestedOwnerDTO nestedOwner = new NestedOwnerDTO();
        nestedOwner.setKey(ownerKey);

        PermissionBlueprintDTO permission = new PermissionBlueprintDTO();
        permission.setOwner(nestedOwner);
        permission.setType(type);
        permission.setAccess(access);

        return permission;
    }

    /*
    public RoleDTO addUserToRole(String rolename, UserDTO user) throws ApiException {
        return addUserToRole(rolename, user.getUsername());
    }

    public RoleDTO addUserToRole(RoleDTO role, String username) throws ApiException {
        return addUserToRole(role.getName(), username);
    }

    public RoleDTO addUserToRole(RoleDTO role, UserDTO user) throws ApiException {
        return addUserToRole(role.getName(), user.getUsername());
    }

    public RoleDTO addUserToRole(String roleName, String username) throws ApiException {
        RolesApi rolesApi = new RolesApi(apiClient);
        // FIXME: this doesn't work right now.  The generated client is sending in a Object as the
        //  POST body even though the API spec doesn't define a POST body.  Jackson then chokes on
        //  serializing an empty Object. Need to figure that out.
        return rolesApi.addUserToRole(roleName, username);
    }
*/
}

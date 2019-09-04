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
import org.candlepin.client.model.CdnDTO;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.model.ProductDTO;
import org.candlepin.client.model.RoleDTO;
import org.candlepin.client.model.UserDTO;
import org.candlepin.client.resources.CdnApi;
import org.candlepin.client.resources.DistributorVersionsApi;
import org.candlepin.client.resources.OwnersApi;
import org.candlepin.client.resources.RolesApi;
import org.candlepin.client.resources.RulesApi;
import org.candlepin.client.resources.UsersApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to track all owners, users, roles, etc. created during a test so that they can
 * be deleted at the end of the test.
 */
@Component
@Scope("thread")
public class TestManifest {
    private final ApiClient apiClient;

    private final List<OwnerDTO> owners = new ArrayList<>();
    private final List<ProductDTO> products = new ArrayList<>();
    private final List<UserDTO> users = new ArrayList<>();
    private final List<RoleDTO> roles = new ArrayList<>();
    private final List<CdnDTO> cdns = new ArrayList<>();

    private final List<String> ownerKeys = new ArrayList<>();
    private final List<String> productIds = new ArrayList<>();
    private final List<String> userNames = new ArrayList<>();
    private final List<String> roleNames = new ArrayList<>();
    private final List<String> cdnLabels = new ArrayList<>();
    private final List<String> distributorVersionIds = new ArrayList<>();

    private boolean clearRules = false;

    @Autowired
    public TestManifest(@Qualifier("adminApiClient") ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void push(OwnerDTO o) {
        owners.add(o);
    }

    public void pushOwner(String key) {
        ownerKeys.add(key);
    }

    public void pop(OwnerDTO o) {
        owners.remove(o);
    }

    public void popOwner(String key) {
        ownerKeys.remove(key);
    }

    public void push(ProductDTO p) {
        products.add(p);
    }

    public void pushProduct(String id) {
        productIds.add(id);
    }

    public void pop(ProductDTO p) {
        products.remove(p);
    }

    public void pop(String id) {
        productIds.remove(id);
    }

    public void push(UserDTO u) {
        users.add(u);
    }

    public void pushUser(String name) {
        userNames.add(name);
    }

    public void pop(UserDTO u) {
        users.remove(u);
    }

    public void popUser(String name) {
        userNames.remove(name);
    }

    public void push(RoleDTO r) {
        roles.add(r);
    }

    public void pushRole(String name) {
        roleNames.add(name);
    }

    public void pop(RoleDTO r) {
        roles.remove(r);
    }

    public void popRole(String name) {
        roleNames.remove(name);
    }

    public void push(CdnDTO c) {
        cdns.add(c);
    }

    public void pushCdn(String label) {
        cdnLabels.add(label);
    }

    public void pop(CdnDTO c) {
        cdns.remove(c);
    }

    public void popCdn(String label) {
        cdnLabels.remove(label);
    }

    public void pushDistributorVersion(String id) {
        distributorVersionIds.add(id);
    }

    public void popDistributorVersion(String id) {
        distributorVersionIds.remove(id);
    }

    public void shouldClearRules() {
        clearRules = true;
    }

    public void clearManifest() {
        clearRoles();
        clearOwners();
        clearUsers();
        clearDistributorVersions();
        clearCdns();

        if (clearRules) {
            clearRules();
        }
    }

    private void clearRoles() {
        RolesApi api = new RolesApi(apiClient);
        roles.forEach(x -> api.deleteRole(x.getName()));
        roleNames.forEach(api::deleteRole);
    }

    private void clearOwners() {
        OwnersApi api = new OwnersApi(apiClient);
        owners.forEach(x -> api.deleteOwner(x.getKey(), true, true));
        ownerKeys.forEach(x -> api.deleteOwner(x, true, true));
    }

    private void clearUsers() {
        UsersApi api = new UsersApi(apiClient);
        users.forEach(x -> api.deleteUser(x.getUsername()));
        userNames.forEach(api::deleteUser);
    }

    private void clearDistributorVersions() {
        DistributorVersionsApi api = new DistributorVersionsApi(apiClient);
        distributorVersionIds.forEach(api::deleteDistributorVersion);
    }

    private void clearCdns() {
        CdnApi api = new CdnApi(apiClient);
        cdns.forEach(x -> api.deleteCDN(x.getLabel()));
        cdnLabels.forEach(api::deleteCDN);
    }

    private void clearRules() {
        RulesApi api = new RulesApi(apiClient);
        api.delete();
    }
}

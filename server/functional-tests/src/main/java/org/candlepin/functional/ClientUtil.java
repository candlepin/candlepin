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
import org.candlepin.client.model.UserCreationRequest;
import org.candlepin.client.resources.UsersApi;

/**
 * Utility for creating various ApiClients
 */
public class ClientUtil {
    private final ApiClient adminApiClient;

    public ClientUtil(ApiClient adminApiClient) {
        this.adminApiClient = adminApiClient;
    }

    public ApiClient userClient(String username, String owner, ApiClientBuilder apiClientBuilder)
        throws ApiException {
        String password = TestUtil.randomString(10);
        UserCreationRequest userReq = new UserCreationRequest();
        userReq.setUsername(username);
        userReq.setPassword(password);

        UsersApi usersApi = new UsersApi(adminApiClient);
        usersApi.createUser(userReq);

        // TODO associate with an owner

        return apiClientBuilder.withUsername(username).withPassword(password).build();
    }
}

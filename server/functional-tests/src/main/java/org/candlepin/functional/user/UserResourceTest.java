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
package org.candlepin.functional.user;

import org.candlepin.client.ApiClient;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.functional.ApiClientBuilder;
import org.candlepin.functional.FunctionalTestCase;
import org.candlepin.functional.TestUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test the /owners resource
 */
@FunctionalTestCase
public class UserResourceTest {

    @Autowired private ApiClient adminApiClient;
    @Autowired private ApiClientBuilder clientBuilder;

    @Test
    public void shouldListOwnersForUser() throws Exception {
        OwnerDTO owner = TestUtil.trivialOwner(adminApiClient);
//        // TODO assign user to owner!
//        String username = TestUtil.randomString("user1");
//        ApiClient userClient = new ClientUtil(adminApiClient).userClient(username, clientBuilder);
//
//        UsersApi usersApi = new UsersApi(userClient);
//        usersApi.listUsersOwners(username);
    }

}

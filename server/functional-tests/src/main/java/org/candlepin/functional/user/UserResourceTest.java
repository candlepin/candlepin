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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.client.ApiClient;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.resources.UsersApi;
import org.candlepin.functional.ClientUtil;
import org.candlepin.functional.FunctionalTestCase;
import org.candlepin.functional.TestUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Test the /owners resource
 */
@FunctionalTestCase
public class UserResourceTest {

    @Autowired @Qualifier("adminApiClient") private ApiClient adminApiClient;
    @Autowired private ClientUtil clientUtil;
    @Autowired private TestUtil testUtil;

    private OwnerDTO owner;

    @BeforeEach
    public void setUp() throws Exception {
        // TODO
    }

    @AfterEach
    public void tearDown() throws Exception {
        // TODO
    }

    @Test
    public void shouldListOwnersForUser() throws Exception {
        OwnerDTO owner = testUtil.trivialOwner();
        String username = TestUtil.randomString("user1");
        ApiClient userClient = clientUtil.newUserAndClient(username, owner.getKey());

        UsersApi usersApi = new UsersApi(userClient);
        List<OwnerDTO> users = usersApi.listUsersOwners(username);
        assertEquals(1, users.size());
        assertEquals(owner, users.get(0));
    }

    @Test
    public void shouldRaise404DeletingUnknownUser() throws Exception {
        UsersApi usersApi = new UsersApi(adminApiClient);
        usersApi.deleteUser("does_not_exist");
    }



}

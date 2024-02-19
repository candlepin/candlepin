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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.RoleDTO;
import org.candlepin.dto.api.server.v1.UserDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserInvalidException;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


/**
 * UserResourceTest
 */
public class UserResourceTest {

    private ModelTranslator modelTranslator;
    private OwnerCurator mockOwnerCurator;
    private UserServiceAdapter mockUserServiceAdapter;
    private I18n mockI18n;
    private UserResource userResource;

    @BeforeEach
    public void init() throws Exception {

        ConsumerTypeCurator mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        EnvironmentCurator mockEnvironmentCurator = mock(EnvironmentCurator.class);

        this.mockUserServiceAdapter = mock(UserServiceAdapter.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);
        this.mockI18n = mock(I18n.class);
        this.modelTranslator = new StandardTranslator(mockConsumerTypeCurator, mockEnvironmentCurator,
            mockOwnerCurator);

        this.userResource = new UserResource(this.mockUserServiceAdapter, this.mockI18n,
            this.mockOwnerCurator, this.modelTranslator);
    }

    @Test
    public void testCreateUser() {
        UserDTO dto = new UserDTO()
            .username("test-user")
            .password("banana")
            .superAdmin(true);

        when(this.mockUserServiceAdapter.createUser(any())).thenReturn(
            InfoAdapter.userInfoAdapter(dto));

        UserDTO output = this.userResource.createUser(dto);

        assertNotNull(output);
    }

    @Test
    public void testCreateUserNoUsername() {
        UserDTO dto = new UserDTO()
            .password("banana")
            .superAdmin(true);

        assertThrows(BadRequestException.class, () -> this.userResource.createUser(dto));
    }

    @Test
    public void testCreateUserWithNull() {
        assertThrows(BadRequestException.class, () -> this.userResource.createUser(null));
    }

    @Test
    public void testCreateUserWithExistingUser() {
        UserDTO dto = new UserDTO()
            .username("test-user")
            .password("banana")
            .superAdmin(true);

        when(this.mockUserServiceAdapter.findByLogin(dto.getUsername())).thenReturn(
            InfoAdapter.userInfoAdapter(dto));

        assertThrows(ConflictException.class, () -> this.userResource.createUser(dto));
    }

    @Test
    public void testLookupUser() {
        User user = new User()
            .setUsername("test-user")
            .setHashedPassword("banana")
            .setSuperAdmin(true);

        when(this.mockUserServiceAdapter.findByLogin(user.getUsername())).thenReturn(user);

        UserDTO output = this.userResource.getUserInfo(user.getUsername());

        assertNotNull(output);
    }

    @Test
    public void testLookupUserDoesntExist() {
        when(this.mockUserServiceAdapter.findByLogin(anyString())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> this.userResource.getUserInfo("no such user"));
    }

    @Test
    public void testListAllUsers() {
        int userCount = 5;
        List<User> listOfUsers = new ArrayList<>();
        for (int i = 0; i < userCount; ++i) {
            User user = new User()
                .setUsername("test-user-" + i)
                .setHashedPassword("banana")
                .setSuperAdmin(true);

            listOfUsers.add(user);
        }

        doReturn(listOfUsers).when(this.mockUserServiceAdapter).listUsers();

        Stream<UserDTO> response = this.userResource.listUsers();

        assertNotNull(response);
        assertThat(response.toList())
            .isNotNull()
            .hasSize(5)
            .extracting(UserDTO::getUsername)
            .allSatisfy(username -> {
                assertThat(username).isNotNull();
                assertThat(listOfUsers)
                    .extracting(User::getUsername)
                    .contains(username);
            });
    }

    @Test
    public void testListAllOwners() {
        User user = new User()
            .setUsername("dummyuser" + TestUtil.randomInt())
            .setHashedPassword("password");

        Owner owner = TestUtil.createOwner("owner_key1");

        when(this.mockUserServiceAdapter.findByLogin(user.getUsername())).thenReturn(user);
        doReturn(List.of(owner)).when(this.mockUserServiceAdapter).getAccessibleOwners(user.getUsername());

        Stream<OwnerDTO> response = this.userResource.listUserOwners(user.getUsername());

        assertNotNull(response);

        List<OwnerDTO> owners = response.toList();

        assertEquals(1, owners.size());
    }

    @Test
    public void testDeleteUser() {
        User user = new User()
            .setUsername("test-user")
            .setHashedPassword("banana")
            .setSuperAdmin(true);

        when(this.mockUserServiceAdapter.findByLogin(user.getUsername())).thenReturn(user);

        this.userResource.deleteUser(user.getUsername());

        verify(this.mockUserServiceAdapter, times(1)).deleteUser(user.getUsername());
    }

    @Test
    public void testDeleteUserNotFound() {
        when(this.mockUserServiceAdapter.findByLogin(anyString())).thenReturn(null);

        assertThrows(NotFoundException.class, () -> this.userResource.deleteUser("no such user"));
    }

    @Test
    public void testUpdateUser() {
        User user = new User()
            .setUsername("test-user")
            .setHashedPassword("banana")
            .setSuperAdmin(false);

        UserDTO update = new UserDTO()
            .username("Luke");

        when(this.mockUserServiceAdapter.findByLogin(user.getUsername())).thenReturn(user);
        when(this.mockUserServiceAdapter.updateUser(eq(user.getUsername()), any())).then(
            AdditionalAnswers.returnsSecondArg());

        UserDTO result = this.userResource.updateUser("test-user", update);

        assertNotNull(result);
    }

    @Test
    public void testUpdateUsersNoLogin() {
        UserDTO dto = new UserDTO()
            .username("henri")
            .password("password");

        assertThrows(NotFoundException.class, () -> this.userResource.updateUser("JarJarIsMyCopilot", dto));
    }

    @Test
    public void testGetRolesForUser() {
        User user = new User()
            .setUsername("henri")
            .setHashedPassword("password");

        Role userRole = new Role("name");
        userRole.addUser(user);

        when(this.mockUserServiceAdapter.findByLogin(user.getUsername())).thenReturn(user);

        Stream<RoleDTO> userRoles = this.userResource.getUserRoles(user.getUsername());

        assertNotNull(userRoles);
        assertEquals(1, userRoles.toList().size());
    }

    @Test
    public void testFetchByUsernameUserServiceException() {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.mockI18n, this.mockOwnerCurator,
            this.modelTranslator);
        doThrow(new UserInvalidException("test_user")).when(adapter).findByLogin("test_user");
        assertThrows(UserInvalidException.class, () -> resource.getUserInfo("test_user"));
    }

    @Test
    public void testCreateUserServiceException() {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.mockI18n, this.mockOwnerCurator,
            this.modelTranslator);
        doThrow(new UserDisabledException("test_user")).when(adapter).findByLogin("test_user");
        assertThrows(UserDisabledException.class, () -> resource.getUserInfo("test_user"));
    }

    @Test
    public void testListUserOwnersUserServiceException() {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.mockI18n, this.mockOwnerCurator,
            this.modelTranslator);
        doThrow(new UserInvalidException("test_user")).when(adapter).getAccessibleOwners("test_user");
        assertThrows(CandlepinException.class, () -> resource.getUserInfo("test_user"));
    }
}

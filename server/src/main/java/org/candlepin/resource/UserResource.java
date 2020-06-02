/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.User;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import com.google.inject.Inject;

import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.ws.rs.Path;



/**
 * UserResource
 */
@Path("/users")
@Api(value = "users", authorizations = { @Authorization("basic") })
public class UserResource implements UsersApi {

    private final UserServiceAdapter userService;
    private final I18n i18n;
    private final OwnerCurator ownerCurator;
    private final ModelTranslator modelTranslator;

    @Inject
    public UserResource(UserServiceAdapter userService, I18n i18n, OwnerCurator ownerCurator,
        ModelTranslator modelTranslator) {
        this.userService = Objects.requireNonNull(userService);
        this.i18n = Objects.requireNonNull(i18n);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
    }

    /**
     * Fetches the user for a given username, or throws a NotFoundException if the username cannot
     * be found.
     *
     * @param username
     *  The username of the user to fetch
     *
     * @throws NotFoundException
     *  if a user for the given username cannot be found
     *
     * @throws BadRequestException
     *  if the given user is null or empty
     *
     * @return
     *  The user for the given username
     */
    private UserInfo fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new BadRequestException(this.i18n.tr("username is null or empty"));
        }

        UserInfo user = this.userService.findByLogin(username);
        if (user == null) {
            throw new NotFoundException(this.i18n.tr("User not found: {0}", username));
        }

        return user;
    }

    @Override
    public Stream<UserDTO> listUsers() {
        Collection<? extends UserInfo> users = userService.listUsers();

        return users != null ?
            users.stream().map(this.modelTranslator.getStreamMapper(UserInfo.class, UserDTO.class)) :
            null;
    }

    @Override
    public UserDTO getUserInfo(@Verify(User.class) String username) {
        return this.modelTranslator.translate(this.fetchUserByUsername(username), UserDTO.class);
    }

    /*
     * getUserRoles will only return roles for one user. If you want a
     * full view of a role, use /roles/ instead.
     */
    @Override
    public Stream<RoleDTO> getUserRoles(@Verify(User.class) String username) {
        UserInfo user = this.fetchUserByUsername(username);

        Collection<? extends RoleInfo> roles = user.getRoles();
        if (roles != null) {
            UserDTO udto = this.modelTranslator.translate(user, UserDTO.class);
            Set<UserDTO> users = Collections.singleton(udto);

            // Make sure we clear/overwrite the collection of users with our singleton users set
            // to avoid leaking role details about other users.
            return roles.stream()
                .map(this.modelTranslator.getStreamMapper(RoleInfo.class, RoleDTO.class))
                .map(e -> e.users(new HashSet<>(users)));
        }

        return Stream.empty();
    }

    @Override
    public UserDTO createUser(UserDTO dto) {
        if (dto == null) {
            throw new BadRequestException(this.i18n.tr("user data is null or empty"));
        }

        if (dto.getUsername() == null) {
            throw new BadRequestException(this.i18n.tr("Username not specified"));
        }

        if (this.userService.findByLogin(dto.getUsername()) != null) {
            throw new ConflictException(this.i18n.tr("User already exists: {0}", dto.getUsername()));
        }

        return this.modelTranslator.translate(
            // Translating UserDTO to User Info because UserDTO is no longer supporting UserInfo
            userService.createUser(InfoAdapter.userInfoAdapter(dto)),
                UserDTO.class);
    }

    @Override
    public UserDTO updateUser(@Verify(User.class) String username, UserDTO dto) {

        // We don't actually need the user, but we do this for quick verification and better error
        // generation
        UserInfo user = this.fetchUserByUsername(username);

        return this.modelTranslator.translate(
            userService.updateUser(username, InfoAdapter.userInfoAdapter(dto)),
                UserDTO.class);
    }

    @Override
    public void deleteUser(String username) {
        UserInfo user = this.fetchUserByUsername(username);

        userService.deleteUser(username);
    }

    /**
     * Retrieve a list of owners the user can register systems to.
     * Previously this represented owners the user was an admin for. Because the
     * client uses this API call to list the owners a user can register to, when
     * we introduced 'my systems' administrator, we have to change its meaning to
     * listing the owners that can be registered to by default to maintain
     * compatibility with released clients.
     */
    // TODO: should probably accept access level and sub-resource query params someday
    @Override
    public Stream<OwnerDTO> listUserOwners(@Verify(User.class) String username) {

        // Fetch the user for a simple existence check. We don't actually need it.
        UserInfo user = this.fetchUserByUsername(username);

        Collection<? extends OwnerInfo> owners = this.userService.getAccessibleOwners(username);

        if (owners != null) {
            // If this ends up being a bottleneck, change this to do a bulk owner lookup

            return owners.stream()
                .map(this::resolveOwner)
                .map(this.modelTranslator.getStreamMapper(Owner.class, OwnerDTO.class));
        }

        return null;
    }

    private Owner resolveOwner(OwnerInfo oinfo) {
        if (oinfo != null) {
            // This is a bit of an odd situation. Should we just pass through what the adapter gave us
            // anyway?

            if (oinfo.getKey() == null || oinfo.getKey().isEmpty()) {
                throw new IllegalStateException("owner lacks identifying information: " + oinfo);
            }

            Owner owner = this.ownerCurator.getByKey(oinfo.getKey());
            if (owner == null) {
                owner = new Owner(oinfo.getKey());
            }

            return owner;
        }

        return null;
    }

}

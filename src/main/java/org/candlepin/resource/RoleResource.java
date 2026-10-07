/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.auth.Access;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.server.v1.RoleDTO;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.resource.server.v1.RolesApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.util.UserServiceExceptionTranslator;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import com.google.inject.persist.Transactional;

import org.jboss.resteasy.spi.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.inject.Inject;

/**
 * API implementation for Role operations
 */
public class RoleResource implements RolesApi {
    private static final Logger log = LoggerFactory.getLogger(RoleResource.class);

    private UserServiceAdapter userService;
    private I18n i18n;
    private ModelTranslator modelTranslator;
    private DTOValidator validator;

    @Inject
    public RoleResource(UserServiceAdapter userService, OwnerCurator ownerCurator,
        PermissionBlueprintCurator permCurator, I18n i18n, ModelTranslator modelTranslator,
        DTOValidator validator) {

        this.userService = userService;
        this.i18n = i18n;
        this.modelTranslator = modelTranslator;
        this.validator = validator;
    }

    /**
     * Fetches the role for a given role name, or throws a NotFoundException if the specified role
     * cannot be found.
     *
     * @param roleName
     *  The name of the role to fetch
     *
     * @throws NotFoundException
     *  if a role for the given role name cannot be found
     *
     * @throws BadRequestException
     *  if the given role name is null or empty
     *
     * @return
     *  The role for the given role name
     */
    protected RoleInfo fetchRoleByName(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new BadRequestException(this.i18n.tr("role name is null or empty"));
        }

        RoleInfo role = this.callUserService(() -> this.userService.getRole(roleName));
        if (role == null) {
            throw new NotFoundException(this.i18n.tr("Role not found: {0}", roleName));
        }

        return role;
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
    protected UserInfo fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new BadRequestException(this.i18n.tr("username is null or empty"));
        }

        UserInfo user = this.callUserService(() -> this.userService.findByLogin(username));
        if (user == null) {
            throw new NotFoundException(this.i18n.tr("User not found: {0}", username));
        }

        return user;
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO createRole(RoleDTO dto) {
        if (dto == null) {
            throw new BadRequestException(this.i18n.tr("role data is null or empty"));
        }

        if (dto.getName() == null || dto.getName().isEmpty()) {
            throw new BadRequestException(this.i18n.tr("role name not specified"));
        }

        if (this.callUserService(() -> this.userService.getRole(dto.getName())) != null) {
            throw new ConflictException(this.i18n.tr("Role already exists: {0}", dto.getName()));
        }

        validator.validateCollectionElementsNotNull(dto::getUsers, dto::getPermissions);
        RoleInfo role = this.callUserService(() -> this.userService.createRole(InfoAdapter.roleInfoAdapter(dto)));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO updateRole(String roleName, RoleDTO dto) {

        // We don't actually need the role, but we do this for quick verification and better error
        // generation
        this.fetchRoleByName(roleName);

        validator.validateCollectionElementsNotNull(dto::getUsers, dto::getPermissions);

        RoleInfo role = this.callUserService(() ->
            this.userService.updateRole(roleName, InfoAdapter.roleInfoAdapter(dto)));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO addRolePermission(String roleName, PermissionBlueprintDTO permission) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Don't allow NONE permissions to be created, this is currently just for
        // internal use:
        if (Access.NONE.name().equals(permission.getAccess())) {
            throw new BadRequestException(i18n.tr("Access type NONE not supported."));
        }

        RoleInfo role = this.callUserService(() -> this.userService.addPermissionToRole(roleName,
            InfoAdapter.permissionBlueprintInfoAdapter(permission)));

        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO removeRolePermission(String roleName, String permissionId) {

        // Validate role name
        this.fetchRoleByName(roleName);

        RoleInfo role = this.callUserService(() -> this.userService.removePermissionFromRole(roleName, permissionId));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO getRoleByName(String roleName) {
        RoleInfo role = this.fetchRoleByName(roleName);
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public void deleteRoleByName(String roleName) {
        // Validate role name
        this.fetchRoleByName(roleName);

        this.callUserService(() -> {
            this.userService.deleteRole(roleName);
            return null;
        });
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO addUserToRole(String roleName, String username) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Validate username
        this.fetchUserByUsername(username);

        RoleInfo role = this.callUserService(() -> this.userService.addUserToRole(roleName, username));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public RoleDTO deleteUserFromRole(String roleName, String username) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Validate username
        this.fetchUserByUsername(username);

        RoleInfo role = this.callUserService(() -> this.userService.removeUserFromRole(roleName, username));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public Stream<RoleDTO> getRoles() {
        // TODO: Add in filter options

        Collection<? extends RoleInfo> roles = this.callUserService(() -> this.userService.listRoles());
        return roles.stream().map(this.modelTranslator.getStreamMapper(RoleInfo.class, RoleDTO.class));
    }

    private <T> T callUserService(Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (UserServiceException e) {
            throw UserServiceExceptionTranslator.translate(e, this.i18n, log);
        }
    }
}

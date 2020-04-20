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

import org.candlepin.auth.Access;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import com.google.inject.Inject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.spi.BadRequestException;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;



/**
 *
 */
@Path("/roles")
@Api(value = "roles", authorizations = { @Authorization("basic") })
public class RoleResource {

    private UserServiceAdapter userService;
    private OwnerCurator ownerCurator;
    private PermissionBlueprintCurator permissionCurator;
    private I18n i18n;
    private ModelTranslator modelTranslator;
    private DTOValidator validator;

    @Inject
    public RoleResource(UserServiceAdapter userService, OwnerCurator ownerCurator,
        PermissionBlueprintCurator permCurator, I18n i18n, ModelTranslator modelTranslator,
        DTOValidator validator) {

        this.userService = userService;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.permissionCurator = permCurator;
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

        RoleInfo role = this.userService.getRole(roleName);
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

        UserInfo user = this.userService.findByLogin(username);
        if (user == null) {
            throw new NotFoundException(this.i18n.tr("User not found: {0}", username));
        }

        return user;
    }

    @ApiOperation(notes = "Creates a Role", value = "createRole")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO createRole(@ApiParam(name = "role", required = true) RoleDTO dto) {
        if (dto == null) {
            throw new BadRequestException(this.i18n.tr("role data is null or empty"));
        }

        if (dto.getName() == null || dto.getName().isEmpty()) {
            throw new BadRequestException(this.i18n.tr("role name not specified"));
        }

        if (this.userService.getRole(dto.getName()) != null) {
            throw new ConflictException(this.i18n.tr("Role already exists: {0}", dto.getName()));
        }

        validator.validateCollectionElementsNotNull(dto::getUsers, dto::getPermissions);
        RoleInfo role = this.userService.createRole(InfoAdapter.roleInfoAdapter(dto));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Updates a Role.  To avoid race conditions, we do not support " +
        "updating the user or permission collections. Currently this call will only update " +
        "the role name. See the specific nested POST/DELETE calls for modifying users and" +
        " permissions.", value = "updateRole")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Path("{role_name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO updateRole(@PathParam("role_name") String roleName,
        @ApiParam(name = "role", required = true) RoleDTO dto) {

        // We don't actually need the role, but we do this for quick verification and better error
        // generation
        this.fetchRoleByName(roleName);

        validator.validateCollectionElementsNotNull(dto::getUsers, dto::getPermissions);

        RoleInfo role = this.userService.updateRole(roleName, InfoAdapter.roleInfoAdapter(dto));
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Adds a Permission to a Role. Returns the updated Role.",
        value = "addRolePermission")
    @ApiResponses({ @ApiResponse(code = 404, message = ""), @ApiResponse(code = 400, message = "") })
    @POST
    @Path("{role_name}/permissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO addRolePermission(@PathParam("role_name") String roleName,
        @ApiParam(name = "permissionBlueprint", required = true) PermissionBlueprintDTO permission) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Don't allow NONE permissions to be created, this is currently just for
        // internal use:
        if (Access.NONE.name().equals(permission.getAccess())) {
            throw new BadRequestException(i18n.tr("Access type NONE not supported."));
        }

        RoleInfo role = this.userService.addPermissionToRole(roleName,
            InfoAdapter.permissionBlueprintInfoAdapter(permission));

        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Removes a Permission from a Role. Returns the updated Role.",
        value = "removeRolePermission")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })    @DELETE
    @Path("{role_name}/permissions/{perm_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO removeRolePermission(@PathParam("role_name") String roleName,
        @PathParam("perm_id") String permissionId) {

        // Validate role name
        this.fetchRoleByName(roleName);

        RoleInfo role = this.userService.removePermissionFromRole(roleName, permissionId);
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Retrieves a single Role", value = "getRole")
    @GET
    @Path("{role_name}")
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO getRole(@PathParam("role_name") String roleName) {
        RoleInfo role = this.fetchRoleByName(roleName);
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Removes a Role", value = "deleteRole")
    @DELETE
    @Path("/{role_name}")
    @Produces(MediaType.WILDCARD)
    public void deleteRole(@PathParam("role_name") String roleName) {
        // Validate role name
        this.fetchRoleByName(roleName);

        this.userService.deleteRole(roleName);
    }

    @ApiOperation(notes = "Adds a User to a Role", value = "addUser")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @POST
    @Path("/{role_name}/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public RoleDTO addUserToRole(@PathParam("role_name") String roleName,
        @PathParam("username") String username) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Validate username
        this.fetchUserByUsername(username);

        RoleInfo role = this.userService.addUserToRole(roleName, username);
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Removes a User from a Role", value = "deleteUser")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @DELETE
    @Path("/{role_name}/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public RoleDTO deleteUserFromRole(@PathParam("role_name") String roleName,
        @PathParam("username") String username) {

        // Validate role name
        this.fetchRoleByName(roleName);

        // Validate username
        this.fetchUserByUsername(username);

        RoleInfo role = this.userService.removeUserFromRole(roleName, username);
        return this.modelTranslator.translate(role, RoleDTO.class);
    }

    @ApiOperation(notes = "Retrieves a list of Roles", value = "getRoles",
        response = RoleDTO.class, responseContainer = "List")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "roles")
    public Stream<RoleDTO> getRoles() {
        // TODO: Add in filter options

        Collection<? extends RoleInfo> roles = this.userService.listRoles();
        return roles.stream().map(this.modelTranslator.getStreamMapper(RoleInfo.class, RoleDTO.class));
    }
}

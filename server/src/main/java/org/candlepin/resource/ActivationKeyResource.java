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
import org.candlepin.controller.ActivationKeyController;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Pool;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;

import com.google.inject.Inject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * ActivationKeyResource
 */
@Path("/activation_keys")
@Api(value = "activation_keys", authorizations = { @Authorization("basic") })
public class ActivationKeyResource {

    private static final Logger log = LoggerFactory.getLogger(ActivationKeyResource.class);

    private final ActivationKeyController activationKeyController;
    private final ModelTranslator translator;

    @Inject
    public ActivationKeyResource(
        ActivationKeyController activationKeyController, ModelTranslator translator) {
        this.activationKeyController = Objects.requireNonNull(activationKeyController);
        this.translator = Objects.requireNonNull(translator);
    }

    @ApiOperation(notes = "Retrieves a single Activation Key", value = "Get Activation Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyDTO getActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId) {
        log.debug("Searching for activation key: {}", activationKeyId);
        ActivationKey key = this.activationKeyController.getActivationKey(activationKeyId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Retrieves a list of Pools based on the Activation Key",
        value = "Get Activation Key Pools", response = PoolDTO.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("{activation_key_id}/pools")
    @Produces(MediaType.APPLICATION_JSON)
    public Iterator<PoolDTO> getActivationKeyPools(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId) {
        log.debug("Searching for pools of activation key: {}", activationKeyId);
        return this.activationKeyController.getActivationKeyPools(activationKeyId)
            .stream()
            .map(akp -> translator.translate(akp.getPool(), PoolDTO.class))
            .collect(Collectors.toSet()).iterator();
    }

    @ApiOperation(notes = "Updates an Activation Key", value = "Update Activation Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ActivationKeyDTO updateActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId,
        @ApiParam(name = "update", required = true)
        @NotNull ActivationKeyDTO update) {
        log.debug("Updating activation key: {}", activationKeyId);
        ActivationKey toUpdate = this.activationKeyController
            .updateActivationKey(activationKeyId, toActivationKey(update));
        return this.translator.translate(toUpdate, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Adds a Pool to an Activation Key", value = "Add Pool to Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @POST
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public ActivationKeyDTO addPoolToKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId,
        @PathParam("pool_id")
        @Verify(Pool.class)
        @NotEmpty String poolId,
        @QueryParam("quantity")
        @Positive Long quantity) {
        log.debug("Adding pool: {} to activation key: {}", poolId, activationKeyId);
        ActivationKey key = this.activationKeyController.addPoolToKey(activationKeyId, poolId, quantity);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Removes a Pool from an Activation Key", value = "Remove Pool From Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyDTO removePoolFromKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId,
        @PathParam("pool_id")
        @Verify(Pool.class)
        @NotEmpty String poolId) {
        log.debug("Removing pool: {} from activation key: {}", poolId, activationKeyId);
        ActivationKey key = this.activationKeyController.removePoolFromKey(activationKeyId, poolId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Adds an Product ID to an Activation Key", value = "Add Product ID to key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @POST
    @Path("{activation_key_id}/product/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public ActivationKeyDTO addProductIdToKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId,
        @PathParam("product_id")
        @NotEmpty String productId) {
        log.debug("Adding product ID: {} to activation key: {}", productId, activationKeyId);
        ActivationKey key = this.activationKeyController.addProductIdToKey(activationKeyId, productId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Removes a Product ID from an Activation Key", value = "Remove Product Id from Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("{activation_key_id}/product/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyDTO removeProductIdFromKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId,
        @PathParam("product_id")
        @NotEmpty String productId) {
        log.debug("Removing product ID: {} from activation key: {}", productId, activationKeyId);
        ActivationKey key = this.activationKeyController.removeProductIdFromKey(activationKeyId, productId);
        return this.translator.translate(key, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Retrieves a list of Activation Keys", value = "findActivationKey",
        response = ActivationKey.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<ActivationKeyDTO> findActivationKey() {
        log.debug("Searching for activation keys");
        CandlepinQuery<ActivationKey> query = this.activationKeyController.findActivationKey();
        return this.translator.translateQuery(query, ActivationKeyDTO.class);
    }

    @ApiOperation(notes = "Removes an Activation Key", value = "deleteActivationKey")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class)
        @NotEmpty String activationKeyId) {
        log.debug("Deleting activation key: {}", activationKeyId);
        this.activationKeyController.deleteActivationKey(activationKeyId);
    }

    private ActivationKey toActivationKey(ActivationKeyDTO update) {
        ActivationKey toUpdate = new ActivationKey();

        toUpdate.setName(update.getName());
        toUpdate.setServiceLevel(update.getServiceLevel());
        toUpdate.setReleaseVer(new Release(update.getReleaseVersion()));
        toUpdate.setDescription(update.getDescription());
        toUpdate.setUsage(update.getUsage());
        toUpdate.setRole(update.getRole());
        toUpdate.setAddOns(update.getAddOns());
        toUpdate.setAutoAttach(update.isAutoAttach());

        return toUpdate;
    }

}

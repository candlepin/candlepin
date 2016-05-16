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
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.resource.dto.ActivationKeyData;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.util.ServiceLevelValidator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * ActivationKeyResource
 */
@Path("/activation_keys")
@Api("activation_keys")
public class ActivationKeyResource {
    private static Logger log = LoggerFactory.getLogger(ActivationKeyResource.class);
    private ActivationKeyCurator activationKeyCurator;
    private ProductCurator productCurator;
    private PoolManager poolManager;
    private I18n i18n;
    private ServiceLevelValidator serviceLevelValidator;
    private ActivationKeyRules activationKeyRules;

    @Inject
    public ActivationKeyResource(ActivationKeyCurator activationKeyCurator,
        I18n i18n, PoolManager poolManager,
        ServiceLevelValidator serviceLevelValidator,
        ActivationKeyRules activationKeyRules,
        ProductCurator productCurator) {

        this.activationKeyCurator = activationKeyCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.serviceLevelValidator = serviceLevelValidator;
        this.activationKeyRules = activationKeyRules;
        this.productCurator = productCurator;
    }

    @ApiOperation(notes = "Retrieves a single Activation Key", value = "Get Activation Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyData getActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);

        return new ActivationKeyData(key);
    }

    @ApiOperation(notes = "Retrieves a list of Pools based on the Activation Key",
        value = "Get Activation Key Pools")
    @ApiResponses({ @ApiResponse(code = 400, message = "")})
    @GET
    @Path("{activation_key_id}/pools")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Pool> getActivationKeyPools(
        @PathParam("activation_key_id") String activationKeyId) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        List<Pool> pools = new ArrayList<Pool>();

        for (ActivationKeyPool akp : key.getPools()) {
            pools.add(akp.getPool());
        }

        return pools;
    }

    @ApiOperation(notes = "Updates an Activation Key", value = "Update Activation Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ActivationKeyData updateActivationKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        ActivationKeyData update) {

        ActivationKey toUpdate = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        if (update.getName() != null) {
            toUpdate.setName(update.getName());
        }
        String serviceLevel = update.getServiceLevel();
        if (serviceLevel != null) {
            serviceLevelValidator.validate(toUpdate.getOwner(), serviceLevel);
            toUpdate.setServiceLevel(serviceLevel);
        }
        if (update.getReleaseVersion() != null) {
            toUpdate.setReleaseVer(update.getReleaseVersion());
        }
        if (update.getDescription() != null) {
            toUpdate.setDescription(update.getDescription());
        }
        if (update.isAutoAttach() != null) {
            toUpdate.setAutoAttach(update.isAutoAttach());
        }
        activationKeyCurator.merge(toUpdate);

        return new ActivationKeyData(toUpdate);
    }

    @ApiOperation(notes = "Adds a Pool to an Activation Key", value = "Add Pool to Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "")})
    @POST
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public ActivationKeyData addPoolToKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id") @Verify(Pool.class) String poolId,
        @QueryParam("quantity") Long quantity) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Pool pool = findPool(poolId);

        // Throws a BadRequestException if adding pool to key is a bad idea
        activationKeyRules.validatePoolForActKey(key, pool, quantity);

        // Make sure we don't try to register the pool twice.
        if (key.hasPool(pool)) {
            throw new BadRequestException(
                i18n.tr("Pool ID \"{0}\" has already been registered with this activation key", poolId)
            );
        }

        key.addPool(pool, quantity);
        activationKeyCurator.update(key);

        return new ActivationKeyData(key);
    }

    @ApiOperation(notes = "Removes a Pool from an Activation Key", value = "Remove Pool From Key")
    @ApiResponses({ @ApiResponse(code =  400, message = "")})
    @DELETE
    @Path("{activation_key_id}/pools/{pool_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyData removePoolFromKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("pool_id")
        @Verify(Pool.class) String poolId) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Pool pool = findPool(poolId);
        key.removePool(pool);
        activationKeyCurator.update(key);

        return new ActivationKeyData(key);
    }

    @ApiOperation(notes = "Adds an Product ID to an Activation Key", value = "Add Product ID to key")
    @ApiResponses({ @ApiResponse(code = 400, message = "")})
    @POST
    @Path("{activation_key_id}/product/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public ActivationKeyData addProductIdToKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("product_id") String productId) {

        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);

        // Make sure we don't try to register the product ID twice.
        if (key.hasProduct(product)) {
            throw new BadRequestException(
                i18n.tr("Product ID \"{0}\" has already been registered with this activation key", productId)
            );
        }

        key.addProduct(product);
        activationKeyCurator.update(key);

        return new ActivationKeyData(key);
    }

    @ApiOperation(notes = "Removes a Product ID from an Activation Key", value = "Remove Product Id from Key")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("{activation_key_id}/product/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ActivationKeyData removeProductIdFromKey(
        @PathParam("activation_key_id") @Verify(ActivationKey.class) String activationKeyId,
        @PathParam("product_id") String productId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);
        Product product = confirmProduct(key.getOwner(), productId);
        key.removeProduct(product);
        activationKeyCurator.update(key);

        return new ActivationKeyData(key);
    }

    @ApiOperation(notes = "Retrieves a list of Activation Keys", value = "findActivationKey")
    @ApiResponses({ @ApiResponse(code = 200, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response findActivationKey() {
        // TODO: Replace this with use of a cursor/iterator
        final List<ActivationKey> keyList = activationKeyCurator.listAll();
        final ObjectMapper mapper = new JsonProvider(true)
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        StreamingOutput output = new StreamingOutput() {
            @Override
            public void write(OutputStream stream) throws IOException, WebApplicationException {
                JsonGenerator generator = mapper.getJsonFactory().createGenerator(stream);
                generator.writeStartArray();

                for (ActivationKey key : keyList) {
                    mapper.writeValue(generator, new ActivationKeyData(key));
                }

                generator.writeEndArray();
                generator.flush();
            }
        };

        return Response.ok(output).build();
    }

    @ApiOperation(notes = "Removes an Activation Key", value = "deleteActivationKey")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("{activation_key_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteActivationKey(
        @PathParam("activation_key_id")
        @Verify(ActivationKey.class) String activationKeyId) {
        ActivationKey key = activationKeyCurator.verifyAndLookupKey(activationKeyId);

        log.debug("Deleting activation key: {}", activationKeyId);

        activationKeyCurator.delete(key);
    }

    private Pool findPool(String poolId) {
        Pool pool = poolManager.find(poolId);

        if (pool == null) {
            throw new BadRequestException(i18n.tr("Pool with id {0} could not be found.", poolId));
        }

        return pool;
    }

    private Product confirmProduct(Owner o, String prodId) {
        Product prod = productCurator.lookupById(o, prodId);

        if (prod == null) {
            throw new BadRequestException(i18n.tr("Product with id {0} could not be found.", prodId));
        }

        return prod;
    }

}

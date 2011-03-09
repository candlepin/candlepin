/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.client;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;

import org.jboss.resteasy.client.ClientResponse;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * OwnerExportClient
 */
@Path("/owners")
public interface OwnerClient {

    @GET
    @Path("{owner_key}/replicate")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<Owner> replicateOwner(@PathParam("owner_key") String ownerKey);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/pools")
    ClientResponse<List<Pool>> replicatePools(
        @PathParam("owner_key") String ownerKey);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{owner_key}/entitlements")
    ClientResponse<List<Entitlement>> replicateEntitlements(
        @PathParam("owner_key") String ownerKey);

    @GET
    @Path("{owner_key}/consumers")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<List<Consumer>> replicateOwnerConsumers(
        @PathParam("owner_key") String ownerKey);
    
    @DELETE
    @Path("/{owner_key}")    
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteOwner(@PathParam("owner_key") String ownerKey,
        @QueryParam("revoke") @DefaultValue("true") boolean revoke);
}

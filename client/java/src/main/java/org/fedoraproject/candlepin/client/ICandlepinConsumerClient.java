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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.client.model.Consumer;
import org.fedoraproject.candlepin.client.model.Entitlement;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;
import org.jboss.resteasy.client.ClientResponse;

/**
 * CandlepinClient
 */
public interface ICandlepinConsumerClient {

    @POST
    @Path("consumers")
    @Consumes(MediaType.APPLICATION_JSON)
    Consumer register(Consumer aConsumer);

    @GET
    @Path("consumers/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<Consumer> getConsumer(@PathParam("uuid") String uuid);

    @DELETE
    @Path("consumers/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<Object> deleteConsumer(@PathParam("uuid") String uuid);

    
    
    @GET
    @Path("pools")
    @Produces(MediaType.APPLICATION_JSON)
    List<Pool> listPools();

    @GET
    @Path("pools")
    @Produces(MediaType.APPLICATION_JSON)
    List<Pool> listPools(@QueryParam("consumer") String uuid);

    @POST
    @Path("consumers/{uuid}/entitlements")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<List<Entitlement>> bindByEntitlementID(
        @PathParam("uuid") String uuid, @QueryParam("pool") Long poolId);

    @GET
    @Path("consumers/{uuid}/certificates")
    @Produces(MediaType.APPLICATION_JSON)
    List<EntitlementCertificate> getEntitlementCertificates(
        @PathParam("uuid") String uuid);
    
    @POST
    @Path("consumers/{uuid}/entitlements")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<List<Entitlement>> bindByProductId(
            @PathParam("uuid") String uuid, @QueryParam("product") String productId);
    
    @POST
    @Path("consumers/{uuid}/entitlements")
    @Produces(MediaType.APPLICATION_JSON)
    ClientResponse<List<Entitlement>> bindByRegNumber(
            @PathParam("uuid") String uuid, @QueryParam("token") String regnum);
    
    @DELETE
    @Path("consumers/{uuid}/certificates/{serialNo}")
    ClientResponse<Void>  unBindBySerialNumber(
            @PathParam("uuid") String uuid, @PathParam("serialNo") int serialNumber);
    
    @DELETE 
    @Path("consumers/{uuid}/entitlements/")
    ClientResponse<Void> unBindAll(@PathParam("uuid") String uuid);
    
}

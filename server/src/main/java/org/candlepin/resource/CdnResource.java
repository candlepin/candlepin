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

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * CdnResource
 */
@Path("/cdn")
@Api("cdn")
public class CdnResource {

    private I18n i18n;
    private CdnCurator curator;

    @Inject
    public CdnResource(I18n i18n,
        CdnCurator curator) {
        this.i18n = i18n;
        this.curator = curator;
    }

    @ApiOperation(notes = "Retrieves a list of CDN's", value = "getContentDeliveryNetworks")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Cdn> getContentDeliveryNetworks() {
        return curator.list();
    }

    @ApiOperation(notes = "Removes a CDN", value = "delete")
    @ApiResponses({ @ApiResponse(code =  400, message = ""), @ApiResponse(code =  404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
    @Path("/{label}")
    public void delete(@PathParam("label") String label,
        @Context Principal principal) {
        Cdn cdn = curator.lookupByLabel(label);
        if (cdn != null) {
            curator.delete(cdn);
        }
    }

    @ApiOperation(notes = "Creates a CDN @return a Cdn object", value = "create")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Cdn create(Cdn cdn,
        @Context Principal principal) {
        Cdn existing = curator.lookupByLabel(cdn.getLabel());
        if (existing != null) {
            throw new BadRequestException(
                i18n.tr("A CDN with the label {0}" +
                        "already exists", cdn.getLabel()));
        }
        return curator.create(cdn);
    }

    @ApiOperation(notes = "Updates a CDN @return a Cdn object", value = "update")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{label}")
    public Cdn update(@PathParam("label") String label,
        Cdn cdn,
        @Context Principal principal) {
        Cdn existing = verifyAndLookupCdn(label);
        if (!StringUtils.isBlank(cdn.getName())) {
            existing.setName(cdn.getName());
        }
        if (!StringUtils.isBlank(cdn.getUrl())) {
            existing.setUrl(cdn.getUrl());
        }
        if (cdn.getCertificate() != null) {
            existing.setCertificate(cdn.getCertificate());
        }
        curator.merge(existing);
        return existing;
    }

    private Cdn verifyAndLookupCdn(String label) {
        Cdn cdn = curator.lookupByLabel(label);

        if (cdn == null) {
            throw new NotFoundException(i18n.tr("No such content delivery network: {0}",
                label));
        }
        return cdn;
    }
}

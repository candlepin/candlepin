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

import org.candlepin.auth.Principal;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCurator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * DistributorVersionResource
 */
@Path("/distributor_versions")
public class DistributorVersionResource {

    private I18n i18n;
    private DistributorVersionCurator curator;

    @Inject
    public DistributorVersionResource(I18n i18n,
        DistributorVersionCurator curator) {
        this.i18n = i18n;
        this.curator = curator;
    }

    /**
     * @return a DistributorVersion list
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<DistributorVersion> getVersions() {
        return curator.findAll();
    }

    /**
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id,
        @Context Principal principal) {
        DistributorVersion dv = curator.findById(id);
        if (dv != null) {
            curator.delete(dv);
        }
    }

    /**
     * @return a DistributorVersion
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DistributorVersion create(DistributorVersion dv,
        @Context Principal principal) {
        DistributorVersion existing = curator.findByName(dv.getName());
        if (existing != null) {
            throw new BadRequestException(
                i18n.tr("A distributor version with name {0}" +
                        "already exists", dv.getName()));
        }
        return curator.create(dv);
    }

    /**
     * @return a DistributorVersion
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public DistributorVersion update(@PathParam("id") String id,
        DistributorVersion dv,
        @Context Principal principal) {
        DistributorVersion existing = verifyAndLookupDistributorVersion(id);
        existing.setDisplayName(dv.getDisplayName());
        existing.setCapabilities(dv.getCapabilities());
        curator.merge(existing);
        return existing;
    }

    private DistributorVersion verifyAndLookupDistributorVersion(String id) {
        DistributorVersion dv = curator.findById(id);

        if (dv == null) {
            throw new NotFoundException(i18n.tr("No such distributor version: {0}",
                id));
        }
        return dv;
    }
}

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
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.DistributorVersionDTO;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * DistributorVersionResource
 */
@Component
@Path("/distributor_versions")
@Api(value = "distributor_versions", authorizations = { @Authorization("basic") })
public class DistributorVersionResource {

    private I18n i18n;
    private DistributorVersionCurator curator;
    private ModelTranslator translator;

    @Autowired
    public DistributorVersionResource(I18n i18n, DistributorVersionCurator curator,
        ModelTranslator translator) {

        this.i18n = i18n;
        this.curator = curator;
        this.translator = translator;
    }

    /**
     * Populates the specified entity with data from the provided DTO. This method will not set the
     * ID, key, upstream consumer, content access mode list or content access mode fields.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntity(DistributorVersion entity, DistributorVersionDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the distributor version model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the distributor version dto is null");
        }

        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getCapabilities() != null) {
            if (dto.getCapabilities().isEmpty()) {
                entity.setCapabilities(Collections.emptySet());
            }
            else {
                entity.setCapabilities(dto.getCapabilities()
                    .stream()
                    .map(capability -> new DistributorVersionCapability(entity, capability.getName()))
                    .collect(Collectors.toSet()));
            }
        }

    }

    @ApiOperation(notes = "Retrieves list of Distributor Versions", value = "getVersions")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<DistributorVersionDTO> getVersions(
        @QueryParam("name_search") String nameSearch,
        @QueryParam("capability") String capability,
        @Context Principal principal) {

        List<DistributorVersion> versions;
        if (!StringUtils.isBlank(nameSearch)) {
            versions = curator.findByNameSearch(nameSearch);
        }
        else if (!StringUtils.isBlank(capability)) {
            versions = curator.findByCapability(capability);
        }
        else {
            versions = curator.findAll();
        }

        return versions.stream().map(
            this.translator.getStreamMapper(DistributorVersion.class, DistributorVersionDTO.class));
    }

    @ApiOperation(notes = "Deletes a Distributor Version", value = "delete")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
    @Path("/{id}")
    public void delete(@PathParam("id") String id,
        @Context Principal principal) {
        DistributorVersion dv = curator.findById(id);
        if (dv != null) {
            curator.delete(dv);
        }
    }

    @ApiOperation(notes = "Creates a Distributor Version", value = "create")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DistributorVersionDTO create(
        @ApiParam(name = "distributorVersion", required = true) DistributorVersionDTO dto,
        @Context Principal principal) {
        DistributorVersion existing = curator.findByName(dto.getName());
        if (existing != null) {
            throw new BadRequestException(
                i18n.tr("A distributor version with name {0} " +
                        "already exists", dto.getName()));
        }
        DistributorVersion toCreate = new DistributorVersion();
        populateEntity(toCreate, dto);
        return this.translator.translate(curator.create(toCreate), DistributorVersionDTO.class);
    }

    @ApiOperation(notes = "Updates a Distributor Version", value = "update")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public DistributorVersionDTO update(@PathParam("id") String id,
        @ApiParam(name = "distributorVersion", required = true) DistributorVersionDTO dto,
        @Context Principal principal) {
        DistributorVersion existing = verifyAndLookupDistributorVersion(id);
        existing.setDisplayName(dto.getDisplayName());
        existing.setCapabilities(dto.getCapabilities()
            .stream()
            .map(capability -> new DistributorVersionCapability(existing, capability.getName()))
            .collect(Collectors.toSet()));

        curator.merge(existing);
        return this.translator.translate(existing, DistributorVersionDTO.class);
    }

    private DistributorVersion verifyAndLookupDistributorVersion(String id) {
        DistributorVersion dv = curator.findById(id);

        if (dv == null) {
            throw new NotFoundException(i18n.tr("No such distributor version: {0}", id));
        }
        return dv;
    }
}

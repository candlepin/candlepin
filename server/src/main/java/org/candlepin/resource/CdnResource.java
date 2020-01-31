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
import org.candlepin.controller.CdnManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;

import com.google.inject.Inject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.xnap.commons.i18n.I18n;

import java.time.OffsetDateTime;
import java.util.Date;

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

/**
 * CdnResource
 */
@Path("/cdn")
@Api(value = "cdn", authorizations = { @Authorization("basic") })
public class CdnResource {

    private I18n i18n;
    private CdnCurator curator;
    private CdnManager cdnManager;
    private ModelTranslator translator;

    @Inject
    public CdnResource(I18n i18n, CdnCurator curator, CdnManager manager, ModelTranslator translator) {
        this.i18n = i18n;
        this.curator = curator;
        this.cdnManager = manager;
        this.translator = translator;
    }

    @ApiOperation(notes = "Retrieves a list of CDN's", value = "getContentDeliveryNetworks",
        response = CdnDTO.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<CdnDTO> getContentDeliveryNetworks() {
        return this.translator.translateQuery(curator.listAll(), CdnDTO.class);
    }

    @ApiOperation(notes = "Removes a CDN", value = "delete")
    @ApiResponses({ @ApiResponse(code =  400, message = ""), @ApiResponse(code =  404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
    @Path("/{label}")
    public void delete(@PathParam("label") String label,
        @Context Principal principal) {
        Cdn cdn = curator.getByLabel(label);
        if (cdn != null) {
            cdnManager.deleteCdn(cdn);
        }
    }

    @ApiOperation(notes = "Creates a CDN @return a Cdn object", value = "create")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CdnDTO create(
        @ApiParam(name = "cdn", required = true) CdnDTO cdnDTOInput,
        @Context Principal principal) {
        Cdn existing = curator.getByLabel(cdnDTOInput.getLabel());
        if (existing != null) {
            throw new BadRequestException(i18n.tr(
                "A CDN with the label {0} already exists", cdnDTOInput.getLabel()));
        }

        Cdn cndToCreate = new Cdn();
        this.populateEntity(cndToCreate, cdnDTOInput);

        if (cdnDTOInput.getLabel() != null) {
            cndToCreate.setLabel(cdnDTOInput.getLabel());
        }

        return this.translator.translate(cdnManager.createCdn(cndToCreate), CdnDTO.class);
    }

    @ApiOperation(notes = "Updates a CDN @return a Cdn object", value = "update")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{label}")
    public CdnDTO update(@PathParam("label") String label,
        @ApiParam(name = "cdn", required = true) CdnDTO cdnDTOInput,
        @Context Principal principal) {
        Cdn existing = verifyAndLookupCdn(label);
        this.populateEntity(existing, cdnDTOInput);
        cdnManager.updateCdn(existing);
        return this.translator.translate(existing, CdnDTO.class);
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     * This method will not set the ID field.
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
    private void populateEntity(Cdn entity, CdnDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the Cdn model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the Cdn dto is null");
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getUrl() != null) {
            entity.setUrl(dto.getUrl());
        }

        if (dto.getCertificate() != null) {
            CertificateDTO certDTO = dto.getCertificate();
            CdnCertificate cdnCert;

            if (certDTO.getKey() != null && certDTO.getCert() != null) {
                cdnCert = new CdnCertificate();
                cdnCert.setCert(certDTO.getCert());
                cdnCert.setKey(certDTO.getKey());

                if (certDTO.getSerial() != null) {
                    CertificateSerialDTO certSerialDTO = certDTO.getSerial();
                    CertificateSerial certSerial = new CertificateSerial();

                    OffsetDateTime expiration = certSerialDTO.getExpiration();
                    certSerial.setExpiration(expiration != null ?
                        new Date(expiration.toInstant().toEpochMilli()) : null);

                    if (certSerialDTO.getSerial() != null) {
                        certSerial.setSerial(certSerialDTO.getSerial().longValue());
                    }

                    if (certSerialDTO.getCollected() != null) {
                        certSerial.setCollected(certSerialDTO.getCollected());
                    }

                    if (certSerialDTO.getRevoked() != null) {
                        certSerial.setRevoked(certSerialDTO.getRevoked());
                    }

                    cdnCert.setSerial(certSerial);
                }
                entity.setCertificate(cdnCert);
            }
            else {
                throw new BadRequestException(i18n.tr("cdn certificate has null key or cert."));
            }
        }
    }

    private Cdn verifyAndLookupCdn(String label) {
        Cdn cdn = curator.getByLabel(label);

        if (cdn == null) {
            throw new NotFoundException(i18n.tr("No such content delivery network: {0}",
                label));
        }
        return cdn;
    }
}

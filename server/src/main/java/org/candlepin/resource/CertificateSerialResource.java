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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;



/**
 * CertificateSerialResource
 */
@Component
@Transactional
@Path("/serials")
@Api(value = "serials", authorizations = { @Authorization("basic") })
public class CertificateSerialResource {
    private CertificateSerialCurator certificateSerialCurator;
    private ModelTranslator translator;

    //@Inject
    @Autowired
    public CertificateSerialResource(CertificateSerialCurator certificateSerialCurator,
        ModelTranslator translator) {

        this.certificateSerialCurator = certificateSerialCurator;
        this.translator = translator;
    }

    @ApiOperation(notes = "Retrieves a list of Certificate Serials", value = "getCertificateSerials",
        response = CertificateSerial.class, responseContainer = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<CertificateSerialDTO> getCertificateSerials() {
        CandlepinQuery<CertificateSerial> query = this.certificateSerialCurator.listAll();
        return this.translator.translateQuery(query, CertificateSerialDTO.class);
    }

    @ApiOperation(notes = "Retrieves single Certificate Serial", value = "getCertificateSerial")
    @GET
    @Path("/{serial_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateSerialDTO getCertificateSerial(@PathParam("serial_id") Long serialId) {
        CertificateSerial serial = this.certificateSerialCurator.get(serialId);
        return this.translator.translate(serial, CertificateSerialDTO.class);
    }
}

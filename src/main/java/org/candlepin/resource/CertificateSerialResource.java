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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;

import com.google.inject.Inject;

/**
 * CertificateSerialResource
 */
@Path("/serials")
public class CertificateSerialResource {

    private CertificateSerialCurator certificateSerialCurator;

    @Inject
    public CertificateSerialResource(CertificateSerialCurator certificateSerialCurator) {
        this.certificateSerialCurator = certificateSerialCurator;
    }

    /**
     * Retrieves a list of Certificate Serials
     *
     * @return a list of CertificateSerial objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<CertificateSerial> getCertificateSerials() {
        return this.certificateSerialCurator.listAll();
    }

    /**
     * Retrieves single Certificate Serial
     * <p>
     * <pre>
     * {'serial': 'MYSERIALNUMBER'}
     * </pre>
     *
     * @return a CertificateSerial object
     * @httpcode 200
     */
    @GET
    @Path("/{serial_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CertificateSerial getCertificateSerial(@PathParam("serial_id") Long serialId) {
        return this.certificateSerialCurator.find(serialId);
    }
}

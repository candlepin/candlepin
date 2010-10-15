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
package org.fedoraproject.candlepin.resource;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;

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
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @AllowRoles(roles = {Role.SUPER_ADMIN})
    public List<CertificateSerial> getCertificateSerials() {
        return this.certificateSerialCurator.listAll();
    }
    
    @GET
    @Path("/{serial_id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @AllowRoles(roles = {Role.SUPER_ADMIN})
    public CertificateSerial getCertificateSerial(@PathParam("serial_id") Long serialId) {
        return this.certificateSerialCurator.find(serialId);
    }
}

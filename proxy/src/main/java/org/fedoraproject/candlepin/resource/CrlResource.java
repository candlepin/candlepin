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

import java.io.IOException;
import java.io.StringWriter;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.bouncycastle.openssl.PEMWriter;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.controller.CrlGenerator;

import com.google.inject.Inject;

/**
 * CrlResource
 */
@Path("/crl")
public class CrlResource {

    private CrlGenerator crlGenerator;
    
    @Inject
    public CrlResource(CrlGenerator crlGenerator) {
        this.crlGenerator = crlGenerator;
    }
    
    /**
     * @return the current CRL
     * @throws CRLException if there is issue generating the CRL
     * @throws IOException if there is a problem serializing the CRL
     */
    @GET
    @AllowRoles(roles = Role.SUPER_ADMIN)
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public String getCurrentCrl(@Context Principal principal) 
        throws CRLException, IOException {
        
        X509CRL crl = this.crlGenerator.createCRL(getDN(principal));
        
        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        
        pemWriter.writeObject(crl);
        pemWriter.close();
        
        return stringWriter.toString();
    }
    
    // TODO:  Not sure if this is the desired method of 
    //        generating the DN...
    private String getDN(Principal principal) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(principal.getOwner().getDisplayName());
        sb.append(", UID=");
        sb.append(principal.hashCode());
        return sb.toString();
    }
}

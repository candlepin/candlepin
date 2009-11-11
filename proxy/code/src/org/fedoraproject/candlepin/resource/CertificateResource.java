/**
 * Copyright (c) 2008 Red Hat, Inc.
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

import org.fedoraproject.candlepin.model.User;

import com.redhat.rhn.common.cert.Certificate;
import com.redhat.rhn.common.cert.CertificateFactory;
import com.sun.jersey.core.util.Base64;

import org.jdom.JDOMException;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * CertificateResource
 * @version $Rev$
 */
@Path("/certificate")
public class CertificateResource extends BaseResource {
    public static Certificate cert;

    /**
     * default ctor
     */
    public CertificateResource() {
        super(User.class);
    }

    /**
     * Uploads the certificate containing list of entitlements.
     * @param base64cert base64 encoded certificate.
     * @return id of certificate.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public String create(String base64cert) {
        
        try {
            System.out.println("cert: [" + base64cert + "]");
            String decoded = Base64.base64Decode(base64cert);
            System.out.println(decoded);
            cert = CertificateFactory.read(decoded);
        }
        catch (JDOMException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "uuid";
    }
    
//    @GET
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    public String get() {
//        return "Hello Certificate";
//    }
    
    public static Certificate get() {
        return cert;
    }
}

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
import java.text.ParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.SubscriptionsCertificate;
import org.fedoraproject.candlepin.model.SubscriptionsCertificateCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.jdom.JDOMException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.redhat.rhn.common.cert.SpacewalkCertificate;
import com.redhat.rhn.common.cert.CertificateFactory;


/**
 * CertificateResource - Rest interface to Subscription Certificates.
 */
@Path("/certificates")
public class CertificateResource  {
    private static String encodedCert = ""; //FIXME bad bad bad
    private SubscriptionsCertificateCurator certificateCurator;
    private SpacewalkCertificateCurator spacewalkCertificateCurator;
    private Principal principal;
    
    private static Logger log = Logger.getLogger(CertificateResource.class);
    
    private I18n i18n;
    
    @Inject
    public CertificateResource(SpacewalkCertificateCurator spacewalkCertificateCurator,
                               SubscriptionsCertificateCurator certificateCurator,
                               Principal principal,
                               I18n i18n) {
   

        this.spacewalkCertificateCurator = spacewalkCertificateCurator;
        this.certificateCurator = certificateCurator;
        this.principal = principal;
        this.i18n = i18n;
    }
    
    /**
     * Uploads the certificate containing list of entitlements.
     * @param base64cert base64 encoded certificate.
     * @return id of certificate.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public String upload(String base64cert) {
        
        try {
            if (base64cert == null || "".equals(base64cert)) {
                throw new BadRequestException(
                    i18n.tr("Empty certificate is being uploaded."));
            }
            
            String decoded = new String(Base64.decodeBase64(base64cert));
            SpacewalkCertificate cert = CertificateFactory.read(decoded);
            
            // TODO: Check for duplicate upload of the same certificate.
            
            // TODO: Check if certificate owner matches authorized owner?
            
            Owner owner = principal.getOwner();
            log.info("Uploading subscription certificate for owner: " + owner); 
            
            SubscriptionsCertificate certBlob = new SubscriptionsCertificate(decoded, 
                    owner);
            certificateCurator.create(certBlob);
           
            spacewalkCertificateCurator.parseCertificate(cert, owner);
        }
        catch (JDOMException e) {
            log.error("Exception when parsing satellite certificate.", e);
            throw new BadRequestException(
                i18n.tr("Exception when parsing satellite certificate."));
        }
        catch (IOException e) {
            log.error("Exception when reading satellite certificate.", e);
            throw new BadRequestException(
                i18n.tr("Exception when reading satellite certificate."));
        }
        catch (ParseException e) {
            log.error("Exception when parsing satellite certificate.", e);
            throw new BadRequestException(
                i18n.tr("Exception when parsing satellite certificate."));
        }
        return base64cert;
    }
    
}

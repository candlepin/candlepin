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
import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.fedoraproject.candlepin.model.CertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.User;
import org.jdom.JDOMException;

import com.google.inject.Inject;
import com.redhat.rhn.common.cert.Certificate;
import com.redhat.rhn.common.cert.CertificateFactory;
import com.redhat.rhn.common.cert.ChannelFamilyDescriptor;
import com.sun.jersey.core.util.Base64;


/**
 * CertificateResource
 */
@Path("/certificate")
public class CertificateResource extends BaseResource {
    private static String encodedCert = ""; // bad bad bad

    
    private OwnerCurator ownerCurator;
    private ProductCurator productCurator;
    private EntitlementPoolCurator entitlementPoolCurator;
    private CertificateCurator certificateCurator;

    
    @Inject
    public CertificateResource(OwnerCurator ownerCurator, 
                               ProductCurator productCurator, 
                               EntitlementPoolCurator entitlementPoolCurator,
                               CertificateCurator certificateCurator) {
        super(User.class);
        
        this.ownerCurator = ownerCurator;
        this.productCurator = productCurator;
        this.entitlementPoolCurator = entitlementPoolCurator;
        this.certificateCurator = certificateCurator;
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
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
            
            encodedCert = base64cert;
            String decoded = Base64.base64Decode(base64cert);
            Certificate cert = CertificateFactory.read(decoded);
            
            Owner owner = addOwner(cert);
            org.fedoraproject.candlepin.model.Certificate certBlob =
                new org.fedoraproject.candlepin.model.Certificate(decoded, owner);
            certificateCurator.create(certBlob);
           
            addProducts(cert, owner);
        }
        catch (JDOMException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        return "uuid";
    }
    

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public String get() {
//        byte[] s = null;
//        if (cert != null) {
//            s = Base64.encode(cert.asXmlString());
//        }
//        
//        String str = createString(s);
//        System.out.println(str);
//        return str;
        return encodedCert;
    }
    
    private void addProduct(Owner owner, String pname, long maxmem,
            Date start, Date end) {

        Product p = productCurator.lookupByName(pname);
        if (p == null) {
            p = new Product(pname, pname);
            productCurator.create(p);
        }

        EntitlementPool ep = new EntitlementPool();
        ep.setOwner(owner);
        ep.setProduct(p);
        ep.setMaxMembers(maxmem);
        ep.setStartDate(start);
        ep.setEndDate(end);
        ep.setCurrentMembers(0);
        entitlementPoolCurator.create(ep);
        
    }

    private Owner addOwner(Certificate cert) throws ParseException {
        Owner owner = ownerCurator.lookupByName(cert.getOwner());
        if (owner == null) {
            owner = new Owner(cert.getOwner());
            ownerCurator.create(owner);
        }
        return owner;
    }
    
    private void addProducts(Certificate cert, Owner owner) throws ParseException {

        // get the product the cert is for (and the channel families 
        // which have the other products you can have)
        Date issued = cert.getIssuedDate();
        Date expires = cert.getExpiresDate();
        
        addProduct(owner, cert.getProduct(),
                new Long(cert.getSlots()).longValue(),
                issued, expires);
        
        // create products for the channel families
        for (ChannelFamilyDescriptor cfd : cert.getChannelFamilies()) {
            addProduct(owner, cfd.getFamily(),
                    new Long(cfd.getQuantity()).longValue(),
                    issued, expires);
        }
        
        // create products for each of the add-on entitlements.
        if (!isEmpty(cert.getMonitoringSlots())) {
            addProduct(owner, "monitoring",
                    new Long(cert.getMonitoringSlots()).longValue(),
                    issued, expires);
        }
        
        if (!isEmpty(cert.getNonlinuxSlots())) {
            addProduct(owner, "nonlinux",
                    new Long(cert.getNonlinuxSlots()).longValue(),
                    issued, expires);
        }
        
        if (!isEmpty(cert.getProvisioningSlots())) {
            addProduct(owner, "provisioning",
                    new Long(cert.getProvisioningSlots()).longValue(),
                    issued, expires);
        }
        
        if (!isEmpty(cert.getVirtualizationSlots())) {
            addProduct(owner, "virtualization_host",
                    new Long(cert.getVirtualizationSlots()).longValue(),
                    issued, expires);           
        }
        
        if (!isEmpty(cert.getVirtualizationPlatformSlots())) {
            addProduct(owner, "virtualization_host_platform",
                    new Long(cert.getVirtualizationPlatformSlots()).longValue(),
                    issued, expires);
        }
    }
    
    private boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }
}

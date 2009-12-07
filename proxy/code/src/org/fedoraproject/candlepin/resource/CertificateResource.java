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

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;
import org.jdom.JDOMException;

import com.redhat.rhn.common.cert.Certificate;
import com.redhat.rhn.common.cert.CertificateFactory;
import com.redhat.rhn.common.cert.ChannelFamilyDescriptor;
import com.sun.jersey.core.util.Base64;


/**
 * CertificateResource
 */
@Path("/certificate")
public class CertificateResource extends BaseResource {
    public static Certificate cert;
    public static String encodedCert = ""; // bad bad bad

    private OwnerCurator ownerCurator;

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
    public String upload(String base64cert) {
        
        try {
            if (base64cert == null || "".equals(base64cert)) {
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
            
            encodedCert = base64cert;
            String decoded = Base64.base64Decode(base64cert);
//            System.out.println(decoded);
            cert = CertificateFactory.read(decoded);
            
            addProducts(cert);
        }
        catch (JDOMException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ParseException e) {
            // TODO Auto-generated catch block
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
       Product p = new Product(pname, pname);

       // TODO: does product already exist?
       // TODO: store product

        EntitlementPool ep = new EntitlementPool();
        ep.setOwner(owner);
        ep.setProduct(p);
        ep.setMaxMembers(maxmem);
        ep.setStartDate(start);
        ep.setEndDate(end);

        // TODO: store entitlement pool
    }

    private void addProducts(Certificate cert) throws ParseException {
        // look up the owner by the same name, if none found, create a new one.
//        Owner owner = (Owner) ObjectFactory.get().lookupByFieldName(
//                Owner.class, "name", cert.getOwner());
        
        // TODO: horrible temporary hack until we sort out authentication
        // Lookup all owners and use the first one. :(
        List<Owner> owners = ownerCurator.listAll();
        Owner owner = owners.get(0);
        
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

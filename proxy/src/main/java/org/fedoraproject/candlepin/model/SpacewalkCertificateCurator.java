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
package org.fedoraproject.candlepin.model;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;

import com.google.inject.Inject;
import com.redhat.rhn.common.cert.SpacewalkCertificate;
import com.redhat.rhn.common.cert.ChannelFamilyDescriptor;

/**
 * SpacewalkCertificateCurator
 */
public class SpacewalkCertificateCurator {
    
    private PoolCurator poolCurator;
    //TODO Need to go through the service for this.
    private ProductCurator productCurator;
    private AttributeCurator attributeCurator;
    private SubscriptionCurator subCurator;

    
    public static final String PRODUCT_MONITORING = "monitoring";
    public static final String PRODUCT_PROVISIONING = "provisioning";
    public static final String PRODUCT_VIRT_HOST = "virtualization_host";
    public static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    public static final String PRODUCT_VIRT_GUEST = "virt_guest";
    
    public static final String ATTRIB_ALLOWED_GUESTS = "allowed_guests";


    @Inject
    public SpacewalkCertificateCurator(PoolCurator poolCurator, 
            ProductCurator productCurator, AttributeCurator attributeCurator,
            SubscriptionCurator subCurator) {
        
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        this.attributeCurator = attributeCurator;
        this.subCurator = subCurator;
    }

    /**
     * parses the Spacewalk certificate.
     * @param cert Spacewalk certificate
     * @param owner owner of the certificate
     * @throws ParseException thrown if problem parsing.
     */
    public void parseCertificate(SpacewalkCertificate cert, Owner owner) 
        throws ParseException {

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
            addProduct(owner, PRODUCT_VIRT_HOST,
                    new Long(cert.getVirtualizationSlots()).longValue(),
                    issued, expires);           
        }
        
        if (!isEmpty(cert.getVirtualizationPlatformSlots())) {
            addProduct(owner, PRODUCT_VIRT_HOST_PLATFORM,
                    new Long(cert.getVirtualizationPlatformSlots()).longValue(),
                    issued, expires);
        }
        
        if (!isEmpty(cert.getVirtualizationSlots()) || 
                !isEmpty(cert.getVirtualizationPlatformSlots())) {
            createProductIfDoesNotExist(PRODUCT_VIRT_GUEST);
        }
    }
    
    private boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }

    private void addProduct(Owner owner, String pname, long maxmem,
            Date start, Date end) {

        Product p = createProductIfDoesNotExist(pname);

        // Assume we'll create a subscription to back these entitlement pools:
        Subscription sub = new Subscription(owner, p.getId(), maxmem, start, end, start);
        subCurator.create(sub);
        
        Pool ep = new Pool();
        ep.setOwner(owner);
        ep.setProductId(p.getId());
        ep.setQuantity(maxmem);
        ep.setStartDate(start);
        ep.setEndDate(end);
        ep.setConsumed(new Long(0));
        ep.setSubscriptionId(sub.getId());

        owner.addEntitlementPool(ep);
        poolCurator.create(ep);
    }
    
    private Product createProductIfDoesNotExist(String name) {
        Product p = productCurator.lookupByName(name);
        if (p == null) {
            // FIXME: sat cert doesn't have this info, but PRoduct
            // needs it now. Populate now so we don't NPE on
            // sat-cert imported products, but the real answer
            // is to populate with more "real" product data

            // okay, the abs is a little lame, but all of this needs to be removed shortly 
            // FIXME
            p = new Product(name, name, "server", "1.0", "ALL", 
                Math.abs(Long.valueOf(name.hashCode())), "SVC", new HashSet<Product>());

            // Representing the implicit logic in the Satellite certificate:
            if (name.equals(PRODUCT_VIRT_HOST)) {
                Attribute a = new Attribute(ATTRIB_ALLOWED_GUESTS, "5");
                attributeCurator.create(a);
                p.addAttribute(a);
            }
            else if (name.equals(PRODUCT_VIRT_HOST_PLATFORM)) {
                Attribute a = new Attribute(ATTRIB_ALLOWED_GUESTS, "-1");
                attributeCurator.create(a);
                p.addAttribute(a);
            }

            productCurator.create(p);
        }
        
        return p;
    }
}

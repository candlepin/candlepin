package org.fedoraproject.candlepin.model;

import java.text.ParseException;
import java.util.Date;

import com.google.inject.Inject;
import com.redhat.rhn.common.cert.Certificate;
import com.redhat.rhn.common.cert.ChannelFamilyDescriptor;

public class SpacewalkCertificateCurator {
    
    private EntitlementPoolCurator entitlementPoolCurator;
    private ProductCurator productCurator;
    private AttributeCurator attributeCurator;

    
    public static final String PRODUCT_MONITORING = "monitoring";
    public static final String PRODUCT_PROVISIONING = "provisioning";
    public static final String PRODUCT_VIRT_HOST = "virtualization_host";
    public static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    public static final String PRODUCT_VIRT_GUEST = "virt_guest";
    
    public static final String ATTRIB_ALLOWED_GUESTS = "allowed_guests";


    @Inject
    public SpacewalkCertificateCurator(EntitlementPoolCurator entitlementPoolCurator, 
            ProductCurator productCurator, AttributeCurator attributeCurator) {
        
        this.entitlementPoolCurator = entitlementPoolCurator;
        this.productCurator = productCurator;
        this.attributeCurator = attributeCurator;
    }

    public void parseCertificate(Certificate cert, Owner owner) throws ParseException {

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
        
        EntitlementPool ep = new EntitlementPool();
        ep.setOwner(owner);
        ep.setProduct(p);
        ep.setMaxMembers(maxmem);
        ep.setStartDate(start);
        ep.setEndDate(end);
        ep.setCurrentMembers(0);
        entitlementPoolCurator.create(ep);
    }
    
    private Product createProductIfDoesNotExist(String name) {
        Product p = productCurator.lookupByName(name);
        if (p == null) {
            p = new Product(name, name);

            // Representing the implicit logic in the Satellite certificate:
            if (name.equals(PRODUCT_VIRT_HOST)) {
                Attribute a = new Attribute(ATTRIB_ALLOWED_GUESTS, new Long(5));
                attributeCurator.create(a);
                p.addAttribute(a);
            }
            else if (name.equals(PRODUCT_VIRT_HOST_PLATFORM)) {
                Attribute a = new Attribute(ATTRIB_ALLOWED_GUESTS, new Long(-1));
                attributeCurator.create(a);
                p.addAttribute(a);
            }

            productCurator.create(p);
        }
        
        return p;
    }
}

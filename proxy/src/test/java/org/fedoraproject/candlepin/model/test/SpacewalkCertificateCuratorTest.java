package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import com.redhat.rhn.common.cert.CertificateFactory;

public class SpacewalkCertificateCuratorTest extends DatabaseTestFixture {
    
    private String certificateWithChannelFamilies;
    private Owner owner;

    @Before
    public void setUp() throws Exception {
        certificateWithChannelFamilies = 
            SpacewalkCertificateCuratorTest.readCertificate(
                    "/certs/spacewalk-with-channel-families.cert");
        
        owner = new Owner("owner");
        ownerCurator.create(owner);
    }
    
    @Test
    public void parseChannelFamily() throws Exception {
        
        Map<String, Long> channelFamilies = new HashMap<String, Long>() {{
           put("rhel-server", new Long(2000)); 
           put("rhel-cluster", new Long(4000)); 
           put("rhel-gfs", new Long(4000)); 
           put("rhel-client-workstation", new Long(1000)); 
           put("rhel-client-vt", new Long(1000)); 
           put("rhel-devsuite", new Long(4000)); 
           put("rhel-client-supplementary", new Long(2000)); 
           put("rhel-s390x-server-supplementary", new Long(1000)); 
           put("rhel-client-workstation-fastrack", new Long(1000)); 
           put("rhn-tools", new Long(6000)); 
           put("rhel-client", new Long(2000)); 
           put("rhel-server-fastrack", new Long(2000)); 
           put("rhel-server-hts", new Long(8000)); 
           put("rhel-server-vt", new Long(2000)); 
           put("rhel-server-cluster-storage", new Long(1000)); 
           put("rhel-server-cluster", new Long(1000)); 
           put("rhel-s390x-fastrack", new Long(1000)); 
           put("rhel-rhaps", new Long(4000)); 
           put("rhel-client-fastrack", new Long(2000)); 
           put("rhn-proxy", new Long(100)); 
           put("rhel-server-supplementary", new Long(2000)); 
           put("rhel-s390x-server", new Long(1000)); 
           put("rhel-server-productivity", new Long(1000)); 
           put("rhel-server-productivity-z", new Long(1000)); 
           put("rhel-server-supplementary-z", new Long(1000)); 
           put("rhel-server-vt-z", new Long(1000)); 
           put("rhel-server-z", new Long(1000)); 
           put("rhel-appstk", new Long(1000)); 
           put("jb-appplatform", new Long(1000)); 
           put("jb-middleware", new Long(1000)); 
        }};
        
        spacewalkCertificateCurator.parseCertificate(
                CertificateFactory.read(certificateWithChannelFamilies),
                owner
        );
        
        for(String channelFamily: channelFamilies.keySet()) {
            assertChannelFamilyExists(channelFamily, channelFamilies.get(channelFamily));
        }
    }
    
    private void assertChannelFamilyExists(String channelFamilyName, Long quantity) {
        Product product = productCurator.lookupByName(channelFamilyName);
        assertNotNull(product);
        EntitlementPool entitlementPool = entitlementPoolCurator.listByOwnerAndProduct(owner, 
                null, product).get(0);
        assertNotNull(entitlementPool);
        assertEquals(quantity, entitlementPool.getMaxMembers());
        assertEquals(new Long(0), entitlementPool.getCurrentMembers());
    }

    public static String readCertificate(String path) throws Exception {
        InputStream is = path.getClass().getResourceAsStream(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        return builder.toString();
    }
}

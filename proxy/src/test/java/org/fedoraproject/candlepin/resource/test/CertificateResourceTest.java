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

package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class CertificateResourceTest extends DatabaseTestFixture {
    
    private CertificateResource certResource;
    private String sampleCertXml;
    
    @Before
    public void createObjects() throws Exception {
        
        certResource = new CertificateResource(ownerCurator,
                spacewalkCertCurator, certificateCurator, i18n);
        
        InputStream is = this.getClass().getResourceAsStream(
                "/org/fedoraproject/candlepin/resource/test/spacewalk-public.cert");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line + "\n");
        }
        sampleCertXml = builder.toString();
    }
    
    @Test
    public void ownerCreated() {
        certResource.upload(TestUtil.xmlToBase64String(sampleCertXml));
        Owner owner = ownerCurator.lookupByName("Spacewalk Public Cert");
        assertNotNull(owner);
    }
    
    @Test
    public void simpleUploadCertProductsCreated() {
        certResource.upload(TestUtil.xmlToBase64String(sampleCertXml));
        List<Product> products = productCurator.listAll();
        assertEquals(6, products.size());
    }

    @Test
    public void entitlementPoolCreation() {
        String encoded = TestUtil.xmlToBase64String(sampleCertXml);
        certResource.upload(encoded);
        Owner owner = ownerCurator.lookupByName("Spacewalk Public Cert");
        List<Pool> entPools = poolCurator.listByOwner(owner);
        assertEquals(5, entPools.size());
    }

    @Test
    public void channelFamilyCreation() {
        // TODO!!!!!! Current test cert has no channel families.
    }
    
    @Test
    public void uploadCertWithPreExistingProducts() {
        // TODO
    }

}

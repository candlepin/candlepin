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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CertificateResourceTest extends DatabaseTestFixture {
    
    private CertificateResource certResource;
    private String sampleCertXml;
    
    @Before
    public void createObjects() throws Exception {
        certResource = new CertificateResource();
        
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
    public void simpleUploadCertProductsCreated() {
        certResource.upload(TestUtil.xmlToBase64String(sampleCertXml));
        // TODO: check that products got created!
    }

    
}

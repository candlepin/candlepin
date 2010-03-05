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
package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

public class CertificateHttpClientTest extends AbstractGuiceGrizzlyTest {

    private String sampleCertXml;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        
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
    public void uploadAValidCertificateShouldPass() {
        
        WebResource r = resource().path("/certificates");
        String returned = r.accept("application/json")
             .type("application/json")
             .post(String.class, TestUtil.xmlToBase64String(sampleCertXml));

        assertNotNull(returned);
        assertNotNull(ownerCurator.lookupByName("Spacewalk Public Cert"));
    }
    
    @Test
    public void uploadOfEmptyCertificateShouldFailWithBadRequest() {
        try {
            WebResource r = resource().path("/certificates");
            r.accept("application/json")
                 .type("application/json")
                 .post(String.class, "");
            fail();
        }
        catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    public void uploadOfInvalidCertificateShouldFailWithBadRequest() {
        try {
            WebResource r = resource().path("/certificates");
            r.accept("application/json")
                 .type("application/json")
                 .post(String.class,
                    TestUtil.xmlToBase64String(sampleCertXml.substring(0, 20)));
            fail();
        }
        catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }
}

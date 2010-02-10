package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.fedoraproject.candlepin.client.test.ConsumerHttpClientTest.TestServletConfig;
import org.fedoraproject.candlepin.test.TestUtil;

import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CertificateHttpClientTest extends AbstractGuiceGrizzlyTest {

    private String sampleCertXml;

    @Before
    public void setUp() throws Exception {
        TestServletConfig.servletInjector = injector;
        startServer(TestServletConfig.class);
        
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
        
        WebResource r = resource().path("/certificate");
        String returned = r.accept("application/json")
             .type("application/json")
             .post(String.class, TestUtil.xmlToBase64String(sampleCertXml));

        assertNotNull(returned);
        assertNotNull(ownerCurator.lookupByName("Spacewalk Public Cert"));
    }
    
    @Test
    public void uploadOfEmptyCertificateShouldFailWithBadRequest() {
        try {
            WebResource r = resource().path("/certificate");
            r.accept("application/json")
                 .type("application/json")
                 .post(String.class, "");
            fail();
        } catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    public void uploadOfInvalidCertificateShouldFailWithBadRequest() {
        try {
            WebResource r = resource().path("/certificate");
            r.accept("application/json")
                 .type("application/json")
                 .post(String.class, 
                         TestUtil.xmlToBase64String(sampleCertXml.substring(0, 20)));
            fail();
        } catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }
}

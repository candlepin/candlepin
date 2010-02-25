package org.fedoraproject.candlepin.auth;

import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import org.junit.Before;
import org.junit.Test;

public class SSLAuthFilterTest {
    
    private Certificate certificatePath;
    private Certificate selfSignedCertificate;
    private Certificate caCertificate;

    @Before
    public void setUp() throws Exception {
        certificatePath = CertificateFactory
            .getInstance("X.509")
            .generateCertificate(getClass().getResourceAsStream("CertChain.cer"));
        
        selfSignedCertificate = CertificateFactory
            .getInstance("X.509")
            .generateCertificate(getClass().getResourceAsStream("SelfSignedCert.cer"));
        
        caCertificate = CertificateFactory
            .getInstance("X.509")
            .generateCertificate(getClass().getResourceAsStream("CACert.pem"));
    }
    
    @Test
    public void validCertificateShouldPassAuthentication() {
        
    }
}

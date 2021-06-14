/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * some useful sources of certificate-related information:
 * http://www.herongyang.com/Cryptography/index.html (in particular:
 * http://bit.ly/a2sX1d ) http://bit.ly/aOqTAV
 *
 * Certificates:
 * - ca.crt: Root certificate used to sign the certchain.crt
 * - certchain.crt: Chained certificate signed by ca.crt
 * - selfsigned.crt: A self signed certificate seperate from the others.
 */
public class SSLCertTest {

    private X509Certificate certificatePath;
    private X509Certificate selfSignedCertificate;
    private X509Certificate caCertificate;
    private CertificateFactory certificateFactory;
    private PKIXParameters PKIXparams;

    @Before
    public void setUp() throws Exception {
        certificateFactory = CertificateFactory.getInstance("X.509");

        certificatePath = (X509Certificate) certificateFactory.generateCertificate(
            getClass().getResourceAsStream("leaf.crt"));

        selfSignedCertificate = (X509Certificate) certificateFactory.generateCertificate(
            getClass().getResourceAsStream("selfsigned.crt"));

        caCertificate = (X509Certificate) certificateFactory.generateCertificate(
            getClass().getResourceAsStream("ca.crt"));

        TrustAnchor anchor = new TrustAnchor(caCertificate, null);
        PKIXparams = new PKIXParameters(Collections.singleton(anchor));
        PKIXparams.setRevocationEnabled(false);
    }

    @SuppressWarnings("serial")
    @Test
    public void validCertificateShouldPassVerification() throws Exception {
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        CertPath cp = certificateFactory.generateCertPath(Collections.singletonList(certificatePath));

        // PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
        cpv.validate(cp, PKIXparams);

        assertEquals("CN=www.example.com, L=Halifax, ST=NS, C=CA",
            certificatePath.getSubjectDN().getName());
    }

    @SuppressWarnings("serial")
    @Test(expected = CertPathValidatorException.class)
    public void invalidCertificateShouldFailVerification() throws Exception {
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        CertPath cp = certificateFactory.generateCertPath(Collections.singletonList(selfSignedCertificate));

        //PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
        cpv.validate(cp, PKIXparams);
    }
}

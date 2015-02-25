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
package org.candlepin.test;

import org.candlepin.pki.PKIReader;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 * PKIReaderForTesting
 */
public class PKIReaderForTesting implements PKIReader {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public X509Certificate getCACert() throws IOException, CertificateException {
        InputStream caStream = PKIReaderForTesting.class.getClassLoader().getResourceAsStream("test-ca.crt");
        X509Certificate ca = (X509Certificate)
            CertificateFactory.getInstance("X.509").generateCertificate(caStream);
        return ca;
    }

    @Override
    public PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        InputStream keyStream = this.getClass().getClassLoader().getResourceAsStream("test-ca.key");

        PEMReader reader = null;
        KeyPair keyPair = null;
        try {
            reader = new PEMReader(new InputStreamReader(keyStream));
            keyPair = (KeyPair) reader.readObject();
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
        return keyPair.getPrivate();
    }

    @Override
    public Set<X509Certificate> getUpstreamCACerts() throws IOException,
        CertificateException {
        return null;
    }

}

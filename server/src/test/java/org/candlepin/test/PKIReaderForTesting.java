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

import static org.candlepin.pki.impl.BouncyCastleProviderLoader.BC_PROVIDER;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.impl.BouncyCastleProviderLoader;
import org.candlepin.pki.impl.PrivateKeyReader;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.Assert;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.inject.Inject;

/**
 * PKIReaderForTesting
 */
public class PKIReaderForTesting extends PKIReader {
    static {
        BouncyCastleProviderLoader.addProvider();
    }

    @Inject
    public PKIReaderForTesting(Configuration config, PrivateKeyReader reader)
        throws CertificateException, IOException {
        super(new MapConfiguration(), new PrivateKeyReader());
    }

    @Override
    protected void validateArguments() {
        // Don't do any validation
    }

    @Override
    protected void readConfig(Configuration config) {
        // Do nothing
    }

    @Override
    protected PrivateKey readPrivateKey(PrivateKeyReader reader) throws FileNotFoundException {
        InputStream keyStream = this.getClass().getClassLoader().getResourceAsStream("test-ca.key");

        KeyPair keyPair = null;
        try (
            PEMParser parser = new PEMParser(new InputStreamReader(keyStream));
        ) {
            keyPair = new JcaPEMKeyConverter()
                .setProvider(BC_PROVIDER)
                .getKeyPair((PEMKeyPair) parser.readObject());
        }
        catch (IOException e) {
            Assert.fail("Could not load private key");
        }

        return keyPair.getPrivate();
    }

    @Override
    protected X509Certificate loadCACertificate(String path) {
        X509Certificate ca = null;
        try (
            InputStream caStream =
                PKIReaderForTesting.class.getClassLoader().getResourceAsStream("test-ca.crt");
        ) {
            ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(caStream);
        }
        catch (IOException | CertificateException e) {
            Assert.fail("Could not load CA certificate");
        }
        return ca;
    }

    @Override
    protected Set<X509Certificate> loadUpstreamCACertificates(String path) {
        return null;
    }

    @Override
    public Set<X509Certificate> getUpstreamCACerts() {
        return null;
    }
}

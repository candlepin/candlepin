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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.pki.BouncyCastlePrivateKeyReader;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.impl.BouncyCastleProviderLoader;

import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Set;

import javax.inject.Inject;

/**
 * CertificateReaderForTesting
 */
public class CertificateReaderForTesting extends CertificateReader {
    static {
        BouncyCastleProviderLoader.addProvider();
    }

    @Inject
    public CertificateReaderForTesting(Configuration config, PrivateKeyReader reader)
        throws CertificateException, IOException {
        super(new MapConfiguration(), new BouncyCastlePrivateKeyReader());
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
    protected RSAPrivateKey readPrivateKey(PrivateKeyReader reader) throws IOException {
        InputStream keyStream = this.getClass().getClassLoader().getResourceAsStream(getKeyFileName());
        return reader.read(keyStream, null);
    }

    @Override
    protected X509Certificate loadCACertificate(String path) {
        X509Certificate ca = null;
        try (
            InputStream caStream = this.getClass().getClassLoader().getResourceAsStream(getCAFileName());
        ) {
            ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(caStream);
        }
        catch (IOException | CertificateException e) {
            Assert.fail("Could not load CA certificate");
        }
        return ca;
    }

    public String getCAFileName() {
        return "test-ca.crt";
    }

    public String getKeyFileName() {
        return "test-ca.key";
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

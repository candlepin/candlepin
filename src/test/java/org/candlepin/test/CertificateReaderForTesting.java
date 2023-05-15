/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.impl.JSSPrivateKeyReader;
import org.candlepin.pki.impl.JSSProviderLoader;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.inject.Inject;


public class CertificateReaderForTesting extends CertificateReader {
    static {
        JSSProviderLoader.initialize();
    }

    @Inject
    public CertificateReaderForTesting()
        throws CertificateException, IOException {
        super(new DevConfig(), new JSSPrivateKeyReader());
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
    protected PrivateKey readPrivateKey(PrivateKeyReader reader) throws IOException {
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
            Assertions.fail("Could not load CA certificate");
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

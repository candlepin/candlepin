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
package org.candlepin.pki;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashSet;
import java.util.Set;

/**
 * A generic mechanism for reading CA certificates from an underlying datastore.
 */
public class CertificateReader {
    private CertificateFactory certFactory;
    private String caCertPath;
    private String upstreamCaCertPath;
    private String caKeyPath;
    private String caKeyPassword;
    private final X509Certificate x509Certificate;
    private final Set<X509Certificate> upstreamX509Certificates;
    private final RSAPrivateKey privateKey;

    @Inject
    public CertificateReader(Configuration config, PrivateKeyReader reader)
        throws CertificateException, IOException {
        certFactory = CertificateFactory.getInstance("X.509");

        readConfig(config);
        validateArguments();

        this.x509Certificate = loadCACertificate(this.caCertPath);
        this.upstreamX509Certificates = loadUpstreamCACertificates(upstreamCaCertPath);
        this.privateKey = readPrivateKey(reader);
    }

    protected void readConfig(Configuration config) {
        this.caCertPath = config.getString(ConfigProperties.CA_CERT);
        this.upstreamCaCertPath = config.getString(ConfigProperties.CA_CERT_UPSTREAM);
        this.caKeyPath = config.getString(ConfigProperties.CA_KEY);
        this.caKeyPassword = config.getString(ConfigProperties.CA_KEY_PASSWORD, null);
    }

    protected void validateArguments() {
        if (this.caCertPath == null) {
            throw new IllegalArgumentException("caCertPath cannot be null. Unable to load CA Certificate");
        }
        if (this.caKeyPath == null) {
            throw new IllegalArgumentException("caKeyPath cannot be null. Unable to load PrivateKey");
        }
    }

    protected RSAPrivateKey readPrivateKey(PrivateKeyReader reader) throws IOException {
        return reader.read(caKeyPath, caKeyPassword);
    }
    /**
     * Supplies the CA's {@link X509Certificate}.
     *
     * @return a new Cert
     * @throws IOException if a file can't be read or is not found
     * @throws CertificateException  if there is an error from the underlying cert factory
     */
    public X509Certificate getCACert() throws IOException, CertificateException {
        return this.x509Certificate;
    }

    public Set<X509Certificate> getUpstreamCACerts()  throws IOException, CertificateException {
        return this.upstreamX509Certificates;
    }

    /**
     * Supplies the CA's {@link PrivateKey}.
     *
     * @return a new PrivateKey
     * @throws IOException if a file can't be read or is not found
     * @throws GeneralSecurityException if something violated policy
     */
    public RSAPrivateKey getCaKey() throws IOException, GeneralSecurityException {
        return this.privateKey;
    }

    protected X509Certificate loadCACertificate(String path) {
        try (
            InputStream inStream = new FileInputStream(path);
        ) {
            return (X509Certificate) this.certFactory.generateCertificate(inStream);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Set<X509Certificate> loadUpstreamCACertificates(String path) {
        Set<X509Certificate> result = new HashSet<>();
        File dir = new File(path);
        if (!dir.exists()) {
            return result;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            throw new RuntimeException("Could not read files in " + path);
        }

        for (File file : files) {
            try (
                InputStream inStream = new FileInputStream(file.getAbsolutePath());
            ) {

                X509Certificate cert = (X509Certificate) this.certFactory.generateCertificate(inStream);
                result.add(cert);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

}

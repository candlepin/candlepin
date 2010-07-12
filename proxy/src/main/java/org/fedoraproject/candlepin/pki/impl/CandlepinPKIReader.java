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
package org.fedoraproject.candlepin.pki.impl;

import java.io.FileInputStream;
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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

/**
 * The default {@link PKIReader} for Candlepin.  This reads the file paths for
 * the CA certificate and CA private key, as well as an optional password, from
 * the config system.  These values are customizable via /etc/candlepin/candlepin.conf.
 */
public class CandlepinPKIReader implements PKIReader, PasswordFinder {

    private CertificateFactory certFactory;
    private String caCertPath;
    private String caKeyPath;
    private String caKeyPassword;
    private final X509Certificate x509Certificate;
    private final PrivateKey privateKey;
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Inject
    public CandlepinPKIReader(Config config) throws CertificateException {
        this.certFactory = CertificateFactory.getInstance("X.509");
        this.caCertPath = config.getString(ConfigProperties.CA_CERT);
        this.caKeyPath = config.getString(ConfigProperties.CA_KEY);
        Util.assertNotNull(this.caCertPath,
            "caCertPath cannot be null. Unable to load CA Certificate");
        Util.assertNotNull(this.caKeyPath,
            "caKeyPath cannot be null. Unable to load PrivateKey");
        this.caKeyPassword = config.getString(ConfigProperties.CA_KEY_PASSWORD);
        this.x509Certificate = loadCACertificate();
        this.privateKey = loadPrivateKey();
    }

    /**
     * @return
     */
    private X509Certificate loadCACertificate() {
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(this.caCertPath);
            X509Certificate cert = (X509Certificate) this.certFactory
                .generateCertificate(inStream);
            inStream.close();
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
            }
            catch (IOException e) {
                // ignore. there's nothing we can do.
            }
        }
    }

    /**
     * @return
     */
    private PrivateKey loadPrivateKey() {
        try {
            InputStreamReader inStream = new InputStreamReader(
                new FileInputStream(this.caKeyPath));
            PEMReader reader = null;

            try {
                if (this.caKeyPassword != null) {
                    reader = new PEMReader(inStream, this);
                }
                else {
                    reader = new PEMReader(inStream);
                }

                KeyPair caKeyPair = (KeyPair) reader.readObject();
                if (caKeyPair == null) {
                    throw new GeneralSecurityException(
                        "Reading CA private key failed");
                }
                return caKeyPair.getPrivate();
            }
            finally {
                reader.close();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public X509Certificate getCACert() throws IOException, CertificateException {
        return this.x509Certificate;
    }

    /**
     * {@inheritDoc}
     *
     * Reads the {@link KeyPair} from the CA's private key file specified in the
     * candlepin config.
     *
     * @return private key for the cacert
     */
    @Override
    public PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        return this.privateKey;
    }

    @Override
    public char[] getPassword() {
        // just grab the key password that was pulled from the config
        return caKeyPassword.toCharArray();
    }

}

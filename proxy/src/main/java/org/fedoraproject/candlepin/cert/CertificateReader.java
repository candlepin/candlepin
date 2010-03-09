/**
 * Copyright (c) 2010 Red Hat, Inc.
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
package org.fedoraproject.candlepin.cert;

import com.google.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;

/**
 * Responsible for reading SSL certificates from the file system.
 */
public class CertificateReader {

    private CertificateFactory certFactory;
    private Config config;

    @Inject
    public CertificateReader(Config config) throws CertificateException {
        this.config = config;
        this.certFactory = CertificateFactory.getInstance("X.509");
    }

    /**
     * Reads the CA's {@link X509Certificate} from the file system. This file is
     * specified in the candlepin config.
     * 
     * @return a new Cert
     * @throws IOException if a file can't be read or is not found
     * @throws CertificateException  if there is an error from the underlying cert factory
     */
    public X509Certificate getCACert() throws IOException, CertificateException {
        String caCertFile = config.getString(ConfigProperties.CA_CERT);

        if (caCertFile != null) {
            InputStream inStream = new FileInputStream(caCertFile);
            X509Certificate cert = (X509Certificate) this.certFactory
                .generateCertificate(inStream);
            inStream.close();

            return cert;
        }

        return null;
    }

    /**
     * Reads the {@link PrivateKey} from the file system. This file is specified
     * in the candlepin config.
     * 
     * @return a new PrivateKey
     * @throws GeneralSecurityException if something violated policy
     */
    public PrivateKey getCaKey() throws GeneralSecurityException {
        KeyPair keyPair = getKeyPair();

        if (keyPair != null) {
            return keyPair.getPrivate();
        }

        return null;
    }

    /**
     * Reads the {@link KeyPair} from the CA's private key file specified in the
     * candlepin config.
     * 
     * @return
     */
    private KeyPair getKeyPair() throws GeneralSecurityException {
        String caKeyFile = config.getString(ConfigProperties.CA_KEY);

        if (caKeyFile != null) {
            try {
                InputStreamReader inStream = new InputStreamReader(
                    new FileInputStream(caKeyFile));

                PEMReader reader = new PEMReader(inStream,
                    new ConfigPasswordFinder());
                KeyPair keyPair = (KeyPair) reader.readObject();

                if (keyPair == null) {
                    throw new GeneralSecurityException(
                        "Reading CA private key failed");
                }

                return keyPair;
            }
            catch (IOException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Custom {@link PasswordFinder} that reads the candlepin config file for a
     * password in plain text.
     */
    private class ConfigPasswordFinder implements PasswordFinder {

        private char[] caKeyPassword;

        private ConfigPasswordFinder() {
            String password = config
                .getString(ConfigProperties.CA_KEY_PASSWORD);

            if (password != null) {
                caKeyPassword = password.toCharArray();
            }
        }

        @Override
        public char[] getPassword() {
            return this.caKeyPassword;
        }
    }
}

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

/**
 *  Responsible for reading SSL certificates from the file system.
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
     * Reads the CA's {@link X509Certificate} from the file system.  This file
     * is specified in the candlepin config.
     *
     * @return
     * @throws IOException
     * @throws CertificateException
     */
    public X509Certificate getCACert() throws IOException, CertificateException {
        String caCertFile = config.getString("candlepin.ca_cert");

        if (caCertFile != null) {
            InputStream inStream = new FileInputStream(caCertFile);
            X509Certificate cert = (X509Certificate) 
                    this.certFactory.generateCertificate(inStream);
            inStream.close();

            return cert;
        }

        return null;
    }

    /**
     * Reads the {@link PrivateKey} from the file system.  This file is specified
     * in the candlepin config.
     *
     * @return
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public PrivateKey getCaKey() throws GeneralSecurityException {
        KeyPair keyPair = getKeyPair();

        if(keyPair != null) {
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
        String caKeyFile = config.getString("candlepin.ca_key");

        if (caKeyFile != null) {
            try {
                InputStreamReader inStream = new InputStreamReader(
                        new FileInputStream(caKeyFile));

                PEMReader reader = new PEMReader(inStream, new ConfigPasswordFinder());
                KeyPair keyPair = (KeyPair)reader.readObject();

                if(keyPair == null) {
                    throw new GeneralSecurityException("Reading CA private key failed");
                }

                return keyPair;
            } catch (IOException e) {
                return null;
            }
        }

        return null;
    }

    /**
     *  Custom {@link PasswordFinder} that reads the candlepin config file
     * for a password in plain text.
     */
    private class ConfigPasswordFinder implements PasswordFinder {

        private char[] caKeyPassword;

        private ConfigPasswordFinder() {
            String password = config.getString("candlepin.ca_key_password");

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

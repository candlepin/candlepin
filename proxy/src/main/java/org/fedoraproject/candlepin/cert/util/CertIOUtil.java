package org.fedoraproject.candlepin.cert.util;

import com.google.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.fedoraproject.candlepin.config.Config;

public class CertIOUtil {

    private Config config;

    @Inject
    public CertIOUtil(Config config) {
        this.config = config;
    }

    public X509Certificate getCACert() throws IOException, GeneralSecurityException {
        String caCertFile = config.getString("org.candlepin.ca_cert");

        if (caCertFile != null) {
            InputStream inStream = new FileInputStream(caCertFile);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            inStream.close();

            return cert;
        }

        return null;
    }
    
    public PrivateKey getCaKey() throws IOException, GeneralSecurityException {
        String caKeyFile = config.getString("org.candlepin.ca_key");
        String caKeyPasswordFile = config.getString("org.candlepin.ca_key_password");

        if (caKeyFile != null && caKeyPasswordFile != null) {
            final char[] password = caKeyPasswordFile.toCharArray();
            InputStreamReader inStream = new InputStreamReader(new FileInputStream(caKeyFile));
            // PEMreader requires a password finder to decrypt the password
            PasswordFinder pfinder = new PasswordFinder() {public char[] getPassword() { return password; }};
            PEMReader reader = new PEMReader(inStream, pfinder);

            KeyPair keyPair = (KeyPair) reader.readObject();
            reader.close();

            if(keyPair == null) {
                throw new GeneralSecurityException("Reading CA private key failed");
            }

            return keyPair.getPrivate();
        }

        return null;
    }
}

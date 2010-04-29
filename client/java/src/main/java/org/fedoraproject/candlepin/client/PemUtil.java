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
package org.fedoraproject.candlepin.client;

import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMReader;

/**
 * PemUtility
 */
public class PemUtil {

    private PemUtil() {

    }

    public static KeyStore pemToKeystore(String certificateFile,
        String privateKeyFile, String password) {
        try {
            X509Certificate cert = readCert(certificateFile);
            Certificate[] certs = { cert };

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("certificate", cert);
            ks.setKeyEntry("privateKey", readPrivateKey(privateKeyFile), password
                .toCharArray(), certs);
            return ks;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String extractUUID(String certificateFile) {
        X509Certificate cert = readCert(certificateFile);
        String name = cert.getSubjectDN().getName();
        int location = name.indexOf("UID=");
        if (location > 0) {
            name = name.substring(location + 4);
            location = name.indexOf(",");
            name = name.substring(0, location);
        }
        return name;
    }
    
    public static PrivateKey readPrivateKey(String filename) {
        try {
            PEMReader reader = new PEMReader(new FileReader(filename));
            KeyPair kPair = (KeyPair) reader.readObject();
            return kPair.getPrivate();
        }
        catch (Exception e) {
            throw new RuntimeException(e);            
        }
    }
    
    public static X509Certificate readCert(String certificateFile) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf
                .generateCertificate(new FileInputStream(certificateFile));        
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }

}

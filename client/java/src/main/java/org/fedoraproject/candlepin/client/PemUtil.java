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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.extension.X509ExtensionUtil;

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
            ks.setKeyEntry("privateKey", readPrivateKey(privateKeyFile),
                password.toCharArray(), certs);
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
            CertificateFactory cf = CertificateFactory
                .getInstance(Constants.X509);
            X509Certificate cert = (X509Certificate) cf
                .generateCertificate(new FileInputStream(certificateFile));
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate createCert(String certData) {
        try {
            CertificateFactory cf = CertificateFactory
                .getInstance(Constants.X509);
            X509Certificate cert = (X509Certificate) cf
                .generateCertificate(new ByteArrayInputStream(certData
                    .getBytes()));
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey createPrivateKey(String keyData) {
        try {
            PEMReader reader = new PEMReader(new StringReader(keyData));
            KeyPair kPair = (KeyPair) reader.readObject();
            return kPair.getPrivate();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getPemEncoded(X509Certificate cert)
        throws GeneralSecurityException, IOException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter oswriter = new OutputStreamWriter(
            byteArrayOutputStream);
        PEMWriter w = new PEMWriter(oswriter);
        w.writeObject(cert);
        w.close();
        return new String(byteArrayOutputStream.toByteArray());
    }

    public static String getPemEncoded(Key key) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter oswriter = new OutputStreamWriter(
            byteArrayOutputStream);
        PEMWriter writer = new PEMWriter(oswriter);
        writer.writeObject(key);
        writer.close();
        return new String(byteArrayOutputStream.toByteArray());
    }

    public static String getExtensionValue(X509Certificate cert, String oid,
        String defaultValue) {
        byte[] value = cert.getExtensionValue(oid);

        if (value != null) {
            try {
                return X509ExtensionUtil.fromExtensionValue(value).toString();
            }
            catch (IOException e) {
                throw new ClientException(e);
            }
        }
        else {
            return defaultValue;
        }
    }

    public static Date getExtensionDate(X509Certificate cert, String oid,
        Date defaultValue) {
        byte[] value = cert.getExtensionValue(oid);
        if (value != null) {
            try {
                String dateString = X509ExtensionUtil.fromExtensionValue(value)
                    .toString();
                SimpleDateFormat fmt = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.s");
                return fmt.parse(dateString);
            }
            catch (IOException e) {
                throw new ClientException(e);
            }
            catch (ParseException e) {
                throw new ClientException(e);
            }
        }
        else {
            return defaultValue;
        }
    }

}

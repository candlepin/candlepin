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
package org.fedoraproject.candlepin.pki;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

/**
 * PKIUtility
 */
public class PKIUtility {
    
    // TODO : configurable?
    private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGO = "SHA1WITHRSA";
    private PKIReader reader;
    
    public PKIUtility(PKIReader reader) { 
        Security.addProvider(new BouncyCastleProvider());
        this.reader = reader;
    }
 
    public X509Certificate createX509Certificate(
        String dn,
        List<X509ExtensionWrapper> extensions, 
        Date startDate,
        Date endDate,
        KeyPair clientKeyPair, 
        Long serialNumber)
        throws GeneralSecurityException, IOException {

        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        X509Certificate caCert = reader.getCACert();
        // set cert fields
        certGen.setSerialNumber(new BigInteger(serialNumber.toString()));
        certGen.setIssuerDN(caCert.getSubjectX500Principal());
        certGen.setNotBefore(startDate);
        certGen.setNotAfter(endDate);

        X509Principal subjectPrincipal = new X509Principal(dn);
        certGen.setSubjectDN(subjectPrincipal);
        certGen.setPublicKey(clientKeyPair.getPublic());
        certGen.setSignatureAlgorithm(SIGNATURE_ALGO);

        // set key usage - required for proper x509 function
        KeyUsage keyUsage = new KeyUsage(
            KeyUsage.digitalSignature | 
            KeyUsage.keyEncipherment | 
            KeyUsage.dataEncipherment);

        // add SSL extensions - required for proper x509 function
        NetscapeCertType certType = new NetscapeCertType(
            NetscapeCertType.sslClient | 
            NetscapeCertType.smime);
        
        certGen.addExtension(MiscObjectIdentifiers.netscapeCertType.toString(),
                false, certType);
        certGen.addExtension(X509Extensions.KeyUsage.toString(), false, keyUsage);
        
        certGen.addExtension(X509Extensions.AuthorityKeyIdentifier, false,
            new AuthorityKeyIdentifierStructure(caCert));
        certGen.addExtension(X509Extensions.SubjectKeyIdentifier, false,
            new SubjectKeyIdentifierStructure(clientKeyPair.getPublic()));

        if (extensions != null) {
            for (X509ExtensionWrapper wrapper : extensions) {
                certGen.addExtension(wrapper.getOid(), wrapper.isCritical(),
                        wrapper.getAsn1Encodable());
            }
        }

        // Generate the certificate
        return certGen.generate(reader.getCaKey());
    }
    
    public KeyPair decodeKeys(byte[] privKeyBits, byte[] pubKeyBits)
        throws InvalidKeySpecException, NoSuchAlgorithmException {

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        // build the private key
        PrivateKey privKey = keyFactory
            .generatePrivate(new PKCS8EncodedKeySpec(privKeyBits));
        // build the public key
        PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(
            pubKeyBits));
        // make them a key pair
        return new KeyPair(pubKey, privKey);
    }

    public KeyPair generateNewKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }
    
    /**
     * Take an X509Certificate object and return a byte[] of the certificate,
     * PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws GeneralSecurityException if there is a security issue
     * @throws IOException if there is i/o problem
     */
    public byte[] getPemEncoded(X509Certificate cert) throws 
        GeneralSecurityException, IOException {
        
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter oswriter = new OutputStreamWriter(byteArrayOutputStream);
        PEMWriter w =  new PEMWriter(oswriter);
        w.writeObject(cert);
        w.close();
        byte[] pemEncoded = byteArrayOutputStream.toByteArray();
        return pemEncoded;
    }
        
    public byte[] getPemEncoded(Key key) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStreamWriter oswriter = new OutputStreamWriter(byteArrayOutputStream);
        PEMWriter writer = new PEMWriter(oswriter);
        writer.writeObject(key);
        writer.close();
        return byteArrayOutputStream.toByteArray();
    }
}

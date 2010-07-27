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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Set;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
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
    
    public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    public static final String BEGIN_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    public static final String END_KEY = "-----END RSA PRIVATE KEY-----";
    
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
        Set<X509ExtensionWrapper> extensions, 
        Date startDate,
        Date endDate,
        KeyPair clientKeyPair, 
        BigInteger serialNumber,
        String alternateName)
        throws GeneralSecurityException, IOException {

        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        X509Certificate caCert = reader.getCACert();
        // set cert fields
        certGen.setSerialNumber(serialNumber);
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
        certGen.addExtension(X509Extensions.ExtendedKeyUsage, false, 
            new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        
        // Add an alternate name if provided
        if (alternateName != null) {
            GeneralName name = new GeneralName(GeneralName.directoryName,
                "CN=" + alternateName);
            certGen.addExtension(X509Extensions.SubjectAlternativeName, false,
                new GeneralNames(name));
        }

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
    
    public static X509Certificate createCert(byte[] certData) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf
            .generateCertificate(new ByteArrayInputStream(certData));
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public byte[] getSHA256WithRSAHash(InputStream input) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(reader.getCaKey());
            
            updateSignature(input, signature);
            return signature.sign();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean verifySHA256WithRSAHash(
            InputStream input, byte[] signedHash, Certificate certificate) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(certificate);

            updateSignature(input, signature);
            return signature.verify(signedHash);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateSignature(InputStream input, Signature signature)
        throws IOException, SignatureException {
        byte[] dataBytes = new byte[4096];
        int nread = 0; 
        while ((nread = input.read(dataBytes)) != -1) {
            signature.update(dataBytes, 0, nread);
        }
    }
}

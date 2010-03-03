package org.fedoraproject.candlepin.cert.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V3CertificateGenerator;

public class BouncyCastlePKIUtil {
    // TODO : configurable
    private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGO = "SHA1WITHRSA";
    
    /**
     * 
     * @param cert
     *      entitlement cert with extensions already in it
     * @param clientKeyPair
     *      key pair for signing the cert
     * @param serialNumber
     *      serial number for the cert
     * @return 
     *     an X509 certificate ready for consumption
     * @throws GeneralSecurityException
     */
    public static X509Certificate createX509Certificate(
            List<X509ExtensionWrapper> extensions,
            Date startDate,
            Date endDate,
            KeyPair clientKeyPair,
            X509Certificate caCert,
            PrivateKey caPrivateKey,
            BigInteger serialNumber) throws GeneralSecurityException, IOException {

        X500Principal serverDNName = caCert.getSubjectX500Principal();
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        // set cert fields
        certGen.setSerialNumber(serialNumber);
        // TODO get CA cert
        certGen.setIssuerDN(caCert.getSubjectX500Principal());
        certGen.setNotBefore(startDate);
        certGen.setNotAfter(endDate);
        certGen.setSubjectDN(serverDNName); // note: same as issuer
        certGen.setPublicKey(clientKeyPair.getPublic());
        certGen.setSignatureAlgorithm(SIGNATURE_ALGO);

        // set key usage
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature
                | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);
        // add SSL extensions
        certGen.addExtension(MiscObjectIdentifiers.netscapeCertType.toString(), false, (ASN1Encodable)new NetscapeCertType(NetscapeCertType.sslServer | NetscapeCertType.smime));
        certGen.addExtension(X509Extensions.KeyUsage.toString(), false,
                (DEREncodable) keyUsage);
        for (X509ExtensionWrapper wrapper : extensions) {
            certGen.addExtension(
                    wrapper.getOid(), 
                    wrapper.isCritical(),
                    (DEREncodable) wrapper.getAsn1Encodable());
        }

        // Generate the certificate
        X509Certificate x509cert = certGen.generate(caPrivateKey);
        return x509cert;
    }
    
    /**
     * Read the byte streams from the DB to get the public & private keys
     * 
     * @param privKeyBits
     *            DER encoded private key byte array
     * @param pubKeyBits
     *            DER encoded public key byte array
     * @return 
     *    new KeyPair object with the public & private keys from the db
     * @throws Exception
     */
    public static KeyPair decodeKeys(byte[] privKeyBits, byte[] pubKeyBits)
            throws InvalidKeySpecException, NoSuchAlgorithmException {

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        // build the private key
        PrivateKey privKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privKeyBits));
        // build the public key
        PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBits));
        // make them a key pair
        KeyPair keyPair = new KeyPair(pubKey, privKey);
        return keyPair;
    }
    
    /**
     * create a new key pair
     * @return KeyPair
     * @throws NoSuchAlgorithmException
     */
    public static KeyPair generateNewKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }
}

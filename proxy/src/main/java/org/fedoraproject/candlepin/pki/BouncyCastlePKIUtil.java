package org.fedoraproject.candlepin.pki;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
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

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V3CertificateGenerator;

/**
 * Utility class for dealing with bouncy-castle specific 
 * implementations of functionality, like generating certs
 * and reading keys & certs from the db
 * @author jomara
 */
public final class BouncyCastlePKIUtil {
	private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGO = "SHA1WITHRSA";
    
    static { 
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }
    
    private BouncyCastlePKIUtil() { }
    
	/**
	 * 
	 * @param cert
	 * 		entitlement cert with extensions already in it
	 * @param clientKeyPair
	 * 		key pair for signing the cert
	 * @param serialNumber
	 * 		serial number for the cert
	 * @return 
	 *     an X509 certificate ready for consumption
	 * @throws GeneralSecurityException
	 */
	public static X509Certificate createX509Certificate(
			List<X509ExtensionWrapper> extensions, 
			Date startDate,
			Date endDate,
			KeyPair clientKeyPair,
			String serverDN,
			BigInteger serialNumber) throws GeneralSecurityException, IOException {

		X500Principal serverDNName = new X500Principal(serverDN);
		X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

		// set cert fields
		certGen.setSerialNumber(serialNumber);
        X509Certificate caCert = IOUtil.getCACert();
        certGen.setIssuerDN(caCert.getSubjectX500Principal());
		certGen.setNotBefore(startDate);
	    certGen.setNotAfter(endDate);
		certGen.setSubjectDN(serverDNName); // note: same as issuer
		certGen.setPublicKey(clientKeyPair.getPublic());
		certGen.setSignatureAlgorithm(SIGNATURE_ALGO);

		// set key usage. this is important for the cert to work with external applications as expected
		KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature
				| KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);

		certGen.addExtension(X509Extensions.KeyUsage.toString(), false,
				(DEREncodable) keyUsage);
		for (X509ExtensionWrapper wrapper : extensions) {
			certGen.addExtension(
					wrapper.getOid(), 
					wrapper.isCritical(),
					(DEREncodable) wrapper.getAsn1Encodable());
		}

		return certGen.generate(IOUtil.getCaKey());
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
		return new KeyPair(pubKey, privKey);
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

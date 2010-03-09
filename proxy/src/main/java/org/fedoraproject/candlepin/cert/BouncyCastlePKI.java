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
package org.fedoraproject.candlepin.cert;

import com.google.inject.Inject;
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

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

/**
 * 
 * BouncyCastlePKI
 */
public class BouncyCastlePKI {

    // TODO : configurable?
    private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGO = "SHA1WITHRSA";

    private X509Certificate caCert;
    private PrivateKey caPrivateKey;

    @Inject
    public BouncyCastlePKI(CertificateReader certReader) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        this.caCert = certReader.getCACert();
        this.caPrivateKey = certReader.getCaKey();
    }

    public X509Certificate createX509Certificate(String commonName,
        List<X509ExtensionWrapper> extensions, Date startDate, Date endDate,
        BigInteger serialNumber) throws GeneralSecurityException, IOException {

        return createX509Certificate(commonName, extensions, startDate,
            endDate, generateNewKeyPair(), serialNumber);
    }

    /**
     * 
     * @param commonName
     *            human readable name of the cert
     * @param extensions
     *            should be none for now
     * @param startDate
     *            when the cert starts being valid
     * @param endDate
     *            after this time, the cert is no longer valid
     * @param clientKeyPair
     * @param serialNumber
     *            a uniue number for the cert. Random
     * @return the generated cert
     * @throws GeneralSecurityException
     *             if the underlying libraries detected a violation of the
     *             securtity constraints
     * @throws IOException
     *             If the ca cert file is unreadable or missing
     */
    public X509Certificate createX509Certificate(String commonName,
        List<X509ExtensionWrapper> extensions, Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber)
        throws GeneralSecurityException, IOException {

        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        // set cert fields
        certGen.setSerialNumber(serialNumber);
        certGen.setIssuerDN(caCert.getSubjectX500Principal());
        certGen.setNotBefore(startDate);
        certGen.setNotAfter(endDate);

        X509Principal subjectPrincipal = new X509Principal("CN=" + commonName);
        certGen.setSubjectDN(subjectPrincipal);
        certGen.setPublicKey(clientKeyPair.getPublic());
        certGen.setSignatureAlgorithm(SIGNATURE_ALGO);

        // set key usage
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature |
            KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);

        // add SSL extensions
        NetscapeCertType certType = new NetscapeCertType(
            NetscapeCertType.sslServer | NetscapeCertType.smime);
        certGen.addExtension(MiscObjectIdentifiers.netscapeCertType.toString(),
            false, certType);
        certGen.addExtension(X509Extensions.KeyUsage.toString(), false,
            keyUsage);

        if (extensions != null) {
            for (X509ExtensionWrapper wrapper : extensions) {
                certGen.addExtension(wrapper.getOid(), wrapper.isCritical(),
                    wrapper.getAsn1Encodable());
            }
        }

        // Generate the certificate
        return certGen.generate(this.caPrivateKey);
    }

    /**
     * Read the byte streams to get the public & private keys
     * 
     * @param privKeyBits
     *            DER encoded private key byte array
     * @param pubKeyBits
     *            DER encoded public key byte array
     * @return new KeyPair object with the public & private keys from the db
     * @throws InvalidKeySpecException if the key spec is not formated correctly
     * @throws NoSuchAlgorithmException  If the algorithm specificed cannot be found
     */
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
        KeyPair keyPair = new KeyPair(pubKey, privKey);
        return keyPair;
    }

    /**
     * create a new key pair
     * 
     * @return KeyPair
     * @throws NoSuchAlgorithmException
     */
    private KeyPair generateNewKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }
}

/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.ProviderBasedPKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.Util;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.ASN1Value;
import org.mozilla.jss.asn1.INTEGER;
import org.mozilla.jss.asn1.InvalidBERException;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.asn1.SEQUENCE;
import org.mozilla.jss.asn1.UTF8String;
import org.mozilla.jss.netscape.security.extensions.ExtendedKeyUsageExtension;
import org.mozilla.jss.netscape.security.extensions.NSCertTypeExtension;
import org.mozilla.jss.netscape.security.util.BitArray;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.ObjectIdentifier;
import org.mozilla.jss.netscape.security.x509.AlgorithmId;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.BasicConstraintsExtension;
import org.mozilla.jss.netscape.security.x509.CRLExtensions;
import org.mozilla.jss.netscape.security.x509.CRLNumberExtension;
import org.mozilla.jss.netscape.security.x509.CRLReasonExtension;
import org.mozilla.jss.netscape.security.x509.CertificateAlgorithmId;
import org.mozilla.jss.netscape.security.x509.CertificateExtensions;
import org.mozilla.jss.netscape.security.x509.CertificateIssuerName;
import org.mozilla.jss.netscape.security.x509.CertificateSerialNumber;
import org.mozilla.jss.netscape.security.x509.CertificateSubjectName;
import org.mozilla.jss.netscape.security.x509.CertificateValidity;
import org.mozilla.jss.netscape.security.x509.CertificateVersion;
import org.mozilla.jss.netscape.security.x509.CertificateX509Key;
import org.mozilla.jss.netscape.security.x509.Extension;
import org.mozilla.jss.netscape.security.x509.GeneralName;
import org.mozilla.jss.netscape.security.x509.GeneralNames;
import org.mozilla.jss.netscape.security.x509.GeneralNamesException;
import org.mozilla.jss.netscape.security.x509.KeyIdentifier;
import org.mozilla.jss.netscape.security.x509.KeyUsageExtension;
import org.mozilla.jss.netscape.security.x509.PKIXExtensions;
import org.mozilla.jss.netscape.security.x509.RevokedCertImpl;
import org.mozilla.jss.netscape.security.x509.RevokedCertificate;
import org.mozilla.jss.netscape.security.x509.SubjectAlternativeNameExtension;
import org.mozilla.jss.netscape.security.x509.SubjectKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CRLImpl;
import org.mozilla.jss.netscape.security.x509.X509CertImpl;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;
import org.mozilla.jss.netscape.security.x509.X509Key;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * PKI utility that uses the JSS crypto provider
 */
public class JSSPKIUtility extends ProviderBasedPKIUtility {
    public static final byte[] LINE_SEPARATOR = String.format("%n").getBytes();
    public static final String SIGNING_ALG_ID = "SHA256withRSA";

    public static final String CRL_PEM_NAME = "X509 CRL";
    public static final String CERTIFICATE_PEM_NAME = "CERTIFICATE";

    // Note that using RSA PRIVATE KEY instead of PRIVATE KEY will indicate this is
    // a PKCS1 format instead of a PKCS8.
    public static final String PRIVATE_KEY_PEM_NAME = "RSA PRIVATE KEY";

    @Inject
    public JSSPKIUtility(CertificateReader reader, SubjectKeyIdentifierWriter writer, Configuration config) {
        super(reader, writer, config);
    }

    @Override
    public X509Certificate createX509Certificate(String dn, Set<X509ExtensionWrapper> extensions,
        Set<X509ByteExtensionWrapper> byteExtensions, Date startDate, Date endDate, KeyPair clientKeyPair,
        BigInteger serialNumber, String alternateName) throws IOException {

        X509CertInfo certInfo = new X509CertInfo();
        try {
            X509Certificate caCert = reader.getCACert();
            byte[] publicKeyEncoded = clientKeyPair.getPublic().getEncoded();

            certInfo.set(X509CertInfo.ISSUER,
                new CertificateIssuerName(new X500Name(caCert.getSubjectX500Principal().getEncoded())));
            certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
            certInfo.set(X509CertInfo.VALIDITY, new CertificateValidity(startDate, endDate));
            certInfo.set(X509CertInfo.SUBJECT, new CertificateSubjectName(new X500Name(dn)));
            certInfo.set(X509CertInfo.KEY,
                new CertificateX509Key(X509Key.parse(new DerValue(publicKeyEncoded))));
            certInfo.set(X509CertInfo.ALGORITHM_ID,
                new CertificateAlgorithmId(AlgorithmId.get(SIGNING_ALG_ID)));
            certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

            CertificateExtensions certExtensions = buildStandardExtensions(new CertificateExtensions(),
                dn, clientKeyPair, extensions, caCert, alternateName);
            certInfo.set(X509CertInfo.EXTENSIONS, certExtensions);

            if (extensions != null) {
                for (X509ExtensionWrapper wrapper : extensions) {
                    // Avoid null values. Set them to blank if they are null
                    String value = wrapper.getValue() == null ? "" :  wrapper.getValue();
                    UTF8String der = new UTF8String(value);
                    certExtensions.add(buildCustomExtension(wrapper.getOid(), wrapper.isCritical(), der));
                }
            }

            if (byteExtensions != null) {
                for (X509ByteExtensionWrapper wrapper : byteExtensions) {
                    // Avoid null values. Set them to blank if they are null
                    byte[] value = wrapper.getValue() == null ? new byte[0] : wrapper.getValue();
                    OCTET_STRING der = new OCTET_STRING(value);
                    certExtensions.add(buildCustomExtension(wrapper.getOid(), wrapper.isCritical(), der));
                }
            }

            X509CertImpl certImpl = new X509CertImpl(certInfo);
            certImpl.sign(reader.getCaKey(), SIGNING_ALG_ID);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certImpl.getEncoded()));
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException("Could not create X.509 certificate", e);
        }
    }

    /**
     * The Extension class expects to receive an octet string.  This method just takes care of
     * wrapping the ASN1Value we send within an octet string and then returns the extension object.
     * @param oid a String of the object identifier
     * @param critical whether the extension should be marked critical
     * @param der the actual DER of the extension
     * @return an Extension object
     * @throws IOException if the Extension cannot be created
     */
    private Extension buildCustomExtension(String oid, boolean critical, ASN1Value der) throws IOException {
        byte[] extnValue = ASN1Util.encode(new OCTET_STRING(ASN1Util.encode(der)));

        /* FIXME there is currently a bug in JSS that prevents it from creating object identifiers with
         * components that are larger than Integer.MAX_VALUE.  In several places (e.g. ueber certs) we use
         * product IDs as components of OIDs and product ids are generally larger that Integer.MAX_VALUE.
         * As a result, we can't encode those extensions currently.
         */
        return new Extension(new ObjectIdentifier(oid), critical, extnValue);
    }

    /**
     * Add boilerplate extensions required by RFC 5280.
     * @param certExtensions a CertificateExtensions object to modify
     * @param keyPair the KeyPair used to create the SubjectKeyIdentifier extension
     * @param providedExtensions A Set of provided extensions that will be added to the certificate.  In some
     * cases (hosted mode) access to the information in those extensions is required for creating the
     * subjectKeyIdentifier.
     *
     * @return a modified version of the certExtensions parameter
     * @throws IOException in case of encoding failures
     */
    private CertificateExtensions buildStandardExtensions(CertificateExtensions certExtensions,
        String dn, KeyPair keyPair, Set<X509ExtensionWrapper> providedExtensions, X509Certificate caCert,
        String alternateName) throws IOException {
        /* The RFC states that KeyUsage SHOULD be marked as critical.  In previous Candlepin code we were
         * not marking it critical but this constructor will.  I do not believe there should be any
         * compatibility issues, but I am noting it just in case. */
        KeyUsageExtension keyUsage = new KeyUsageExtension();
        keyUsage.set(KeyUsageExtension.DIGITAL_SIGNATURE, true);
        keyUsage.set(KeyUsageExtension.KEY_ENCIPHERMENT, true);
        keyUsage.set(KeyUsageExtension.DATA_ENCIPHERMENT, true);
        certExtensions.add(keyUsage);

        // Not critical by default
        ExtendedKeyUsageExtension extendedKeyUsage = new ExtendedKeyUsageExtension();
        /* JSS doesn't have a constant defined for the "clientAuth" OID so we have to put it in by hand.
         * See https://tools.ietf.org/html/rfc5280#appendix-A specifically id-kp-clientAuth.  This OID
         * denotes that a certificate is meant for client authentication over TLS */
        extendedKeyUsage.addOID(new ObjectIdentifier("1.3.6.1.5.5.7.3.2"));
        certExtensions.add(extendedKeyUsage);

        // Not critical for non-CA certs.  -1 pathLen means it won't be encoded.
        BasicConstraintsExtension basicConstraints = new BasicConstraintsExtension(false, -1);
        certExtensions.add(basicConstraints);

        try {
            /* Not critical by default.  I am extremely dubious that we actually need this extension
             * but I'm keeping it because our old cert creation code added it. */
            NSCertTypeExtension netscapeCertType = new NSCertTypeExtension();
            netscapeCertType.set(NSCertTypeExtension.SSL_CLIENT, true);
            netscapeCertType.set(NSCertTypeExtension.EMAIL, true);
            certExtensions.add(netscapeCertType);
        }
        catch (CertificateException e) {
            throw new IOException("Could not construct certificate extensions", e);
        }

        try {
            /* The JSS SubjectKeyIdentifierExtension class expects you to give it the unencoded KeyIdentifier.
             * The SubjectKeyIdentifierExtension class, however, returns the encoded KeyIdentifier (an DER
             * octet string).  Therefore, we need to unpack the KeyIdentifier. */
            byte[] encodedSki = subjectKeyWriter.getSubjectKeyIdentifier(keyPair, providedExtensions);
            OCTET_STRING extOctets = (OCTET_STRING) ASN1Util.decode(new OCTET_STRING.Template(), encodedSki);
            // Required to be non-critical
            SubjectKeyIdentifierExtension ski = new SubjectKeyIdentifierExtension(extOctets.toByteArray());
            certExtensions.add(ski);

            // Not critical by default
            AuthorityKeyIdentifierExtension aki = buildAuthorityKeyIdentifier(caCert);
            certExtensions.add(aki);

            /*
             * Why add the certificate subject again as an alternative name?  RFC 6125 Section 6.4.4
             * stipulates that if SANs are provided, a validator MUST use them instead of the certificate
             * subject.  If no SANs are present, the RFC allows the validator to use the subject field.  So,
             * if we do have an SAN to add, we need to add the subject field again as an SAN.

             * See http://stackoverflow.com/questions/5935369 and
             * https://tools.ietf.org/html/rfc6125#section-6.4.4
             */
            // Not critical by default and should *not* be critical since the subject field isn't empty
            SubjectAlternativeNameExtension altNames = new SubjectAlternativeNameExtension();
            GeneralName[] akiName;
            if (alternateName == null) {
                akiName = new GeneralName[1];
            }
            else {
                akiName = new GeneralName[2];
                akiName[1] = new GeneralName(new X500Name("CN=" + alternateName));
            }

            akiName[0] = new GeneralName(new X500Name(dn));

            GeneralNames generalNames = new GeneralNames(akiName);
            altNames.setGeneralNames(generalNames);

            certExtensions.add(altNames);
        }
        catch (InvalidBERException | GeneralNamesException e) {
            throw new IOException("Could not construct certificate extensions", e);
        }

        return certExtensions;
    }

    @Override
    public X509CRL createX509CRL(List<X509CRLEntryWrapper> entries, BigInteger crlNumber) {
        try {
            X509Certificate caCert = reader.getCACert();

            CRLExtensions entryExtensions = new CRLExtensions();
            entryExtensions.add(CRLReasonExtension.PRIVILEGE_WITHDRAWN);

            List<RevokedCertificate> revokedCerts = entries.stream()
                .map(e -> new RevokedCertImpl(e.getSerialNumber(), e.getRevocationDate(), entryExtensions))
                .collect(Collectors.toCollection(ArrayList::new));

            CRLExtensions crlExtensions = new CRLExtensions();
            crlExtensions.add(new CRLNumberExtension(crlNumber));
            crlExtensions.add(buildAuthorityKeyIdentifier(caCert));

            X500Name issuer = new X500Name(caCert.getIssuerX500Principal().getEncoded());
            Date until = Util.addDaysToDt(config.getInt(ConfigProperties.CRL_NEXT_UPDATE_DELTA));
            X509CRLImpl crlImpl = new X509CRLImpl(
                issuer,
                new Date(),
                until,
                revokedCerts.toArray(new RevokedCertificate[] {}),
                crlExtensions
            );

            crlImpl.sign(reader.getCaKey(), SIGNING_ALG_ID);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlImpl.getEncoded()));
        }
        catch (GeneralSecurityException | IOException | InvalidBERException e) {
            throw new RuntimeException("Error creating CRL", e);
        }
    }

    /**
     * Calculate the KeyIdentifier for an RSAPublicKey and place it in an AuthorityKeyIdentifier extension.
     *
     * Java encodes RSA public keys using the SubjectPublicKeyInfo type described in RFC 5280.
     * <pre>
     * SubjectPublicKeyInfo  ::=  SEQUENCE  {
     *   algorithm            AlgorithmIdentifier,
     *   subjectPublicKey     BIT STRING  }
     *
     * AlgorithmIdentifier  ::=  SEQUENCE  {
     *   algorithm               OBJECT IDENTIFIER,
     *   parameters              ANY DEFINED BY algorithm OPTIONAL  }
     * </pre>
     *
     * A KeyIdentifier is a SHA-1 digest of the subjectPublicKey bit string from the ASN.1 above.
     *
     * @param key the RSAPublicKey to use
     * @return an AuthorityKeyIdentifierExtension based on the key
     * @throws IOException if we can't construct a MessageDigest object.
     */
    public static AuthorityKeyIdentifierExtension buildAuthorityKeyIdentifier(RSAPublicKey key)
        throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");

            byte[] encodedKey = key.getEncoded();

            DerInputStream s = new DerValue(encodedKey).toDerInputStream();
            // Skip the first item in the sequence, AlgorithmIdentifier.
            // The parameter, startLen, is required for skipSequence although it's unused.
            s.skipSequence(0);
            // Get the key's bit string
            BitArray b = s.getUnalignedBitString();
            byte[] digest = d.digest(b.toByteArray());

            KeyIdentifier ki = new KeyIdentifier(digest);
            return new AuthorityKeyIdentifierExtension(ki, null, null);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not find SHA1 implementation", e);
        }
    }

    public static AuthorityKeyIdentifierExtension buildAuthorityKeyIdentifier(X509Certificate caCert)
        throws InvalidBERException, IOException {
        // The subject key identifier of the CA becomes the Authority Key Identifer of the CRL.
        byte[] extValue = caCert.getExtensionValue(PKIXExtensions.SubjectKey_Id.toString());

        /* The getExtensionValue returns us the Extension extnValue element which is an octet string.  For
         * the SubjectKeyIdentifier extension the extnValue only contains a KeyIdentifier.  The actual
         * KeyIdentifier is also an octet string.  The extnValue for the SubjectKeyIdentifier
         * is therefore ultimately an octet string of an octet string.  See Appendix A of RFC 5280. */
        OCTET_STRING extOctets = (OCTET_STRING) ASN1Util.decode(new OCTET_STRING.Template(), extValue);
        OCTET_STRING ski = (OCTET_STRING) ASN1Util.decode(
            new OCTET_STRING.Template(), extOctets.toByteArray()
        );

        if (ski == null) {
            /* If the SubjectPublicKey extension isn't available, we can calculate the value ourselves
             * from the certificate's public key. */
            return buildAuthorityKeyIdentifier((RSAPublicKey) caCert.getPublicKey());
        }

        /* RFC 5280 section 4.2.1.1 is a bit odd.  It states the AuthorityKeyIdentifier MAY contain
         * a KeyIdentifier, GeneralNames, or CertificateSerialNumber.  The KeyIdentifier is mandatory for
         * non-self-signed certificates, but there is no additional guidance about when or why one should
         * provide the GeneralNames or CertificateSerialNumber.  I've found at least one place,
         * https://www.v13.gr/blog/?p=293, that explicitly recommends against giving them.  Plus in our old
         * crypto code that used BouncyCastle, we were not providing these values, so I'm leaving them out.
         */
        KeyIdentifier ki = new KeyIdentifier(ski.toByteArray());
        return new AuthorityKeyIdentifierExtension(ki, null, null);
    }

    private byte[] getPemEncoded(byte[] der, String type) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writePemEncoded(der, out, type);
            return out.toByteArray();
        }
    }

    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        try {
            return getPemEncoded(cert.getEncoded(), CERTIFICATE_PEM_NAME);
        }
        catch (CertificateEncodingException e) {
            throw new IOException("Could not encode certificate", e);
        }
    }

    @Override
    public byte[] getPemEncoded(RSAPrivateKey key) throws IOException {
        try {
            return getPemEncoded(toPKCS1(key), PRIVATE_KEY_PEM_NAME);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not encode key", e);
        }
    }

    @Override
    public byte[] getPemEncoded(X509CRL crl) throws IOException {
        try {
            return getPemEncoded(crl.getEncoded(), CRL_PEM_NAME);
        }
        catch (CRLException e) {
            throw new IOException("Could not encode CRL", e);
        }
    }

    private void writePemEncoded(byte[] der, OutputStream out, String type) throws IOException {
        out.write(("-----BEGIN " + type + "-----\n").getBytes(Charsets.UTF_8));

        // Write base64 encoded DER.  Does not close the underlying stream.
        Base64OutputStream b64Out = new Base64OutputStream(out, true, 64, LINE_SEPARATOR);
        b64Out.write(der);
        b64Out.eof();
        b64Out.flush();
        out.write(("-----END " + type + "-----\n").getBytes(Charsets.UTF_8));
    }

    @Override
    public void writePemEncoded(X509Certificate cert, OutputStream out) throws IOException {
        try {
            writePemEncoded(cert.getEncoded(), out, CERTIFICATE_PEM_NAME);
        }
        catch (CertificateEncodingException e) {
            throw new IOException("Could not encode certificate", e);
        }
    }

    @Override
    public void writePemEncoded(RSAPrivateKey key, OutputStream out) throws IOException {
        try {
            writePemEncoded(toPKCS1(key), out, PRIVATE_KEY_PEM_NAME);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not encode key", e);
        }
    }

    @Override
    public void writePemEncoded(X509CRL crl, OutputStream out) throws IOException {
        try {
            writePemEncoded(crl.getEncoded(), out, CRL_PEM_NAME);
        }
        catch (CRLException e) {
            throw new IOException("Could not encode CRL", e);
        }
    }

    /**
     * Java will encode a Key object using the PKCS8 format (defined in RFC 5208).  Unfortunately, OpenSSL
     * encodes RSA keys using PKCS1 (defined in Appendix A of RFC 3447).  All of our older code expects to
     * work with PKCS1 formats.  I do not know for a fact that switching to PKCS8 would break anything, but I
     * don't care to risk it.
     * @param key a Key to encode in PKCS1 format
     * @return a PKCS1 format key encoded in DER
     */
    private byte[] toPKCS1(RSAPrivateKey key) throws NoSuchAlgorithmException, IOException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKeySpec keySpec;
        try {
            // Crt here stands for "Chinese Remainder Theorem", a commonly used way to optimize RSA.  Some
            // extra numbers are stored with the RSA modulus and public and private exponents that speed up
            // the modular exponentiation computations required for decryption.
            keySpec = kf.getKeySpec(key, RSAPrivateCrtKeySpec.class);
        }
        catch (InvalidKeySpecException e) {
            // If our RSAPrivateKey is not actually an RSAPrivateCrtKey, we're not going to be able to
            // encode it to PKCS1.
            throw new IOException("Key provided is not of type RSA with CRT attributes", e);
        }

        Integer version = 0;
        BigInteger modulus = keySpec.getModulus();
        BigInteger publicExponent = keySpec.getPublicExponent();
        BigInteger privateExponent = keySpec.getPrivateExponent();
        BigInteger primeP = keySpec.getPrimeP();
        BigInteger primeQ = keySpec.getPrimeQ();
        BigInteger primeExponentP = keySpec.getPrimeExponentP();
        BigInteger primeExponentQ = keySpec.getPrimeExponentQ();
        BigInteger coefficient = keySpec.getCrtCoefficient();

        SEQUENCE sequence = new SEQUENCE();
        sequence.addElement(new INTEGER(version));
        sequence.addElement(new INTEGER(modulus));
        sequence.addElement(new INTEGER(publicExponent));
        sequence.addElement(new INTEGER(privateExponent));
        sequence.addElement(new INTEGER(primeP));
        sequence.addElement(new INTEGER(primeQ));
        sequence.addElement(new INTEGER(primeExponentP));
        sequence.addElement(new INTEGER(primeExponentQ));
        sequence.addElement(new INTEGER(coefficient));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sequence.encode(baos);
        return baos.toByteArray();
    }
}

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

import org.candlepin.config.Configuration;
import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.asn1.ASN1Util;
import org.mozilla.jss.asn1.ASN1Value;
import org.mozilla.jss.asn1.InvalidBERException;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.asn1.UTF8String;
import org.mozilla.jss.crypto.CryptoToken;
import org.mozilla.jss.crypto.KeyPairAlgorithm;
import org.mozilla.jss.crypto.KeyPairGenerator;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.crypto.TokenRuntimeException;
import org.mozilla.jss.netscape.security.extensions.ExtendedKeyUsageExtension;
import org.mozilla.jss.netscape.security.extensions.NSCertTypeExtension;
import org.mozilla.jss.netscape.security.util.BitArray;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.ObjectIdentifier;
import org.mozilla.jss.netscape.security.x509.AlgorithmId;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.BasicConstraintsExtension;
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
import org.mozilla.jss.netscape.security.x509.SubjectAlternativeNameExtension;
import org.mozilla.jss.netscape.security.x509.SubjectKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.X500Name;
import org.mozilla.jss.netscape.security.x509.X509CertImpl;
import org.mozilla.jss.netscape.security.x509.X509CertInfo;
import org.mozilla.jss.netscape.security.x509.X509Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.inject.Inject;



/**
 * PKI utility that uses the JSS crypto provider
 */
public class JSSPKIUtility extends ProviderBasedPKIUtility {
    private static final Logger log = LoggerFactory.getLogger(JSSPKIUtility.class);

    private static final String KEY_ALGORITHM = "RSA";
    public static final int KEY_SIZE = 4096;

    public static final byte[] LINE_SEPARATOR = String.format("%n").getBytes();
    public static final String SIGNING_ALG_ID = "SHA256withRSA";

    public static final String CERTIFICATE_PEM_NAME = "CERTIFICATE";

    // Note that using RSA PRIVATE KEY instead of PRIVATE KEY will indicate this is
    // a PKCS1 format instead of a PKCS8.
    public static final String PRIVATE_KEY_PEM_NAME = "PRIVATE KEY";

    /**
     * PublicKey implementation that guarantees access to its format and encoding
     */
    private static class InsecurePublicKey implements PublicKey {

        private final String algorithm;
        private final byte[] encoded;
        private final String format;

        public InsecurePublicKey(PublicKey impl, byte[] encoded, String format) {
            this.algorithm = impl.getAlgorithm();
            this.encoded = Objects.requireNonNull(encoded);
            this.format = Objects.requireNonNull(format);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAlgorithm() {
            return this.algorithm;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] getEncoded() {
            return this.encoded;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFormat() {
            return this.format;
        }
    }

    /**
     * PrivateKey implementation that guarantees access to its format and encoding
     */
    private static class InsecurePrivateKey implements PrivateKey {

        private final String algorithm;
        private final byte[] encoded;
        private final String format;

        public InsecurePrivateKey(PrivateKey impl, byte[] encoded, String format) {
            this.algorithm = impl.getAlgorithm();
            this.encoded = Objects.requireNonNull(encoded);
            this.format = Objects.requireNonNull(format);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAlgorithm() {
            return this.algorithm;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] getEncoded() {
            return this.encoded;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFormat() {
            return this.format;
        }
    }


    private final KeyPairDataCurator keypairDataCurator;


    @Inject
    public JSSPKIUtility(CertificateReader reader, SubjectKeyIdentifierWriter writer, Configuration config,
        KeyPairDataCurator keypairDataCurator) {

        super(reader, writer, config);

        this.keypairDataCurator = keypairDataCurator;
    }

    @Override
    public X509Certificate createX509Certificate(String dn, Set<X509ExtensionWrapper> extensions,
        Set<X509ByteExtensionWrapper> byteExtensions, Date startDate, Date endDate, KeyPair clientKeyPair,
        BigInteger serialNumber, String alternateName) throws IOException {

        // Ensure JSS is properly initialized before attempting any operations with it
        JSSProviderLoader.initialize();

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

            // Impl note:
            // The reencoding here is necessary to get cert extensions to register. If we return the
            // X509CertImpl instance above, the encoding will be correct, and the cert will be
            // valid, it just won't have any extensions present in the object.
            return new X509CertImpl(certImpl.getEncoded());
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
            if (alternateName != null) {
                SubjectAlternativeNameExtension altNames = new SubjectAlternativeNameExtension();
                GeneralName[] akiName = new GeneralName[2];
                akiName[0] = new GeneralName(new X500Name(dn));
                akiName[1] = new GeneralName(new X500Name("CN=" + alternateName));
                GeneralNames generalNames = new GeneralNames(akiName);
                altNames.setGeneralNames(generalNames);
                certExtensions.add(altNames);
            }
        }
        catch (InvalidBERException | GeneralNamesException | NoSuchAlgorithmException e) {
            throw new IOException("Could not construct certificate extensions", e);
        }

        return certExtensions;
    }

    /**
     * Calculate the KeyIdentifier for a public key and place it in an AuthorityKeyIdentifier extension.
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
     * @param key the public key to use
     * @return an AuthorityKeyIdentifierExtension based on the key
     * @throws IOException if we can't construct a MessageDigest object.
     */
    public static AuthorityKeyIdentifierExtension buildAuthorityKeyIdentifier(PublicKey key)
        throws IOException {
        try {
            Provider provider = JSSProviderLoader.getProvider(true);
            MessageDigest d = MessageDigest.getInstance("SHA-1", provider);

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
            return buildAuthorityKeyIdentifier(caCert.getPublicKey());
        }

        /* RFC 5280 section 4.2.1.1 is a bit odd.  It states the AuthorityKeyIdentifier MAY contain
         * a KeyIdentifier or the issuer name and CertificateSerialNumber.  The KeyIdentifier is mandatory for
         * non-self-signed certificates, but there is no additional guidance about when or why one should
         * provide the issuer name or CertificateSerialNumber.  I've found at least one place,
         * https://www.v13.gr/blog/?p=293, that explicitly recommends against giving them.  Also,
         * the semantics around the issuer field in this extension can be very confusing
         * (see https://www.openssl.org/docs/faq.html#USER14).  Our old crypto code that used BouncyCastle
         * did include the issuer and serial number along with the key identifier, but I think it's best if
         * we leave it out.
         */
        KeyIdentifier ki = new KeyIdentifier(ski.toByteArray());
        return new AuthorityKeyIdentifierExtension(ki, null, null);
    }

    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        if (cert == null) {
            throw new IllegalArgumentException("cert is null");
        }

        try {
            return getPemEncoded(cert.getEncoded(), CERTIFICATE_PEM_NAME);
        }
        catch (CertificateEncodingException e) {
            throw new IOException("Could not encode certificate", e);
        }
    }

    @Override
    public byte[] getPemEncoded(PrivateKey key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        try {
            byte[] encoded = this.getKeyEncoding(key);
            return this.getPemEncoded(encoded, PRIVATE_KEY_PEM_NAME);
        }
        catch (Exception e) {
            throw new IOException("Could not encode key", e);
        }
    }

    private byte[] getPemEncoded(byte[] der, String type) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writePemEncoded(der, out, type);
            return out.toByteArray();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyPair generateKeyPair() throws KeyException {
        try {
            CryptoManager manager = JSSProviderLoader.getCryptoManager(true);
            CryptoToken token = manager.getInternalKeyStorageToken();
            KeyPairGenerator kpgen = token.getKeyPairGenerator(KeyPairAlgorithm.fromString(KEY_ALGORITHM));

            kpgen.temporaryPairs(true);
            kpgen.sensitivePairs(true);
            kpgen.extractablePairs(true); // probably extraneous; does nothing in FIPS mode
            kpgen.initialize(KEY_SIZE);

            KeyPair keypair = kpgen.genKeyPair();
            return this.buildInsecureKeyPair(keypair);
        }
        catch (NoSuchAlgorithmException | TokenException | TokenRuntimeException e) {
            throw new KeyException(e);
        }
    }

    /**
     * Rebuilds an "insecure" KeyPair instance from another KeyPair instance. The returned KeyPair
     * will allow fetching the encoded form of both the public and private key.
     *
     * @param keypair
     *  the keypair to rebuild
     *
     * @return
     *  a keypair which allows extracting the encoded form of both the public and private key
     */
    private KeyPair buildInsecureKeyPair(KeyPair keypair) throws KeyException {
        return this.buildInsecureKeyPair(keypair.getPublic(), keypair.getPrivate());
    }

    /**
     * Builds an "insecure" KeyPair instance from the provided public and private keys. The returned
     * KeyPair will allow fetching the encoded form of both the public and private key.
     *
     * @param publicKey
     *  the public key to use to build the insecure keypair
     *
     * @param privateKey
     *  the private key to use to build the insecure keypair
     *
     * @return
     *  a keypair which allows extracting the encoded form of both the public and private key
     */
    private KeyPair buildInsecureKeyPair(PublicKey publicKey, PrivateKey privateKey)
        throws KeyException {

        byte[] pubEncoded = publicKey.getEncoded();
        byte[] privEncoded = this.getKeyEncoding(privateKey);

        PublicKey insecurePub = new InsecurePublicKey(publicKey, pubEncoded, publicKey.getFormat());
        PrivateKey insecurePriv = new InsecurePrivateKey(privateKey, privEncoded, "PKCS#8");

        return new KeyPair(insecurePub, insecurePriv);
    }

    /**
     * Fetches the encoded form of the specified private key
     *
     * @param privateKey
     *  the private key from which to fetch the encoded form
     *
     * @return
     *  the encoded form of the given private key
     */
    private byte[] getKeyEncoding(PrivateKey privateKey) throws KeyException {
        byte[] unwrapped = privateKey.getEncoded();

        if (unwrapped != null) {
            return unwrapped;
        }

        try {
            String algorithm = "AES";
            String transformation = "AES/CBC/PKCS5Padding";
            int blockSize = 16; // bytes
            int keySize = 256; // bits

            Provider provider = JSSProviderLoader.getProvider(true);

            KeyGenerator keygen = KeyGenerator.getInstance(algorithm, provider);
            keygen.init(keySize);

            SecretKey skey = keygen.generateKey();
            IvParameterSpec ivspec = new IvParameterSpec(new byte[blockSize]);
            Arrays.fill(ivspec.getIV(), (byte) blockSize);

            Cipher cipher = Cipher.getInstance(transformation, provider);
            cipher.init(Cipher.WRAP_MODE, skey, ivspec);

            byte[] wrapped = cipher.wrap(privateKey);

            cipher = Cipher.getInstance(transformation, provider);
            cipher.init(Cipher.DECRYPT_MODE, skey, ivspec);

            unwrapped = cipher.doFinal(wrapped);
        }
        catch (Exception e) {
            throw new KeyException(e);
        }

        return unwrapped;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyPair getConsumerKeyPair(Consumer consumer) throws KeyException {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        KeyPairData kpdata = consumer.getKeyPairData();
        KeyPair keypair = null;

        if (kpdata == null) {
            // no key data, create new and persist
            keypair = this.generateKeyPair();

            kpdata = new KeyPairData()
                .setPublicKeyData(keypair.getPublic().getEncoded())
                .setPrivateKeyData(keypair.getPrivate().getEncoded());

            kpdata = this.keypairDataCurator.create(kpdata, false);
            consumer.setKeyPairData(kpdata);
        }
        else {
            // Try to process as PKCS8 data
            keypair = this.processAsPKCS8(kpdata);

            // If output is null, it's not PKCS8 data, try to process it as a Java-serialized object
            if (keypair == null) {
                log.info("Key pair does not appear to be PKCS8 data; attempting Java deserialization...");
                keypair = this.processAsJSO(kpdata);

                // If output is still null here, the key is malformed, so we should generate
                // a new one
                if (keypair == null) {
                    log.warn("Malformed key data found for consumer {}, generating new key pair", consumer);
                    keypair = this.generateKeyPair();
                }

                // In either case, we need to update the the key pair data associated with the
                // consumer so we can avoid this conversion in the future.
                kpdata.setPublicKeyData(keypair.getPublic().getEncoded());
                kpdata.setPrivateKeyData(keypair.getPrivate().getEncoded());

                kpdata = this.keypairDataCurator.merge(kpdata);
                consumer.setKeyPairData(kpdata);
            }
        }

        return keypair;
    }

    /**
     * Attempts to process the given keypair data as if the keys are PKCS8 formatted. If the key
     * pair data is incomplete or invalid, this method returns null.
     *
     * @param kpdata
     *  the key pair data to process
     *
     * @return
     *  a KeyPair consisting of the keys from the provided key pair data, or null if the key pair
     *  data could not be processed
     */
    private KeyPair processAsPKCS8(KeyPairData kpdata) {
        try {
            PublicKey publicKey = this.generatePublicKey(kpdata.getPublicKeyData(), KEY_ALGORITHM);
            PrivateKey privateKey = this.generatePrivateKey(kpdata.getPrivateKeyData(), KEY_ALGORITHM);

            return this.buildInsecureKeyPair(publicKey, privateKey);
        }
        catch (GeneralSecurityException e) {
            // If any exception occurred, the keys are either malformed or not PKCS8 keys
            log.debug("Unexpected exception occurred while parsing key data: ", e);
            return null;
        }
    }

    /**
     * Attempts to process the given keypair data as if the keys are Java-serialized key objects. If
     * the key pair data is incomplete or invalid, this method returns null.
     *
     * @param kpdata
     *  the key pair data to process
     *
     * @return
     *  a KeyPair consisting of the keys from the provided key pair data, or null if the key pair
     *  data could not be processed
     */
    private KeyPair processAsJSO(KeyPairData kpdata) {
        try {
            PublicKey publicKey = this.deserializeKey(kpdata.getPublicKeyData(), PublicKey.class);
            PrivateKey privateKey = this.deserializeKey(kpdata.getPrivateKeyData(), PrivateKey.class);

            return this.buildInsecureKeyPair(publicKey, privateKey);
        }
        catch (GeneralSecurityException | ClassNotFoundException | IOException e) {
            // If any exception occurred, the keys are either malformed, not Java-serialized key
            // objects, or something that doesn't allow extracting key data
            log.debug("Unexpected exception occurred while deserializing key data: ", e);
            return null;
        }
    }

    /**
     * Deserializes the given byte array into an object of the specified class. If the data provided
     * cannot be deserialized into the given class, this method throws an exception.
     *
     * @param bytes
     *  the data to deserialize
     *
     * @param keyClass
     *  the key class to which the data should be deserialized
     *
     * @throws IOException
     *  if the key data is malformed, invalid, or otherwise cannot be deserialized to the specified
     *  class
     *
     * @return
     *  an instance of the given key class representing the key data provided
     */
    private <T extends Key> T deserializeKey(byte[] bytes, Class<T> keyClass)
        throws ClassNotFoundException, IOException {

        if (bytes == null) {
            throw new IOException("no data to deserialize");
        }

        try (ObjectInputStream ostream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = ostream.readObject();

            if (!keyClass.isInstance(obj)) {
                throw new IOException("incorrect object parsed from key data: " + obj.getClass());
            }

            return (T) obj;
        }
    }

    /**
     * Generates a PublicKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the X509 encoded public key data from which to generate a PublicKey instance
     *
     * @param algorithm
     *  the algorithm used to generate the key
     *
     * @return
     *  a PublicKey instance
     */
    private PublicKey generatePublicKey(byte[] keydata, String algorithm)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyFactory factory = KeyFactory.getInstance(algorithm, JSSProviderLoader.getProvider(true));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keydata, algorithm);

        return factory.generatePublic(spec);
    }

    /**
     * Generates a PrivateKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the PKCS8 private key data from which to generate a PrivateKey instance
     *
     * @param algorithm
     *  the algorithm used to generate the key
     *
     * @return
     *  a PrivateKey instance
     */
    private PrivateKey generatePrivateKey(byte[] keydata, String algorithm)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyFactory factory = KeyFactory.getInstance(algorithm, JSSProviderLoader.getProvider(true));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keydata, algorithm);

        return factory.generatePrivate(spec);
    }

}

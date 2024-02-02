/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;

import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import javax.inject.Provider;

/**
 * The default {@link ProviderBasedPKIUtility} for Candlepin.
 * This class implements methods to create X509 Certificates, X509 CRLs, encode
 * objects in PEM format (for saving to the db or sending to the client), and
 * decode raw ASN.1 DER values (as read from a Certificate/CRL).
 * <p>
 * All code that imports bouncycastle should live in this module.
 * <p>
 * (March 24, 2011) Notes on implementing a ProviderBasedPKIUtility with NSS/JSS:
 * <p>
 * JSS provides classes and functions to generate X509Certificates (see CertificateInfo,
 * for example).
 * <p>
 * PEM encoding requires us to determine the object type (which we know), add the correct
 * header and footer to the output, base64 encode the DER for the object, and line wrap
 * the base64 encoding.
 * <p>
 * decodeDERValue should be simple, as JSS provides code to parse ASN.1, but I wasn't
 * able to get it to work.
 * <p>
 * The big one is CRL generation. JSS has no code to generate CRLs in any format. We'll
 * have to use the raw ASN.1 libraries to build up our own properly formatted CRL DER
 * representation, then PEM encode it.
 */
public class BouncyCastlePKIUtility extends ProviderBasedPKIUtility {
    private static final byte[] LINE_SEPARATOR = String.format("%n").getBytes();
    private static final String SIGNING_ALG_ID = "SHA256withRSA";
    private static final String PRIVATE_KEY_PEM_NAME = "PRIVATE KEY";

    private final Provider<BouncyCastleProvider> securityProvider;

    @Inject
    public BouncyCastlePKIUtility(Provider<BouncyCastleProvider> securityProvider, CertificateReader reader,
        SubjectKeyIdentifierWriter subjectKeyWriter, Configuration config,
        KeyPairDataCurator keypairDataCurator) {
        super(reader, subjectKeyWriter, config);
        this.securityProvider = Objects.requireNonNull(securityProvider);
    }

    @Override
    public X509Certificate createX509Certificate(DistinguishedName dn,
        Set<X509Extension> extensions, Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException {

        X509Certificate caCert = reader.getCACert();
        byte[] publicKeyEncoded = clientKeyPair.getPublic().getEncoded();

        X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
            X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded()),
            serialNumber,
            startDate,
            endDate,
            new X500Name(dn.value()),
            SubjectPublicKeyInfo.getInstance(publicKeyEncoded));

        // set key usage - required for proper x509 function
        KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature |
            KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);

        // add SSL extensions - required for proper x509 function
        NetscapeCertType certType = new NetscapeCertType(
            NetscapeCertType.sslClient | NetscapeCertType.smime);

        certGen.addExtension(MiscObjectIdentifiers.netscapeCertType,
            false, certType);
        certGen.addExtension(Extension.keyUsage, false,
            keyUsage);

        JcaX509ExtensionUtils extensionUtil = new JcaX509ExtensionUtils();
        AuthorityKeyIdentifier aki = extensionUtil.createAuthorityKeyIdentifier(caCert);
        certGen.addExtension(Extension.authorityKeyIdentifier, false, aki.getEncoded());

        certGen.addExtension(Extension.subjectKeyIdentifier, false,
            subjectKeyWriter.getSubjectKeyIdentifier(clientKeyPair)
        );
        certGen.addExtension(Extension.extendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        certGen.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        // Add an additional alternative name if provided.
        if (alternateName != null) {
            /*
             Why add the certificate subject again as an alternative name?  RFC 6125 Section 6.4.4
             stipulates that if SANs are provided, a validator MUST use them instead of the certificate
             subject.  If no SANs are present, the RFC allows the validator to use the subject field.  So,
             if we do have an SAN to add, we need to add the subject field again as an SAN.

             See http://stackoverflow.com/questions/5935369 and
             https://tools.ietf.org/html/rfc6125#section-6.4.4 and

             NB: These extensions should *not* be marked critical since the subject field is not empty.
            */
            GeneralName subject = new GeneralName(GeneralName.directoryName, dn.value());
            GeneralName name = new GeneralName(GeneralName.directoryName,
                new DistinguishedName(alternateName).value());
            ASN1Encodable[] altNameArray = {subject, name};
            GeneralNames altNames = GeneralNames.getInstance(new DERSequence(altNameArray));
            certGen.addExtension(Extension.subjectAlternativeName, false, altNames);
        }

        if (extensions != null) {
            for (X509Extension extension : extensions) {
                certGen.addExtension(extension.oid(), extension.critical(), extension.value());
            }
        }

        JcaContentSignerBuilder builder = new JcaContentSignerBuilder(SIGNING_ALG_ID)
            .setProvider(this.securityProvider.get());
        ContentSigner signer;
        try {
            signer = builder.build(reader.getCaKey());
        }
        catch (OperatorCreationException e) {
            throw new IOException(e);
        }

        // Generate the certificate
        return new JcaX509CertificateConverter().getCertificate(certGen.build(signer));
    }

    private void writePemEncoded(Object obj, OutputStream out) throws IOException {
        OutputStreamWriter oswriter = new OutputStreamWriter(out);
        JcaPEMWriter writer = new JcaPEMWriter(oswriter);
        writer.writeObject(obj);
        writer.flush();
        // We're hoping close does nothing more than a flush and super.close() here
    }

    private byte[] getPemEncoded(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.writePemEncoded(obj, out);

        byte[] output = out.toByteArray();
        out.close();

        return output;
    }

    @Override
    public byte[] getPemEncoded(X509Certificate cert) throws IOException {
        return getPemEncoded((Object) cert);
    }

    @Override
    public byte[] getPemEncoded(PrivateKey key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        try {
            byte[] encoded = key.getEncoded();
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
        out.write(("-----BEGIN " + type + "-----\n").getBytes(StandardCharsets.UTF_8));

        // Write base64 encoded DER.  Does not close the underlying stream.
        Base64OutputStream b64Out = new Base64OutputStream(out, true, 64, LINE_SEPARATOR);
        b64Out.write(der);
        b64Out.eof();
        b64Out.flush();
        out.write(("-----END " + type + "-----\n").getBytes(StandardCharsets.UTF_8));
    }

}

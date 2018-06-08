/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.candlepin.pki.impl.BouncyCastleProviderLoader.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.ProviderBasedPKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The default {@link ProviderBasedPKIUtility} for Candlepin.
 * This class implements methods to create X509 Certificates, X509 CRLs, encode
 * objects in PEM format (for saving to the db or sending to the client), and
 * decode raw ASN.1 DER values (as read from a Certificate/CRL).
 *
 * All code that imports bouncycastle should live in this module.
 *
 * (March 24, 2011) Notes on implementing a ProviderBasedPKIUtility with NSS/JSS:
 *
 * JSS provides classes and functions to generate X509Certificates (see CertificateInfo,
 * for example).
 *
 * PEM encoding requires us to determine the object type (which we know), add the correct
 * header and footer to the output, base64 encode the DER for the object, and line wrap
 * the base64 encoding.
 *
 * decodeDERValue should be simple, as JSS provides code to parse ASN.1, but I wasn't
 * able to get it to work.
 *
 * The big one is CRL generation. JSS has no code to generate CRLs in any format. We'll
 * have to use the raw ASN.1 libraries to build up our own properly formatted CRL DER
 * representation, then PEM encode it.
 */
public class BouncyCastlePKIUtility extends ProviderBasedPKIUtility {
    private static Logger log = LoggerFactory.getLogger(BouncyCastlePKIUtility.class);

    @Inject
    public BouncyCastlePKIUtility(CertificateReader reader, SubjectKeyIdentifierWriter subjectKeyWriter,
        Configuration config) {
        super(reader, subjectKeyWriter, config);
    }

    @Override
    public X509Certificate createX509Certificate(String dn,
        Set<X509ExtensionWrapper> extensions, Set<X509ByteExtensionWrapper> byteExtensions,
        Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException {

        X509Certificate caCert = reader.getCACert();
        byte[] publicKeyEncoded = clientKeyPair.getPublic().getEncoded();

        X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
            X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded()),
            serialNumber,
            startDate,
            endDate,
            new X500Name(dn),
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

        certGen.addExtension(Extension.subjectKeyIdentifier,
            false,
            subjectKeyWriter.getSubjectKeyIdentifier(clientKeyPair, extensions)
        );
        certGen.addExtension(Extension.extendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

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
            GeneralName subject = new GeneralName(GeneralName.directoryName, dn);
            GeneralName name = new GeneralName(GeneralName.directoryName, "CN=" + alternateName);
            ASN1Encodable[] altNameArray = {subject, name};
            GeneralNames altNames = GeneralNames.getInstance(new DERSequence(altNameArray));
            certGen.addExtension(Extension.subjectAlternativeName, false, altNames);
        }

        if (extensions != null) {
            for (X509ExtensionWrapper wrapper : extensions) {
                // Bouncycastle hates null values. So, set them to blank
                // if they are null
                String value = wrapper.getValue() == null ? "" :  wrapper.getValue();
                certGen.addExtension(new ASN1ObjectIdentifier(wrapper.getOid()), wrapper.isCritical(),
                    new DERUTF8String(value));
            }
        }

        if (byteExtensions != null) {
            for (X509ByteExtensionWrapper wrapper : byteExtensions) {
                // Bouncycastle hates null values. So, set them to blank
                // if they are null
                byte[] value = wrapper.getValue() == null ? new byte[0] :
                    wrapper.getValue();
                certGen.addExtension(new ASN1ObjectIdentifier(wrapper.getOid()), wrapper.isCritical(),
                    new DEROctetString(value));
            }
        }

        JcaContentSignerBuilder builder = new JcaContentSignerBuilder(SIGNATURE_ALGO)
            .setProvider(BC_PROVIDER);
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

    @Override
    public X509CRL createX509CRL(List<X509CRLEntryWrapper> entries, BigInteger crlNumber) {
        try {
            X509Certificate caCert = reader.getCACert();
            X509v2CRLBuilder generator = new X509v2CRLBuilder(
                X500Name.getInstance(caCert.getIssuerX500Principal().getEncoded()),
                new Date()
            );
            generator.setNextUpdate(
                Util.addDaysToDt(config.getInt(ConfigProperties.CRL_NEXT_UPDATE_DELTA)));
            // add all the CRL entries.
            for (X509CRLEntryWrapper entry : entries) {
                generator.addCRLEntry(entry.getSerialNumber(), entry.getRevocationDate(),
                    CRLReason.privilegeWithdrawn);
            }
            log.info("Completed adding CRL numbers to the certificate.");

            JcaX509ExtensionUtils extentionUtil = new JcaX509ExtensionUtils();
            AuthorityKeyIdentifier aki = extentionUtil.createAuthorityKeyIdentifier(caCert);
            generator.addExtension(Extension.authorityKeyIdentifier, false, aki.getEncoded());
            generator.addExtension(Extension.cRLNumber, false, new CRLNumber(crlNumber));

            JcaContentSignerBuilder builder = new JcaContentSignerBuilder(SIGNATURE_ALGO).setProvider(
                BC_PROVIDER);
            ContentSigner signer;
            try {
                signer = builder.build(reader.getCaKey());
            }
            catch (OperatorCreationException e) {
                throw new IOException(e);
            }

            return new JcaX509CRLConverter().getCRL(generator.build(signer));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    public byte[] getPemEncoded(RSAPrivateKey key) throws IOException {
        return getPemEncoded((Object) key);
    }

    @Override
    public byte[] getPemEncoded(X509CRL crl) throws IOException {
        return getPemEncoded((Object) crl);
    }

    @Override
    public void writePemEncoded(X509Certificate cert, OutputStream out) throws IOException {
        this.writePemEncoded((Object) cert, out);
    }

    @Override
    public void writePemEncoded(RSAPrivateKey key, OutputStream out) throws IOException {
        this.writePemEncoded((Object) key, out);
    }

    @Override
    public void writePemEncoded(X509CRL crl, OutputStream out) throws IOException {
        this.writePemEncoded((Object) crl, out);
    }
}

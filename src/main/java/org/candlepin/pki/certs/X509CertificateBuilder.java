/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.certs;

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Provider;



/**
 * Builder for the construction of {@link X509Certificate}s.
 */
public class X509CertificateBuilder {
    private static final String SIGNATURE_ALGORITHM = "ML-DSA";
    private static final Pattern X500_SPECIAL_SYMBOL_REGEX = Pattern.compile("\\A([,=+<>#;\"])");

    private final Provider<BouncyCastleProvider> securityProvider;
    private final CertificateReader certificateAuthority;
    private final SubjectKeyIdentifierWriter subjectKeyIdentifierWriter;
    private final List<X509Extension> certExtensions;

    private DistinguishedName distinguishedName;
    private DistinguishedName subjectAltName;
    private Instant validAfter;
    private Instant validUntil;
    private KeyPair keyPair;
    private BigInteger certSerial;

    @Inject
    public X509CertificateBuilder(CertificateReader certificateAuthority,
        Provider<BouncyCastleProvider> securityProvider,
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter) {
        this.certificateAuthority = Objects.requireNonNull(certificateAuthority);
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.subjectKeyIdentifierWriter = Objects.requireNonNull(subjectKeyIdentifierWriter);

        this.certExtensions = new ArrayList<>();
    }

    public X509CertificateBuilder withDN(DistinguishedName dn) {
        this.distinguishedName = dn;
        return this;
    }

    public X509CertificateBuilder withSubjectAltName(String subjectAltName) {
        this.subjectAltName = new DistinguishedName(subjectAltName);
        return this;
    }

    public X509CertificateBuilder withValidity(Instant from, Instant to) {
        this.validAfter = from;
        this.validUntil = to;
        return this;
    }

    public X509CertificateBuilder withKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
        return this;
    }

    public X509CertificateBuilder withSerial(BigInteger serial) {
        this.certSerial = serial;
        return this;
    }

    public X509CertificateBuilder withSerial(long serial) {
        return this.withSerial(BigInteger.valueOf(serial));
    }

    public X509CertificateBuilder withRandomSerial() {
        long serial;

        // Impl note:
        // Math.abs cannot negate MIN_VALUE, so we'll generate a new value when that happens.
        do {
            serial = new SecureRandom().nextLong();
        }
        while (serial == Long.MIN_VALUE);

        return this.withSerial(serial);
    }

    public X509CertificateBuilder withExtensions(Collection<X509Extension> extensions) {
        if (extensions != null && !extensions.isEmpty()) {
            this.certExtensions.addAll(extensions);
        }

        return this;
    }

    public X509CertificateBuilder withExtensions(X509Extension... extensions) {
        return this.withExtensions(extensions != null ? Arrays.asList(extensions) : null);
    }

    public X509Certificate build() {
        this.checkMandatoryFields();

        X509Certificate caCertificate = this.certificateAuthority.getCACert();
        PublicKey clientPubKey = this.keyPair.getPublic();

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            X500Name.getInstance(caCertificate.getSubjectX500Principal().getEncoded()),
            this.certSerial,
            Date.from(this.validAfter),
            Date.from(this.validUntil),
            this.buildX500Name(this.distinguishedName),
            SubjectPublicKeyInfo.getInstance(clientPubKey.getEncoded()));

        this.addSSLCertificateType(builder);
        this.addKeyUsage(builder);
        this.addAuthorityKeyIdentifier(builder, caCertificate);
        this.addSubjectKeyIdentifier(builder, this.keyPair, this.certExtensions);
        this.addSubjectAltName(builder, this.distinguishedName, this.subjectAltName);
        this.addBasicConstraints(builder);
        this.addExtensions(builder, this.certExtensions);

        return buildCertificate(builder, this.signer());
    }

    private X500Name buildX500Name(DistinguishedName distinguishedName) {
        X500NameBuilder builder = new X500NameBuilder();

        String orgName = distinguishedName.organizationName();
        if (orgName != null && !orgName.isBlank()) {
            // Impl note:
            // RFC 2253 defines three ways of specifying the DN according to the opening character,
            // which causes our name builder to not escape the first character of our string. And
            // since our input are strings intended to be used raw, we must manually escape any
            // special opening character to avoid causing an error at this step.
            orgName = X500_SPECIAL_SYMBOL_REGEX.matcher(orgName)
                .replaceFirst("\\\\$1");

            builder.addRDN(BCStyle.O, orgName);
        }

        String commonName = distinguishedName.commonName();
        if (commonName != null && !commonName.isBlank()) {
            commonName = X500_SPECIAL_SYMBOL_REGEX.matcher(commonName)
                .replaceFirst("\\\\$1");

            builder.addRDN(BCStyle.CN, commonName);
        }

        return builder.build();
    }

    private void addSSLCertificateType(X509v3CertificateBuilder builder) {
        NetscapeCertType type = new NetscapeCertType(NetscapeCertType.sslClient | NetscapeCertType.smime);
        addExtension(builder, MiscObjectIdentifiers.netscapeCertType, false, type);
    }

    private void addKeyUsage(X509v3CertificateBuilder builder) {
        KeyUsage usage = new KeyUsage(KeyUsage.digitalSignature |
            KeyUsage.keyEncipherment | KeyUsage.dataEncipherment);
        this.addExtension(builder, Extension.keyUsage, true, usage);

        ExtendedKeyUsage exUsage = new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth);
        this.addExtension(builder, Extension.extendedKeyUsage, false, exUsage);
    }

    private void addAuthorityKeyIdentifier(X509v3CertificateBuilder builder, X509Certificate caCertificate) {
        try {
            // The AKI must only contain the issuer's key ID, due to an issue with openssl failing
            // to verify the cert if the issuer is present in the AKI, even if the issuer is valid
            // and even with the correct key ID. As such, we'll just drop the CA's pub key in here
            // instead of using the cert directly.
            AuthorityKeyIdentifier aki = extensionUtil()
                .createAuthorityKeyIdentifier(caCertificate.getPublicKey());

            builder.addExtension(Extension.authorityKeyIdentifier, false, aki.getEncoded());
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to add authority key identifier.", e);
        }
    }

    private void addSubjectKeyIdentifier(X509v3CertificateBuilder builder, KeyPair keyPair,
        List<X509Extension> extensions) {

        try {
            byte[] ski = this.subjectKeyIdentifierWriter.getSubjectKeyIdentifier(keyPair, extensions);
            builder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        }
        catch (NoSuchAlgorithmException | IOException e) {
            throw new CertificateCreationException("Failed to encode subject key identifier.", e);
        }
    }

    private void addSubjectAltName(X509v3CertificateBuilder builder,
        DistinguishedName distinguishedName, DistinguishedName subjectAltName) {

        if (subjectAltName == null) {
            return;
        }

        // Why add the certificate subject again as an alternative name?  RFC 6125 Section 6.4.4
        // stipulates that if SANs are provided, a validator MUST use them instead of the certificate
        // subject.  If no SANs are present, the RFC allows the validator to use the subject field.  So,
        // if we do have an SAN to add, we need to add the subject field again as an SAN.
        //
        // See:
        //  - http://stackoverflow.com/questions/5935369
        //  - https://tools.ietf.org/html/rfc6125#section-6.4.4

        GeneralName subject = new GeneralName(this.buildX500Name(distinguishedName));
        GeneralName name = new GeneralName(this.buildX500Name(subjectAltName));
        ASN1Encodable[] altNameArray = {subject, name};

        GeneralNames altNames = GeneralNames.getInstance(new DERSequence(altNameArray));
        this.addExtension(builder, Extension.subjectAlternativeName, false, altNames);
    }

    private void addBasicConstraints(X509v3CertificateBuilder builder) {
        addExtension(builder, Extension.basicConstraints, false, new BasicConstraints(false));
    }

    private void addExtensions(X509v3CertificateBuilder builder, Collection<X509Extension> extensions) {
        for (X509Extension extension : extensions) {
            this.addExtension(builder, extension.oid(), extension.critical(), extension.value());
        }
    }

    private X509Certificate buildCertificate(X509v3CertificateBuilder builder, ContentSigner signer) {
        try {
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        }
        catch (CertificateException e) {
            throw new CertificateCreationException("Failed to build the certificate.", e);
        }
    }

    /**
     * Checks the state of this builder, ensuring required fields have been set before attempting to
     * build the certificate.
     *
     * @throws IllegalStateException if one or more required fields have not been populated
     */
    private void checkMandatoryFields() {
        if (this.distinguishedName == null) {
            throw new IllegalStateException("distinguished name has not been set");
        }

        if (this.validAfter == null) {
            throw new IllegalStateException("validAfter/notBefore has not been set");
        }

        if (this.validUntil == null) {
            throw new IllegalStateException("validUntil/notAfter has not been set");
        }

        if (this.certSerial == null) {
            throw new IllegalStateException("certificate serial number has not been set");
        }

        if (this.keyPair == null) {
            throw new IllegalStateException("client key pair has not been set");
        }
    }

    private ContentSigner signer() {
        try {
            return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(this.securityProvider.get())
                .build(this.certificateAuthority.getCaKey());
        }
        catch (OperatorCreationException e) {
            throw new CertificateCreationException("Failed to create certificate signer.", e);
        }
    }

    private static JcaX509ExtensionUtils extensionUtil() {
        try {
            return new JcaX509ExtensionUtils();
        }
        catch (NoSuchAlgorithmException e) {
            throw new CertificateCreationException("Failed to create extension util.", e);
        }
    }

    private void addExtension(X509v3CertificateBuilder builder,
        ASN1ObjectIdentifier oid, boolean critical, ASN1Encodable value) {
        try {
            builder.addExtension(oid, critical, value);
        }
        catch (CertIOException e) {
            throw new CertificateCreationException("Failed to add an extension: %s".formatted(oid), e);
        }
    }

}

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.OID;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.test.CertificateReaderForTesting;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1UTF8String;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

class X509CertificateBuilderTest {
    private static final DistinguishedName DN = new DistinguishedName("candlepinproject.org");

    private CertificateReaderForTesting certificateAuthority;
    private X509CertificateBuilder builder;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();
        this.certificateAuthority = new CertificateReaderForTesting();
        this.builder = new X509CertificateBuilder(
            this.certificateAuthority, securityProvider, subjectKeyIdentifierWriter);
    }

    @Test
    public void testCreateX509Certificate() throws Exception {
        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = createKeyPair();

        X509Certificate cert = this.builder
            .withDN(DN)
            .withValidity(start, end)
            .withSerial(1999L)
            .withKeyPair(keyPair)
            .withSubjectAltName("altName")
            .build();

        assertEquals("SHA256withRSA", cert.getSigAlgName());
        assertEquals("1999", cert.getSerialNumber().toString());

        X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());
        Extensions bcExtensions = holder.getExtensions();

        // KeyUsage extension incorrect
        assertTrue(KeyUsage.fromExtensions(bcExtensions)
            .hasUsages(KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment));

        // ExtendedKeyUsage extension incorrect
        assertTrue(ExtendedKeyUsage
            .fromExtensions(bcExtensions).hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth));

        // Basic constraints incorrectly identify this cert as a CA
        assertFalse(BasicConstraints.fromExtensions(bcExtensions).isCA());

        NetscapeCertType expected = new NetscapeCertType(
            NetscapeCertType.sslClient | NetscapeCertType.smime);

        NetscapeCertType actual = new NetscapeCertType(
            (DERBitString) bcExtensions.getExtension(MiscObjectIdentifiers.netscapeCertType)
                .getParsedValue());

        assertArrayEquals(
            new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic()).getEncoded(),
            SubjectKeyIdentifier.fromExtensions(bcExtensions).getEncoded());

        PrivateKey key = this.certificateAuthority.getCaKey();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKeySpec ks = kf.getKeySpec(key, RSAPrivateCrtKeySpec.class);
        RSAPublicKeySpec pubKs = new RSAPublicKeySpec(ks.getModulus(), ks.getPublicExponent());
        PublicKey pubKey = kf.generatePublic(pubKs);
        assertArrayEquals(
            new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(
                this.certificateAuthority.getCACert()).getEncoded(),
            AuthorityKeyIdentifier.fromExtensions(bcExtensions).getEncoded());

        assertEquals(expected, actual);
    }

    @Test
    public void testCustomExtensions() throws Exception {
        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = createKeyPair();

        String extOid = OID.EntitlementType.namespace();
        String byteExtOid = OID.EntitlementData.namespace();
        byte[] someBytes = new byte[]{0xd, 0xe, 0xf, 0xa, 0xc, 0xe, 0xa, 0xc, 0xe};
        Set<X509Extension> extensions = Set.of(
            new X509StringExtension(extOid, "OrgLevel"),
            new X509ByteExtension(byteExtOid, someBytes)
        );

        X509Certificate cert = this.builder
            .withDN(DN)
            .withValidity(start, end)
            .withSerial(2000L)
            .withKeyPair(keyPair)
            .withSubjectAltName("altName")
            .withExtensions(extensions)
            .build();

        assertNotNull(cert.getExtensionValue(extOid));

        ASN1OctetString value = (ASN1OctetString) ASN1OctetString
            .fromByteArray(cert.getExtensionValue(extOid));
        ASN1UTF8String actual = ASN1UTF8String.getInstance(value.getOctets());
        assertEquals("OrgLevel", actual.getString());

        value = (ASN1OctetString) ASN1OctetString.fromByteArray(cert.getExtensionValue(byteExtOid));
        ASN1OctetString actualBytes = ASN1OctetString.getInstance(value.getOctets());
        assertArrayEquals(someBytes, actualBytes.getOctets());
    }

    private KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(4096);
            return generator.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}

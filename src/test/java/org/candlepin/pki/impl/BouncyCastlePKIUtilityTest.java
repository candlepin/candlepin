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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.util.OIDUtil;

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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;


public class BouncyCastlePKIUtilityTest {

    private KeyPair subjectKeyPair;
    private Configuration config;

    private CertificateReader certificateReader;
    private SubjectKeyIdentifierWriter skiWriter;

    private KeyPairDataCurator mockKeyPairDataCurator;

    @BeforeEach
    public void setUp() throws Exception {
        this.config = TestConfig.defaults();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        this.subjectKeyPair = generator.generateKeyPair();

        certificateReader = new CertificateReaderForTesting();
        skiWriter = new BouncyCastleSubjectKeyIdentifierWriter();

        this.mockKeyPairDataCurator = mock(KeyPairDataCurator.class);
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).merge(any());
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).create(any());
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).create(any(), anyBoolean());
    }

    private BouncyCastlePKIUtility buildBCPKIUtility() {
        return new BouncyCastlePKIUtility(new BouncyCastleSecurityProvider(), this.certificateReader,
            this.skiWriter, this.config, this.mockKeyPairDataCurator);
    }

    @Test
    public void testCreateX509Certificate() throws Exception {
        BouncyCastlePKIUtility pki = this.buildBCPKIUtility();

        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());

        X509Certificate cert = pki.createX509Certificate(buildDN(), null, null, start,
            end, subjectKeyPair, BigInteger.valueOf(1999L), "altName");

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
            new JcaX509ExtensionUtils().createSubjectKeyIdentifier(subjectKeyPair.getPublic()).getEncoded(),
            SubjectKeyIdentifier.fromExtensions(bcExtensions).getEncoded());

        PrivateKey key = this.certificateReader.getCaKey();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKeySpec ks = kf.getKeySpec(key, RSAPrivateCrtKeySpec.class);
        RSAPublicKeySpec pubKs = new RSAPublicKeySpec(ks.getModulus(), ks.getPublicExponent());
        PublicKey pubKey = kf.generatePublic(pubKs);
        assertArrayEquals(
            new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(
                this.certificateReader.getCACert()).getEncoded(),
            AuthorityKeyIdentifier.fromExtensions(bcExtensions).getEncoded());

        assertEquals(expected, actual);
    }

    @Test
    public void testCustomExtensions() throws Exception {
        BouncyCastlePKIUtility pki = this.buildBCPKIUtility();

        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());

        String extOid = OIDUtil.getOid(OIDUtil.Namespace.ENTITLEMENT_TYPE);
        X509ExtensionWrapper typeExtension = new X509ExtensionWrapper(extOid, false, "OrgLevel");
        Set<X509ExtensionWrapper> exts = Set.of(typeExtension);

        String byteExtOid = OIDUtil.getOid(OIDUtil.Namespace.ENTITLEMENT_DATA);
        byte[] someBytes = new byte[]{0xd, 0xe, 0xf, 0xa, 0xc, 0xe, 0xa, 0xc, 0xe};
        X509ByteExtensionWrapper byteExtension = new X509ByteExtensionWrapper(byteExtOid, false, someBytes);
        Set<X509ByteExtensionWrapper> byteExtensions = Set.of(byteExtension);

        X509Certificate cert = pki.createX509Certificate(buildDN(), exts,
            byteExtensions, start, end, subjectKeyPair, BigInteger.valueOf(2000L), "altName");

        assertNotNull(cert.getExtensionValue(extOid));

        ASN1OctetString value = (ASN1OctetString) ASN1OctetString
            .fromByteArray(cert.getExtensionValue(extOid));
        ASN1UTF8String actual = ASN1UTF8String.getInstance(value.getOctets());
        assertEquals("OrgLevel", actual.getString());

        value = (ASN1OctetString) ASN1OctetString.fromByteArray(cert.getExtensionValue(byteExtOid));
        ASN1OctetString actualBytes = ASN1OctetString.getInstance(value.getOctets());
        assertArrayEquals(someBytes, actualBytes.getOctets());
    }

    private DistinguishedName buildDN() {
        return new DistinguishedName("candlepinproject.org");
    }

}

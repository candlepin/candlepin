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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.util.OIDUtil;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERUTF8String;
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
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.KeyIdentifier;
import org.mozilla.jss.netscape.security.x509.PKIXExtensions;
import org.mozilla.jss.netscape.security.x509.X500Name;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CRLReason;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;


/** Unit tests for JSSPKIUtility */
public class JSSPKIUtilityTest {

    private Injector injector;
    private KeyPair subjectKeyPair;
    private Configuration config;

    @Inject private JSSPKIUtility jssUtil;

    @BeforeEach
    public void setUp() throws Exception {
        JSSProviderLoader.addProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        subjectKeyPair = generator.generateKeyPair();
        config = new CandlepinCommonTestConfig();

        Module pkiModule = binder -> {
            binder.bind(Configuration.class).toInstance(config);
            binder.bind(CertificateReader.class).to(CertificateReaderForTesting.class);
            binder.bind(SubjectKeyIdentifierWriter.class).to(DefaultSubjectKeyIdentifierWriter.class);
            binder.bind(JSSPKIUtility.class);
            binder.bind(PrivateKeyReader.class).to(JSSPrivateKeyReader.class);
        };
        injector = Guice.createInjector(pkiModule);
        injector.injectMembers(this);
    }

    /**
     * Assert two dates are equal with variance allowed within a given ChronoUnit.  E.g. Two instants that are
     * only milliseconds apart within the same second would pass the assertion if ChronoUnit.SECONDS (or
     * higher) was passed in.  For practical purposes, ChronoUnit.HOURS is as low as you'd want to go since
     * using ChronoUnit.MINUTES can result in sporadic failures when the two times straddle the 60th second
     * of a minute.  The same can happen with ChronoUnit.HOURS but much more rarely.
     * @param expected expected value
     * @param actual actual value
     * @param fuzz threshold of variance to allow
     */
    public void assertDatesMatch(Date expected, Date actual, ChronoUnit fuzz) {
        ZonedDateTime zonedExpected = expected
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .truncatedTo(fuzz);

        ZonedDateTime zonedActual = actual
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .truncatedTo(fuzz);

        assertEquals(zonedExpected, zonedActual);
    }

    @Test
    public void testCreateX509Certificate() throws Exception {
        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());
        X509Certificate cert = jssUtil.createX509Certificate("cn=candlepinproject.org", null, null, start,
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
            (DERBitString) bcExtensions.getExtension(MiscObjectIdentifiers.netscapeCertType).getParsedValue()
        );

        assertArrayEquals(
            new JcaX509ExtensionUtils().createSubjectKeyIdentifier(subjectKeyPair.getPublic()).getEncoded(),
            SubjectKeyIdentifier.fromExtensions(bcExtensions).getEncoded()
        );

        CertificateReader reader = injector.getInstance(CertificateReader.class);
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) reader.getCaKey();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKeySpec ks = kf.getKeySpec(key, RSAPrivateCrtKeySpec.class);
        RSAPublicKeySpec pubKs = new RSAPublicKeySpec(ks.getModulus(), ks.getPublicExponent());
        PublicKey pubKey = kf.generatePublic(pubKs);
        assertArrayEquals(
            new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(pubKey).getEncoded(),
            AuthorityKeyIdentifier.fromExtensions(bcExtensions).getEncoded()
        );

        assertEquals(expected, actual);
    }

    @Test
    public void testCustomExtensions() throws Exception {
        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());

        String extOid =
            OIDUtil.REDHAT_OID + "." + OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_TYPE_KEY);
        X509ExtensionWrapper typeExtension = new X509ExtensionWrapper(extOid, false, "OrgLevel");
        Set<X509ExtensionWrapper> exts = new LinkedHashSet<>();
        exts.add(typeExtension);

        String byteExtOid =
            OIDUtil.REDHAT_OID + "." + OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY);
        byte[] someBytes = new byte[] { 0xd, 0xe, 0xf, 0xa, 0xc, 0xe, 0xa, 0xc, 0xe };
        X509ByteExtensionWrapper byeExtension = new X509ByteExtensionWrapper(byteExtOid, false, someBytes);
        Set<X509ByteExtensionWrapper> byteExtensions = new LinkedHashSet<>();
        byteExtensions.add(byeExtension);

        X509Certificate cert = jssUtil.createX509Certificate("cn=candlepinproject.org", exts,
            byteExtensions, start, end, subjectKeyPair, BigInteger.valueOf(2000L), "altName");

        ASN1OctetString value =
            (ASN1OctetString) ASN1OctetString.fromByteArray(cert.getExtensionValue(extOid));
        DERUTF8String actual = DERUTF8String.getInstance(value.getOctets());
        assertEquals("OrgLevel", actual.getString());

        value = (ASN1OctetString) ASN1OctetString.fromByteArray(cert.getExtensionValue(byteExtOid));
        ASN1OctetString actualBytes = ASN1OctetString.getInstance(value.getOctets());
        assertArrayEquals(someBytes, actualBytes.getOctets());
    }

    @Test
    public void testCreateCRL() throws Exception {
        Date start = new Date();
        int deltaDays = 400;
        config.setProperty(ConfigProperties.CRL_NEXT_UPDATE_DELTA, String.valueOf(deltaDays));

        List<X509CRLEntryWrapper> entries = new ArrayList<>();

        for (long i = 0L; i < 10L; i++) {
            entries.add(new X509CRLEntryWrapper(BigInteger.valueOf(i), new Date()));
        }

        X509CRL crl = jssUtil.createX509CRL(entries, BigInteger.ONE);

        // Assertions below
        byte[] extValue = crl.getExtensionValue(PKIXExtensions.CRLNumber_Id.toString());

        // All extension values are encoded as octet strings so you need to unwrap them
        ASN1OctetString extOctet = (ASN1OctetString) ASN1OctetString.fromByteArray(extValue);
        ASN1Integer crlNumber = (ASN1Integer) ASN1Integer.fromByteArray(extOctet.getOctets());
        assertEquals(BigInteger.ONE, crlNumber.getValue());

        assertEquals("Test CA", new X500Name(crl.getIssuerX500Principal().getEncoded()).getCommonName());
        assertEquals(2, crl.getVersion());
        this.assertDatesMatch(start, crl.getThisUpdate(), ChronoUnit.HOURS);

        Date expectedUpdate = Date.from(
            start.toInstant().atZone(ZoneId.systemDefault()).plusDays(deltaDays).toInstant()
        );

        // CRLs don't store times with millisecond precision.  The method just ignores any difference
        // between the seconds and millisecond values of the Dates. I also ignore minutes because it can
        // lead to false failures if your test run spans two minutes.
        this.assertDatesMatch(expectedUpdate, crl.getNextUpdate(), ChronoUnit.HOURS);

        for (long i = 0L; i < 10L; i++) {
            X509CRLEntry entry = crl.getRevokedCertificate(BigInteger.valueOf(i));
            assertEquals(entry.getRevocationReason(), CRLReason.PRIVILEGE_WITHDRAWN);
        }

        byte[] extensionValue = crl.getExtensionValue(PKIXExtensions.AuthorityKey_Id.toString());
        ASN1OctetString akiOc = ASN1OctetString.getInstance(extensionValue);
        AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(akiOc.getOctets());

        CertificateReader reader = injector.getInstance(CertificateReader.class);
        X509Certificate ca = reader.getCACert();
        byte[] caExtensionValue = ca.getExtensionValue(PKIXExtensions.SubjectKey_Id.toString());
        ASN1OctetString skiOc = ASN1OctetString.getInstance(caExtensionValue);
        SubjectKeyIdentifier ski = SubjectKeyIdentifier.getInstance(skiOc.getOctets());

        assertArrayEquals(ski.getKeyIdentifier(), aki.getKeyIdentifier());
    }

    @Test
    public void testWritePemKey() throws Exception {
        CertificateReader reader = injector.getInstance(CertificateReader.class);
        RSAPrivateKey key = reader.getCaKey();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        jssUtil.writePemEncoded(key, os);

        String actual = new String(os.toByteArray(), Charsets.UTF_8);

        // Compare against what we started with
        CertificateReaderForTesting testingReader = (CertificateReaderForTesting) reader;
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(testingReader.getKeyFileName());
        String expected = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

        assertEquals(expected, actual);

        // Compare with what BouncyCastle does
        StringWriter bcExpected = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(bcExpected)) {
            pemWriter.writeObject(key);
        }

        assertEquals(bcExpected.toString(), actual);
    }

    @Test
    public void testWritePemEncodedCert() throws Exception {
        CertificateReader reader = injector.getInstance(CertificateReader.class);
        X509Certificate ca = reader.getCACert();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        jssUtil.writePemEncoded(ca, os);

        String actual = new String(os.toByteArray(), Charsets.UTF_8);

        // Compare against what we started with
        CertificateReaderForTesting testingReader = (CertificateReaderForTesting) reader;
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(testingReader.getCAFileName());
        String expected = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

        assertEquals(expected, actual);

        // Compare with what BouncyCastle does
        StringWriter bcExpected = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(bcExpected)) {
            pemWriter.writeObject(ca);
        }

        assertEquals(bcExpected.toString(), actual);
    }

    @Test
    public void testWritePemEncodedCrl() throws Exception {
        InputStream referenceStream = this.getClass().getClassLoader().getResourceAsStream("crl.pem");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509CRL crl = (X509CRL) cf.generateCRL(referenceStream);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        jssUtil.writePemEncoded(crl, os);

        String actual = new String(os.toByteArray(), Charsets.UTF_8);

        // Compare against what we started with
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("crl.pem");
        String expected = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

        assertEquals(expected, actual);

        // Compare with what BouncyCastle does
        StringWriter bcExpected = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(bcExpected)) {
            pemWriter.writeObject(crl);
        }

        assertEquals(bcExpected.toString(), actual);
    }

    @Test
    public void testCalculateAuthorityKeyIdentifier() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        RSAPublicKey key = (RSAPublicKey) gen.generateKeyPair().getPublic();

        AuthorityKeyIdentifier expectedAki = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(key);
        AuthorityKeyIdentifierExtension actualAki = JSSPKIUtility.buildAuthorityKeyIdentifier(key);

        byte[] expectedKeyIdentifier = expectedAki.getKeyIdentifier();
        byte[] actualKeyIdentifier = ((KeyIdentifier) actualAki.get(AuthorityKeyIdentifierExtension.KEY_ID))
            .getIdentifier();

        assertArrayEquals(expectedKeyIdentifier, actualKeyIdentifier);
    }
}

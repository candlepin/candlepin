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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Configuration;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.util.OIDUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.KeyIdentifier;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;



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

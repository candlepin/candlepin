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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.candlepin.TestingModules;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.OIDUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.KeyIdentifier;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import javax.inject.Inject;


public class JSSPKIUtilityTest {

    private Injector injector;
    private KeyPair subjectKeyPair;
    private Configuration config;

    @Inject private CertificateReader certificateReader;
    @Inject private SubjectKeyIdentifierWriter skiWriter;

    private KeyPairDataCurator mockKeyPairDataCurator;


    @BeforeEach
    public void setUp() throws Exception {
        JSSProviderLoader.initialize();
        this.config = TestConfig.defaults();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        this.subjectKeyPair = generator.generateKeyPair();

        this.injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.StandardTest(config),
            new TestingModules.ServletEnvironmentModule()
        );
        this.injector.injectMembers(this);

        this.mockKeyPairDataCurator = mock(KeyPairDataCurator.class);
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).merge(any());
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).create(any());
        doAnswer(returnsFirstArg()).when(this.mockKeyPairDataCurator).create(any(), anyBoolean());
    }

    private JSSPKIUtility buildJSSPKIUtility() {
        return new JSSPKIUtility(this.certificateReader, this.skiWriter, this.config,
            this.mockKeyPairDataCurator);
    }

    @Test
    public void testCreateX509Certificate() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());

        X509Certificate cert = pki.createX509Certificate("cn=candlepinproject.org", null, null, start,
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
        PrivateKey key = reader.getCaKey();
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
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        Date start = new Date();
        Date end = Date.from(LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant());

        String extOid =
            OIDUtil.REDHAT_OID + "." + OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_TYPE_KEY);
        X509ExtensionWrapper typeExtension = new X509ExtensionWrapper(extOid, false, "OrgLevel");
        Set<X509ExtensionWrapper> exts = Set.of(typeExtension);

        String byteExtOid =
            OIDUtil.REDHAT_OID + "." + OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_DATA_KEY);
        byte[] someBytes = new byte[] { 0xd, 0xe, 0xf, 0xa, 0xc, 0xe, 0xa, 0xc, 0xe };
        X509ByteExtensionWrapper byteExtension = new X509ByteExtensionWrapper(byteExtOid, false, someBytes);
        Set<X509ByteExtensionWrapper> byteExtensions = Set.of(byteExtension);

        X509Certificate cert = pki.createX509Certificate("cn=candlepinproject.org", exts,
            byteExtensions, start, end, subjectKeyPair, BigInteger.valueOf(2000L), "altName");

        assertNotNull(cert.getExtensionValue(extOid));

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

        AuthorityKeyIdentifier expectedAki = new JcaX509ExtensionUtils(
            new SHA256DigestCalculator(MessageDigest.getInstance("SHA-256")))
            .createAuthorityKeyIdentifier(key);
        AuthorityKeyIdentifierExtension actualAki = JSSPKIUtility.buildAuthorityKeyIdentifier(key);

        byte[] expectedKeyIdentifier = expectedAki.getKeyIdentifier();
        byte[] actualKeyIdentifier = ((KeyIdentifier) actualAki.get(AuthorityKeyIdentifierExtension.KEY_ID))
            .getIdentifier();

        assertArrayEquals(expectedKeyIdentifier, actualKeyIdentifier);
    }

    @Test
    public void testGenerateKeyPair() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        KeyPair keypair = pki.generateKeyPair();
        assertNotNull(keypair);

        // The keys returned in the key pair *must* be extractable
        PublicKey publicKey = keypair.getPublic();
        assertNotNull(publicKey);
        assertNotNull(publicKey.getFormat());
        assertNotNull(publicKey.getEncoded());

        PrivateKey privateKey = keypair.getPrivate();
        assertNotNull(privateKey);
        assertNotNull(privateKey.getFormat());
        assertNotNull(privateKey.getEncoded());
    }

    @Test
    public void testGetConsumerKeyPair() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        Consumer consumer = new Consumer();
        assertNull(consumer.getKeyPairData());

        KeyPair keypair = pki.getConsumerKeyPair(consumer);
        assertNotNull(keypair);

        // The keys returned in the key pair *must* be extractable
        PublicKey publicKey = keypair.getPublic();
        assertNotNull(publicKey);
        assertNotNull(publicKey.getFormat());
        assertNotNull(publicKey.getEncoded());

        PrivateKey privateKey = keypair.getPrivate();
        assertNotNull(privateKey);
        assertNotNull(privateKey.getFormat());
        assertNotNull(privateKey.getEncoded());

        // The encoding of the returned keys should match what we store in the consumer
        KeyPairData kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertEquals(publicKey.getEncoded(), kpdata.getPublicKeyData());
        assertEquals(privateKey.getEncoded(), kpdata.getPrivateKeyData());
    }

    @Test
    public void testGetConsumerKeyPairRepeatsOutputForConsumer() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        Consumer consumer = new Consumer();
        assertNull(consumer.getKeyPairData());

        KeyPair keypairA = pki.getConsumerKeyPair(consumer);
        assertNotNull(keypairA);
        assertNotNull(keypairA.getPublic());
        assertNotNull(keypairA.getPrivate());

        KeyPairData kpdataA = consumer.getKeyPairData();
        assertNotNull(kpdataA);

        KeyPair keypairB = pki.getConsumerKeyPair(consumer);
        assertNotNull(keypairB);
        assertNotNull(keypairB.getPublic());
        assertNotNull(keypairB.getPrivate());

        KeyPairData kpdataB = consumer.getKeyPairData();
        assertNotNull(kpdataB);

        assertNotSame(keypairA, keypairB);

        // The keypair coming out should be the same, since it shouldn't have required
        // regeneration
        assertTrue(Arrays.equals(keypairA.getPublic().getEncoded(), keypairB.getPublic().getEncoded()));
        assertTrue(Arrays.equals(keypairA.getPrivate().getEncoded(), keypairB.getPrivate().getEncoded()));
    }

    private byte[] serializeObject(Object key) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream ostream = new ObjectOutputStream(baos);

        ostream.writeObject(key);
        ostream.flush();
        ostream.close();

        return baos.toByteArray();
    }

    @Test
    public void testGetConsumerKeyPairConvertsLegacySerializedKeyPairs() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        KeyPair keypair = pki.generateKeyPair();
        byte[] jsoPubKeyBytes = this.serializeObject(keypair.getPublic());
        byte[] jsoPrivKeyBytes = this.serializeObject(keypair.getPrivate());

        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(jsoPubKeyBytes)
            .setPrivateKeyData(jsoPrivKeyBytes);

        Consumer consumer = new Consumer()
            .setKeyPairData(kpdata);

        KeyPair converted = pki.getConsumerKeyPair(consumer);
        assertNotNull(converted);

        // The key pair data should differ, since it should be converted to the newer format,
        // but the keys themselves should remain unchanged
        kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertFalse(Arrays.equals(jsoPubKeyBytes, kpdata.getPublicKeyData()));
        assertFalse(Arrays.equals(jsoPrivKeyBytes, kpdata.getPrivateKeyData()));
        assertTrue(Arrays.equals(keypair.getPublic().getEncoded(), converted.getPublic().getEncoded()));
        assertTrue(Arrays.equals(keypair.getPrivate().getEncoded(), converted.getPrivate().getEncoded()));

        // The converted key pair data should match the encoding of the keys
        assertTrue(Arrays.equals(keypair.getPublic().getEncoded(), kpdata.getPublicKeyData()));
        assertTrue(Arrays.equals(keypair.getPrivate().getEncoded(), kpdata.getPrivateKeyData()));
    }

    @Test
    public void testGetConsumerKeyPairRegneratesMalformedKeyPairs() throws Exception {
        JSSPKIUtility pki = this.buildJSSPKIUtility();

        byte[] pubKeyBytes = "bad_public_key".getBytes();
        byte[] privKeyBytes = "bad_private_key".getBytes();

        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(pubKeyBytes)
            .setPrivateKeyData(privKeyBytes);

        Consumer consumer = new Consumer()
            .setKeyPairData(kpdata);

        KeyPair keypair = pki.getConsumerKeyPair(consumer);
        assertNotNull(keypair);
        assertNotNull(keypair.getPublic());
        assertNotNull(keypair.getPrivate());

        // Ensure the kpdata for this consumer has been updated to match the newly
        // generated key pair
        kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertFalse(Arrays.equals(pubKeyBytes, kpdata.getPublicKeyData()));
        assertFalse(Arrays.equals(privKeyBytes, kpdata.getPrivateKeyData()));

        assertTrue(Arrays.equals(keypair.getPublic().getEncoded(), kpdata.getPublicKeyData()));
        assertTrue(Arrays.equals(keypair.getPrivate().getEncoded(), kpdata.getPrivateKeyData()));
    }

    private static class SHA256DigestCalculator
        implements DigestCalculator {
        private ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        private MessageDigest digest;

        public SHA256DigestCalculator(MessageDigest digest) {
            this.digest = digest;
        }

        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption);
        }

        public OutputStream getOutputStream() {
            return bOut;
        }

        public byte[] getDigest() {
            byte[] bytes = digest.digest(bOut.toByteArray());
            bOut.reset();
            return bytes;
        }
    }
}

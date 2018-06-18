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
package org.candlepin.util;

import static org.candlepin.test.MatchesPattern.*;
import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.hamcrest.number.OrderingComparison;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Test implementations of X509CRLStreamWriter
 */
@RunWith(Parameterized.class)
public class X509CRLStreamWriterTest {
    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Parameterized.Parameters
    public static Iterable<Class> data() {
        return Arrays.asList(BouncyCastleX509CRLStreamWriter.class, JSSX509CRLStreamWriter.class);
    }

    private X500Name issuer;
    private ContentSigner signer;
    private KeyPair keyPair;
    private File outfile;

    private KeyPairGenerator generator;

    private final Constructor<? extends X509CRLStreamWriter> fileConstructor;
    private final Constructor<? extends X509CRLStreamWriter> streamConstructor;

    public X509CRLStreamWriterTest(Class<? extends X509CRLStreamWriter> clazz) throws NoSuchMethodException {
        this.fileConstructor = clazz.getConstructor(File.class, RSAPrivateKey.class, RSAPublicKey.class);
        this.streamConstructor = clazz.getConstructor(InputStream.class, RSAPrivateKey.class,
            RSAPublicKey.class);
    }

    @Before
    public void setUp() throws Exception {
        issuer = new X500Name("CN=Test Issuer");

        generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(keyPair.getPrivate());

        outfile = new File(folder.getRoot(), "new.crl");
        Security.addProvider(BC_PROVIDER);
    }

    private X509v2CRLBuilder createCRLBuilder() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier
            (keyPair.getPublic());
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, identifier);

        /* With a CRL number of 127, incrementing it should cause the number of bytes in the length
         * portion of the TLV to increase by one.*/
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));
        crlBuilder.addCRLEntry(new BigInteger("100"), new Date(), CRLReason.privilegeWithdrawn);
        return crlBuilder;
    }

    private X509CRLHolder createCRL() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        return crlBuilder.build(signer);
    }

    private File writeCRL(X509CRLHolder crl) throws Exception {
        File crlToChange = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(crlToChange, crl.getEncoded());
        return crlToChange;
    }

    private X509CRL readCRL() throws Exception {
        return readCRL(keyPair.getPublic());
    }

    private X509CRL readCRL(PublicKey signatureKey) throws Exception {
        // We could return a X509CRLHolder but that class isn't as fully featured as the built in
        // X509CRL.
        InputStream changedStream = new BufferedInputStream(new FileInputStream(outfile));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509CRL changedCrl = (X509CRL) cf.generateCRL(changedStream);
        changedCrl.verify(signatureKey, BouncyCastleProvider.PROVIDER_NAME);

        return changedCrl;
    }

    @Test
    public void testHandlesExtensions() throws Exception {
        File crlToChange = writeCRL(createCRL());

        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<>();
        expected.add(new BigInteger("100"));
        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToActualCRL() throws Exception {
        ClassLoader classLoader = this.getClass().getClassLoader();
        InputStream crl = classLoader.getResourceAsStream("real-crl.der");
        InputStream keyStream = classLoader.getResourceAsStream("real.key");

        InputStreamReader keyReader = new InputStreamReader(keyStream);

        try (PEMParser reader = new PEMParser(keyReader)) {

            Object pemObj = reader.readObject();
            if (pemObj == null) {
                crl.close();
                throw new RuntimeException("Reading CA private key failed");
            }

            if (pemObj instanceof PEMKeyPair) {
                keyPair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) pemObj);
            }
            else {
                crl.close();
                throw new RuntimeException("Unexpected CA key object: " + pemObj.getClass().getName());
            }
        }

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = streamConstructor.newInstance(
            crl, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
        }

        // Since we have to walk the stream twice, we need two streams!
        stream.preScan(classLoader.getResourceAsStream("real-crl.der")).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertTrue(discoveredSerials.containsAll(newSerials));
    }

    @Test
    public void testAddEntryToBigCRL() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier
            (keyPair.getPublic());
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, identifier);

        /* With a CRL number of 127, incrementing it should cause the number of bytes in the length
         * portion of the TLV to increase by one.*/
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));

        BigInteger serial = new BigInteger("741696FE9E30AD27", 16);
        Set<BigInteger> expected = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            serial = serial.add(BigInteger.TEN);
            crlBuilder.addCRLEntry(serial, new Date(), CRLReason.privilegeWithdrawn);
            expected.add(serial);
        }

        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
            expected.add(i);
        }

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToCRL() throws Exception {
        File crlToChange = writeCRL(createCRL());

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        Set<BigInteger> expected = new HashSet<>();
        expected.add(new BigInteger("100"));

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
            expected.add(i);
        }

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToCRLWithNoExtensions() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        crlBuilder.addCRLEntry(new BigInteger("100"), new Date(), CRLReason.privilegeWithdrawn);
        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        Set<BigInteger> expected = new HashSet<>();
        expected.add(new BigInteger("100"));

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
            expected.add(i);
        }

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToEmptyCRL() throws Exception {
        Date oneHourAgo = new Date(new Date().getTime() - 60L * 60L * 1000L);
        Date oneHourHence = new Date(new Date().getTime() + 60L * 60L * 1000L);

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, oneHourAgo);
        AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier
            (keyPair.getPublic());
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, identifier);

        /* With a CRL number of 127, incrementing it should cause the number of bytes in the length
         * portion of the TLV to increase by one.*/
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));
        crlBuilder.setNextUpdate(oneHourHence);
        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
        }

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        X509CRL originalCrl = new JcaX509CRLConverter().setProvider(BC_PROVIDER).getCRL(holder);

        assertNotNull(changedCrl.getNextUpdate());

        long changedCrlUpdateDelta =
            changedCrl.getNextUpdate().getTime() - changedCrl.getThisUpdate().getTime();
        long expectedUpdateDelta = oneHourHence.getTime() - oneHourAgo.getTime();

        // Allow for a little fuzz between the expected and actual value due to timing issues that can occur
        assertThat(Math.abs(changedCrlUpdateDelta - expectedUpdateDelta),
            OrderingComparison.lessThanOrEqualTo(5L));
        assertThat(changedCrl.getThisUpdate(), OrderingComparison.greaterThan(originalCrl.getThisUpdate()));

        assertEquals(newSerials, discoveredSerials);
        assertEquals(originalCrl.getIssuerX500Principal(), changedCrl.getIssuerX500Principal());

        ASN1ObjectIdentifier crlNumberOID = Extension.cRLNumber;
        byte[] oldCrlNumberBytes = originalCrl.getExtensionValue(crlNumberOID.getId());
        byte[] newCrlNumberBytes = changedCrl.getExtensionValue(crlNumberOID.getId());

        DEROctetString oldOctet = (DEROctetString) DERTaggedObject.fromByteArray(oldCrlNumberBytes);
        DEROctetString newOctet = (DEROctetString) DERTaggedObject.fromByteArray(newCrlNumberBytes);
        ASN1Integer oldNumber = (ASN1Integer) DERTaggedObject.fromByteArray(oldOctet.getOctets());
        ASN1Integer newNumber = (ASN1Integer) DERTaggedObject.fromByteArray(newOctet.getOctets());
        assertEquals(oldNumber.getValue().add(BigInteger.ONE), newNumber.getValue());

        ASN1ObjectIdentifier authorityKeyOID = Extension.authorityKeyIdentifier;
        byte[] oldAuthorityKeyId = originalCrl.getExtensionValue(authorityKeyOID.getId());
        byte[] newAuthorityKeyId = changedCrl.getExtensionValue(authorityKeyOID.getId());
        assertArrayEquals(oldAuthorityKeyId, newAuthorityKeyId);
    }

    @Test
    public void testAddEntryToEmptyCRLWithNoExtensions() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = fileConstructor.newInstance(
            crlToChange, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<>(Arrays.asList(
            new BigInteger("2358215310"),
            new BigInteger("7231352433"),
            new BigInteger("8233181205"),
            new BigInteger("1455615868"),
            new BigInteger("4323487764"),
            new BigInteger("6673256679")
        ));

        for (BigInteger i : newSerials) {
            stream.add(i, new Date(), CRLReason.privilegeWithdrawn);
        }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(matchesPattern("v1.*"));

        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();
    }

    @Test
    public void testKeySizeChange() throws Exception {
        int[] sizes = { 1024, 4096 };

        for (int size : sizes) {
            X509CRLHolder holder = createCRL();
            File crlToChange = writeCRL(holder);

            generator.initialize(size);
            KeyPair differentKeyPair = generator.generateKeyPair();

            X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
                (RSAPrivateKey) differentKeyPair.getPrivate(), (RSAPublicKey) differentKeyPair.getPublic());
            stream.preScan(crlToChange).lock();
            OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
            stream.write(o);
            o.close();

            X509CRL originalCrl = new JcaX509CRLConverter().setProvider(BC_PROVIDER).getCRL(holder);
            X509CRL changedCrl = readCRL(differentKeyPair.getPublic());

            Set<BigInteger> discoveredSerials = new HashSet<>();

            for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
                discoveredSerials.add(entry.getSerialNumber());
            }

            Set<BigInteger> expected = new HashSet<>();
            expected.add(new BigInteger("100"));
            assertEquals(expected, discoveredSerials);

            // Since the key changed, the authorityKeyIdentifier must change
            byte[] oldAkiBytes = originalCrl.getExtensionValue(Extension.authorityKeyIdentifier.getId());
            byte[] newAkiBytes = changedCrl.getExtensionValue(Extension.authorityKeyIdentifier.getId());
            oldAkiBytes = ASN1OctetString.getInstance(oldAkiBytes).getOctets();
            newAkiBytes = ASN1OctetString.getInstance(newAkiBytes).getOctets();

            AuthorityKeyIdentifier oldAki = AuthorityKeyIdentifier.getInstance(oldAkiBytes);
            AuthorityKeyIdentifier newAki = AuthorityKeyIdentifier.getInstance(newAkiBytes);

            AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils()
                .createAuthorityKeyIdentifier(keyPair.getPublic());

            assertEquals(oldAki, identifier);

            AuthorityKeyIdentifier differentIdentifier = new JcaX509ExtensionUtils()
                .createAuthorityKeyIdentifier(differentKeyPair.getPublic());

            assertEquals(newAki, differentIdentifier);
        }
    }

    @Test
    public void testIncrementsExtensions() throws Exception {
        X509CRLHolder holder = createCRL();
        File crlToChange = writeCRL(holder);

        Extension e = holder.getExtension(Extension.cRLNumber);
        ASN1Integer original = (ASN1Integer) e.getParsedValue().toASN1Primitive();

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        byte[] val = changedCrl.getExtensionValue(Extension.cRLNumber.getId());
        DEROctetString s = (DEROctetString) DERTaggedObject.fromByteArray(val);
        ASN1Integer actual = (ASN1Integer) DERTaggedObject.fromByteArray(s.getOctets());

        assertEquals(original.getPositiveValue().add(BigInteger.ONE), actual.getValue());
    }

    @Test
    public void testDeleteEntryFromCRL() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        crlBuilder.addCRLEntry(new BigInteger("101"), new Date(), CRLReason.privilegeWithdrawn);
        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        CRLEntryValidator validator = new CRLEntryValidator() {
            @Override
            public boolean shouldDelete(X509CRLEntry entry) {
                return entry.getSerialNumber().equals(new BigInteger("101"));
            }
        };

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.add(new BigInteger("9000"), new Date(), CRLReason.privilegeWithdrawn);
        stream.preScan(crlToChange, validator).lock();

        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<>();
        expected.add(new BigInteger("100"));
        expected.add(new BigInteger("9000"));

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testModifyUpdatedTime() throws Exception {
        X509CRLHolder holder = createCRL();
        File crlToChange = writeCRL(holder);

        Thread.sleep(1000);

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();
        X509CRL originalCrl = new JcaX509CRLConverter().setProvider(BC_PROVIDER).getCRL(holder);

        assertTrue("Error: CRL thisUpdate field unmodified", originalCrl.getThisUpdate()
            .before(changedCrl.getThisUpdate()));
    }

    @Test
    public void testModifyNextUpdateTime() throws Exception {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 1);
        Date nextUpdate = c.getTime();

        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        crlBuilder.setNextUpdate(nextUpdate);
        X509CRLHolder holder = crlBuilder.build(signer);

        File crlToChange = writeCRL(holder);

        Thread.sleep(1000);

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();
        X509CRL originalCrl = new JcaX509CRLConverter().setProvider(BC_PROVIDER).getCRL(holder);

        assertTrue("Error: CRL nextUpdate field unmodified",
            originalCrl.getNextUpdate().before(changedCrl.getNextUpdate()));
    }

    @Test
    public void testSignatureKeyChange() throws Exception {
        KeyPair differentKeyPair = generator.generateKeyPair();

        ContentSigner otherSigner = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(differentKeyPair.getPrivate());

        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        X509CRLHolder holder = crlBuilder.build(otherSigner);

        File crlToChange = writeCRL(holder);

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        // No SignatureException should be thrown
        readCRL();
    }

    @Test
    public void testUnlockedThrowsException() throws Exception {
        File crlToChange = writeCRL(createCRL());

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.add(new BigInteger("9000"), new Date(), 0);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
            matchesPattern("The instance must be.*locked.*"));

        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.preScan(crlToChange);
        stream.write(o);
        o.close();
    }

    @Test
    public void testUnscannedThrowsException() throws Exception {
        File crlToChange = writeCRL(createCRL());

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.add(new BigInteger("9000"), new Date(), CRLReason.privilegeWithdrawn);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
            matchesPattern("The instance must be.*locked.*"));

        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.lock();
        stream.write(o);
        o.close();
    }

    @Test
    public void testUpgradesSignature() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();

        String signingAlg = "SHA1withRSA";
        ContentSigner sha1Signer = new JcaContentSignerBuilder(signingAlg)
            .setProvider(BC_PROVIDER)
            .build(keyPair.getPrivate());

        X509CRLHolder holder = crlBuilder.build(sha1Signer);

        File crlToChange = writeCRL(holder);

        X509CRLStreamWriter stream = fileConstructor.newInstance(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        stream.setSigningAlgorithm("SHA256withRSA");
        stream.add(new BigInteger("9000"), new Date(), CRLReason.privilegeWithdrawn);
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<>();
        expected.add(new BigInteger("100"));
        expected.add(new BigInteger("9000"));

        assertEquals(expected, discoveredSerials);
    }
}

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

import static org.candlepin.test.MatchesPattern.matchesPattern;
import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.provider.X509CRLEntryObject;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class X509CRLStreamWriterTest {
    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private X500Name issuer;
    private ContentSigner signer;
    private KeyPair keyPair;
    private File outfile;

    private KeyPairGenerator generator;

    @Before
    public void setUp() throws Exception {
        issuer = new X500Name("CN=Test Issuer");

        generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(BC)
            .build(keyPair.getPrivate());

        outfile = new File(folder.getRoot(), "new.crl");
    }

    private X509v2CRLBuilder createCRLBuilder() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        crlBuilder.addExtension(X509Extension.authorityKeyIdentifier, false,
            new AuthorityKeyIdentifierStructure(keyPair.getPublic()));
        /* With a CRL number of 127, incrementing it should cause the number of bytes in the length
         * portion of the TLV to increase by one.*/
        crlBuilder.addExtension(X509Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));
        crlBuilder.addCRLEntry(new BigInteger("100"), new Date(), CRLReason.unspecified);
        return crlBuilder;
    }

    private X509CRL createCRL() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        X509CRLHolder holder = crlBuilder.build(signer);
        return new JcaX509CRLConverter().setProvider(BC).getCRL(holder);
    }

    private File writeCRL(X509CRL crl) throws Exception {
        File crlToChange = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(crlToChange, crl.getEncoded());
        return crlToChange;
    }


    private X509CRL readCRL() throws Exception {
        return readCRL(keyPair.getPublic());
    }

    private X509CRL readCRL(PublicKey signatureKey) throws Exception {
        InputStream changedStream = new BufferedInputStream(new FileInputStream(outfile));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509CRL changedCrl = (X509CRL) cf.generateCRL(changedStream);
        changedCrl.verify(signatureKey, BC);

        return changedCrl;
    }

    @Test
    public void testHandlesExtensions() throws Exception {
        File crlToChange = writeCRL(createCRL());

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<BigInteger>();
        expected.add(new BigInteger("100"));
        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToCRL() throws Exception {
        File crlToChange = writeCRL(createCRL());

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());

        Set<BigInteger> expected = new HashSet<BigInteger>();
        expected.add(new BigInteger("100"));

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<BigInteger>(Arrays.asList(
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

        Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testAddEntryToCRLWithNoExtensions() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        crlBuilder.addCRLEntry(new BigInteger("100"), new Date(), CRLReason.unspecified);
        X509CRLHolder holder = crlBuilder.build(signer);
        X509CRL crl = new JcaX509CRLConverter().setProvider(BC).getCRL(holder);

        File crlToChange = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(crlToChange, crl.getEncoded());

        File outfile = new File(folder.getRoot(), "new.crl");
        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());

        Set<BigInteger> expected = new HashSet<BigInteger>();
        expected.add(new BigInteger("100"));

        // Add enough items to cause the number of length bytes to change
        Set<BigInteger> newSerials = new HashSet<BigInteger>(Arrays.asList(
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

        Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testKeySizeChange() throws Exception {
        int[] sizes = { 1024, 4096 };

        for (int size : sizes) {
            File crlToChange = writeCRL(createCRL());

            generator.initialize(size);
            KeyPair largerKeyPair = generator.generateKeyPair();

            X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
                (RSAPrivateKey) largerKeyPair.getPrivate());
            stream.preScan(crlToChange).lock();
            OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
            stream.write(o);
            o.close();

            X509CRL changedCrl = readCRL(largerKeyPair.getPublic());

            Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

            for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
                discoveredSerials.add(entry.getSerialNumber());
            }

            Set<BigInteger> expected = new HashSet<BigInteger>();
            expected.add(new BigInteger("100"));

            assertEquals(expected, discoveredSerials);
        }
    }

    @Test
    public void testIncrementsExtensions() throws Exception {
        File crlToChange = writeCRL(createCRL());

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        byte[] val = changedCrl.getExtensionValue(X509Extension.cRLNumber.getId());
        DEROctetString s = (DEROctetString) DERTaggedObject.fromByteArray(val);
        DERInteger i = (DERInteger) DERTaggedObject.fromByteArray(s.getOctets());

        assertTrue("CRL Number not incremented", i.getValue().compareTo(BigInteger.ONE) > 0);
    }

    @Test
    public void testDeleteEntryFromCRL() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        crlBuilder.addCRLEntry(new BigInteger("101"), new Date(), CRLReason.unspecified);
        X509CRLHolder holder = crlBuilder.build(signer);
        X509CRL crl = new JcaX509CRLConverter().setProvider(BC).getCRL(holder);

        File crlToChange = writeCRL(crl);

        CRLEntryValidator validator = new CRLEntryValidator() {
            @Override
            public boolean shouldDelete(X509CRLEntryObject entry) {
                return entry.getSerialNumber().equals(new BigInteger("101"));
            }
        };

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.add(new BigInteger("9000"), new Date(), 0);
        stream.preScan(crlToChange, validator).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<BigInteger>();
        expected.add(new BigInteger("100"));
        expected.add(new BigInteger("9000"));

        assertEquals(expected, discoveredSerials);
    }

    @Test
    public void testModifyUpdatedTime() throws Exception {
        X509CRL crl = createCRL();
        File crlToChange = writeCRL(crl);

        Thread.sleep(1000);

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        assertTrue("Error: CRL thisUpdate field unmodified", crl.getThisUpdate()
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
        X509CRL crl = new JcaX509CRLConverter().setProvider(BC).getCRL(holder);

        File crlToChange = writeCRL(crl);

        Thread.sleep(1000);

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        assertTrue("Error: CRL nextUpdate field unmodified",
            crl.getNextUpdate().before(changedCrl.getNextUpdate()));
    }

    @Test
    public void testSignatureKeyChange() throws Exception {
        KeyPair differentKeyPair = generator.generateKeyPair();

        ContentSigner otherSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(BC)
            .build(differentKeyPair.getPrivate());

        X509v2CRLBuilder crlBuilder = createCRLBuilder();
        X509CRLHolder holder = crlBuilder.build(otherSigner);
        X509CRL crl = new JcaX509CRLConverter().setProvider(BC).getCRL(holder);

        File crlToChange = writeCRL(crl);

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
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

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
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

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.add(new BigInteger("9000"), new Date(), 0);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(
            matchesPattern("The instance must be.*locked.*"));

        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.lock();
        stream.write(o);
        o.close();
    }

    @Test
    public void testSha1Signature() throws Exception {
        X509v2CRLBuilder crlBuilder = createCRLBuilder();

        String signingAlg = "SHA1WithRSAEncryption";
        ContentSigner sha1Signer = new JcaContentSignerBuilder(signingAlg)
            .setProvider(BC)
            .build(keyPair.getPrivate());

        X509CRLHolder holder = crlBuilder.build(sha1Signer);
        X509CRL crl = new JcaX509CRLConverter().setProvider(BC).getCRL(holder);

        File crlToChange = writeCRL(crl);

        X509CRLStreamWriter stream = new X509CRLStreamWriter(crlToChange,
            (RSAPrivateKey) keyPair.getPrivate());
        stream.add(new BigInteger("9000"), new Date(), 0);
        stream.preScan(crlToChange).lock();
        OutputStream o = new BufferedOutputStream(new FileOutputStream(outfile));
        stream.write(o);
        o.close();

        X509CRL changedCrl = readCRL();

        Set<BigInteger> discoveredSerials = new HashSet<BigInteger>();

        for (X509CRLEntry entry : changedCrl.getRevokedCertificates()) {
            discoveredSerials.add(entry.getSerialNumber());
        }

        Set<BigInteger> expected = new HashSet<BigInteger>();
        expected.add(new BigInteger("100"));
        expected.add(new BigInteger("9000"));

        assertEquals(expected, discoveredSerials);
    }
}

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

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.TBSCertList;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test implementations of X509CRLEntryStream
 */
@RunWith(Parameterized.class)
public class X509CRLEntryStreamTest {
    private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Parameterized.Parameters
    public static Iterable<Class> data() {
        return Arrays.asList(JSSX509CRLEntryStream.class);
    }

    private File derFile;
    private File pemFile;

    private X500Name issuer;
    private ContentSigner signer;
    private KeyPair keyPair;

    private final Constructor<? extends X509CRLEntryStream> fileConstructor;

    public X509CRLEntryStreamTest(Class<? extends X509CRLEntryStream> clazz) throws NoSuchMethodException {
        this.fileConstructor = clazz.getConstructor(File.class);
    }

    @Before
    public void setUp() throws Exception {
        URL url = this.getClass().getClassLoader().getResource("crl.der");
        derFile = new File(Objects.requireNonNull(url).getFile());

        url = this.getClass().getClassLoader().getResource("crl.pem");
        pemFile = new File(Objects.requireNonNull(url).getFile());

        issuer = new X500Name("CN=Test Issuer");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC_PROVIDER)
            .build(keyPair.getPrivate());
    }

    private BigInteger getSerial(TBSCertList.CRLEntry c) {
        return c.getUserCertificate().getValue();
    }

    @Test
    public void testIterateOverSerials() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Set<BigInteger> referenceSerials = new HashSet<>();
        try (
            InputStream referenceStream = new FileInputStream(derFile);
        ) {
            X509CRL referenceCrl = (X509CRL) cf.generateCRL(referenceStream);
            for (X509CRLEntry entry : referenceCrl.getRevokedCertificates()) {
                referenceSerials.add(entry.getSerialNumber());
            }
        }

        try (
            X509CRLEntryStream stream = fileConstructor.newInstance(derFile);
        ) {
            Set<BigInteger> streamedSerials = new HashSet<>();
            while (stream.hasNext()) {
                streamedSerials.add(stream.next().getSerialNumber());
            }

            assertEquals(referenceSerials, streamedSerials);
        }
    }

    @Test
    public void testIterateOverEmptyCrl() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier
            (keyPair.getPublic());

        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, identifier);
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));

        X509CRLHolder holder = crlBuilder.build(signer);

        File noUpdateTimeCrl = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(noUpdateTimeCrl, holder.getEncoded());

        try (
            X509CRLEntryStream stream = fileConstructor.newInstance(noUpdateTimeCrl);
        ) {
            Set<BigInteger> streamedSerials = new HashSet<>();
            while (stream.hasNext()) {
                streamedSerials.add(stream.next().getSerialNumber());
            }

            assertEquals(0, streamedSerials.size());
        }
    }

    @Test
    public void testIterateOverEmptyCrlWithNoExtensions() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());

        X509CRLHolder holder = crlBuilder.build(signer);

        File noUpdateTimeCrl = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(noUpdateTimeCrl, holder.getEncoded());

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(matchesPattern("v1.*"));

        try (
            X509CRLEntryStream stream = fileConstructor.newInstance(noUpdateTimeCrl);
        ) {
            while (stream.hasNext()) {
                stream.next();
            }
        }
    }

    @Test
    public void testCRLWithoutUpdateTime() throws Exception {
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, new Date());
        AuthorityKeyIdentifier identifier = new JcaX509ExtensionUtils().createAuthorityKeyIdentifier
            (keyPair.getPublic());

        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, identifier);
        crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(new BigInteger("127")));
        crlBuilder.addCRLEntry(new BigInteger("100"), new Date(), CRLReason.unspecified);

        X509CRLHolder holder = crlBuilder.build(signer);

        File noUpdateTimeCrl = new File(folder.getRoot(), "test.crl");
        FileUtils.writeByteArrayToFile(noUpdateTimeCrl, holder.getEncoded());


        try (
            X509CRLEntryStream stream = fileConstructor.newInstance(noUpdateTimeCrl);
        ) {
            Set<BigInteger> streamedSerials = new HashSet<>();
            while (stream.hasNext()) {
                streamedSerials.add(stream.next().getSerialNumber());
            }

            assertEquals(1, streamedSerials.size());
            assertTrue(streamedSerials.contains(new BigInteger("100")));
        }
    }

    @Test
    public void testPemReadThroughBase64Stream() throws Exception {
        /* NB: Base64InputStream only takes base64.  The "-----BEGIN X509 CRL-----" and
         * corresponding footer must be removed. */
        List<String> lines = Files.readAllLines(pemFile.toPath());
        int from = lines.indexOf("-----BEGIN X509 CRL-----") + 1;
        int to  = lines.indexOf("-----END X509 CRL-----");

        List<String> b64Lines = lines.stream()
            .skip(from)
            .limit(to - from)
            .collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String line : b64Lines) {
            baos.write(line.getBytes());
        }
        byte[] b64Bytes = baos.toByteArray();

        Set<BigInteger> referenceSerials = new HashSet<>();
        try (
            InputStream b64Stream = new Base64InputStream(new ByteArrayInputStream(b64Bytes));
        ) {

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509CRL referenceCrl = (X509CRL) cf.generateCRL(b64Stream);

            for (X509CRLEntry entry : referenceCrl.getRevokedCertificates()) {
                referenceSerials.add(entry.getSerialNumber());
            }
        }

        try (
            X509CRLEntryStream stream = fileConstructor.newInstance(derFile);
        ) {
            Set<BigInteger> streamedSerials = new HashSet<>();
            while (stream.hasNext()) {
                streamedSerials.add(stream.next().getSerialNumber());
            }

            assertEquals(referenceSerials, streamedSerials);
        }
    }
}

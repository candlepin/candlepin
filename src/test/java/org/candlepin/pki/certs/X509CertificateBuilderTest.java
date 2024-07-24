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

import static org.assertj.core.api.Assertions.assertThat;
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
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;



class X509CertificateBuilderTest {
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
        X509Certificate caCert = this.certificateAuthority.getCACert();

        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = createKeyPair();

        DistinguishedName distinguishedName = new DistinguishedName("candlepinproject.org", "common name");

        X509Certificate cert = this.builder
            .withDN(distinguishedName)
            .withValidity(start, end)
            .withSerial(1999L)
            .withKeyPair(keyPair)
            .withSubjectAltName("altName")
            .build();

        assertEquals("SHA256withRSA", cert.getSigAlgName());
        assertEquals("1999", cert.getSerialNumber().toString());

        X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());
        Extensions bcExtensions = holder.getExtensions();

        // Verify the DN is set as intended
        X500Principal principal = cert.getSubjectX500Principal();
        assertNotNull(principal);

        String dnstr = principal.getName(X500Principal.RFC2253);
        assertThat(dnstr)
            .isNotNull()
            .containsOnlyOnce("CN=" + distinguishedName.commonName())
            .containsOnlyOnce("O=" + distinguishedName.organizationName());

        // Verify key usage extension is present and critical
        Extension keyUsageExt = bcExtensions.getExtension(Extension.keyUsage);
        assertNotNull(keyUsageExt);
        assertTrue(keyUsageExt.isCritical());

        KeyUsage keyUsage = KeyUsage.fromExtensions(bcExtensions);

        assertTrue(keyUsage.hasUsages(KeyUsage.digitalSignature));
        assertTrue(keyUsage.hasUsages(KeyUsage.keyEncipherment));
        assertTrue(keyUsage.hasUsages(KeyUsage.dataEncipherment));

        // Verify the extended key usage is present, non-critical, and is configured correctly
        Extension exKeyUsageExt = bcExtensions.getExtension(Extension.extendedKeyUsage);
        assertNotNull(exKeyUsageExt);
        assertFalse(exKeyUsageExt.isCritical());

        assertTrue(ExtendedKeyUsage.fromExtensions(bcExtensions)
            .hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth));

        // Verify we aren't generating any CA certs with this builder
        Extension basicConstraintExt = bcExtensions.getExtension(Extension.basicConstraints);
        assertNotNull(basicConstraintExt);

        assertFalse(BasicConstraints.fromExtensions(bcExtensions).isCA());

        // Verify the cert type extensions
        NetscapeCertType expected = new NetscapeCertType(
            NetscapeCertType.sslClient | NetscapeCertType.smime);

        NetscapeCertType actual = new NetscapeCertType(
            (DERBitString) bcExtensions.getExtension(MiscObjectIdentifiers.netscapeCertType)
                .getParsedValue());

        assertEquals(expected, actual);

        // Verify the SKI is built from the provided public key
        byte[] expectedSKI = new JcaX509ExtensionUtils()
            .createSubjectKeyIdentifier(keyPair.getPublic())
            .getEncoded();

        Extension ski = bcExtensions.getExtension(Extension.subjectKeyIdentifier);
        assertNotNull(ski);

        assertArrayEquals(expectedSKI, SubjectKeyIdentifier.fromExtensions(bcExtensions).getEncoded());

        // Verify the AKI only contains the keyid field, referencing our CA cert
        // Note: at the time of writing, adding the issuer causes problems with various cert
        // processors, even if the extension is correctly defined
        byte[] expectedAKI = new JcaX509ExtensionUtils()
            .createAuthorityKeyIdentifier(caCert.getPublicKey())
            .getEncoded();

        Extension aki = bcExtensions.getExtension(Extension.authorityKeyIdentifier);
        assertNotNull(aki);

        assertArrayEquals(expectedAKI, AuthorityKeyIdentifier.fromExtensions(bcExtensions).getEncoded());
    }

    // TODO: FIXME: Add more tests for the expected default properties of certs built with the builder
    //  - verifying the SAN includes both any explicitly provided SAN and the provided (required) DN

    // TODO: FIXME: Add more tests surrounding the various mutators of the builder:
    //  - setting null or empty values
    //  - verifying the field itself resulted in the expected change

    @Test
    public void testCustomExtensions() throws Exception {
        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = createKeyPair();

        DistinguishedName distinguishedName = new DistinguishedName("candlepinproject.org", "common name");

        String extOid = OID.EntitlementType.namespace();
        String byteExtOid = OID.EntitlementData.namespace();
        byte[] someBytes = new byte[]{0xd, 0xe, 0xf, 0xa, 0xc, 0xe, 0xa, 0xc, 0xe};
        Set<X509Extension> extensions = Set.of(
            new X509StringExtension(extOid, "OrgLevel"),
            new X509ByteExtension(byteExtOid, someBytes)
        );

        X509Certificate cert = this.builder
            .withDN(distinguishedName)
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

    // TODO: FIXME: Add more tests for custom extensions:
    //  - calling .withExtensions with a null or empty collection
    //  - calling .withExtensions with a collection that contains multiple instances of the same extension
    //  - calling .withExtensions with a collection that contains null or malformed extensions
    //  - calling .withExtensions multiple times with the same collection
    //  - calling .withExtensions multiple times with different collections
    //  - calling .withExtensions and then changing the collection before calling build

    @Test
    public void testNonLatin1CharactersInDistinguishedNameAreEncoded() throws Exception {
        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = this.createKeyPair();

        String junkChars = "#$%&'()*+,:;=?@[] \".<>\\^_`{|}~£円ßЯ∑#$%&'()*+,:;=?@[] \".<>\\^_`{|}~£円ßЯ∑";

        String cnComponent = "CN=\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑" +
            "\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑";

        String oComponent = "O=\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑" +
            "\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑";

        DistinguishedName distinguishedName = new DistinguishedName(junkChars, junkChars);

        X509Certificate cert = this.builder
            .withDN(distinguishedName)
            .withValidity(start, end)
            .withSerial(2000L)
            .withKeyPair(keyPair)
            .build();

        assertNotNull(cert);

        X500Principal principal = cert.getSubjectX500Principal();
        assertNotNull(principal);

        String dnstr = principal.getName(X500Principal.RFC2253);

        // At the time of writing, our logging subsystem eats some characters (backslashes
        // primarily), making debugging a mismatch here impossible from the test failure output.
        // Uncomment this block to sort out which character(s) have gone missing from the output to
        // determine exactly what is going wrong.
        /*
        try (java.io.FileWriter writer = new java.io.FileWriter("x509CertificateBuilderTest_chardump.txt",
            java.nio.charset.StandardCharsets.UTF_8)) {

            writer.append("Expected CN: ")
                .append(cnComponent)
                .append("\nExpected O: ")
                .append(oComponent)
                .append("\nActual DN:   ")
                .append(dnstr)
                .append("\n");
        }
        */

        assertThat(dnstr)
            .isNotNull()
            .containsOnlyOnce(cnComponent)
            .containsOnlyOnce(oComponent);
    }

    @Test
    public void testNonLatin1CharacterInSubjectAltNameAreEncoded() throws Exception {
        Instant start = Instant.now();
        Instant end = LocalDate.now().plusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant();
        KeyPair keyPair = this.createKeyPair();

        DistinguishedName distinguishedName = new DistinguishedName("candlepinproject.org");
        String junkChars = "#$%&'()*+,:;=?@[] \".<>\\^_`{|}~£円ßЯ∑#$%&'()*+,:;=?@[] \".<>\\^_`{|}~£円ßЯ∑";

        String expectedDN = "CN=candlepinproject.org";
        String expectedSAN = "CN=\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑" +
            "\\#$%&'()*\\+\\,:\\;\\=?@[] \\\".\\<\\>\\\\^_`{|}~£円ßЯ∑";

        X509Certificate cert = this.builder
            .withDN(distinguishedName)
            .withValidity(start, end)
            .withSerial(2000L)
            .withKeyPair(keyPair)
            .withSubjectAltName(junkChars)
            .build();

        assertNotNull(cert);

        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        assertNotNull(sans);

        List<String> sanList = new ArrayList<>();
        sans.forEach(elem -> sanList.add((String) elem.get(1)));

        assertThat(sanList)
            .isNotNull()
            .hasSize(2)
            .contains(expectedDN)
            .contains(expectedSAN);
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

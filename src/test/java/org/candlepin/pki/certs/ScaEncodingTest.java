/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Consumer;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.OID;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.test.CryptoUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.X509V3ExtensionUtil;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1UTF8String;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.zip.InflaterInputStream;

class ScaEncodingTest {
    private byte[] decodeBlock(String encoded, String type) {
        String begin = "-----BEGIN " + type + "-----\n";
        String end = "-----END " + type + "-----";
        int start = encoded.indexOf(begin) + begin.length();
        return Base64.getMimeDecoder().decode(encoded.substring(start, encoded.indexOf(end, start)));
    }

    @Test
    void preservesPayloadFormatAndSignsCompressedBytes() throws Exception {
        Configuration config = TestConfig.defaults();
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(config);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        EntitlementPayloadGenerator generator = new EntitlementPayloadGenerator(
            ObjectMapperFactory.getX509V3ExtensionUtilObjectMapper());
        X509V3ExtensionUtil extensionUtil = new X509V3ExtensionUtil(config, mock(EntitlementCurator.class), new Huffman());
        Owner owner = new Owner().setId("owner").setKey("org")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        ProductContent repository = new ProductContent(new org.candlepin.model.Content("repo")
            .setName("Repository")
            .setType("yum")
            .setLabel("repo")
            .setVendor("vendor")
            .setContentUrl("/content/$basearch")
            .setGpgUrl("https://example.test/key")
            .setMetadataExpiration(600L)
            .setRequiredTags("first,, second")
            .setArches("x86_64"), false);
        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");
        ScaPayloadData data = new ScaContentDataProvider(config, mock(ContentCurator.class), extensionUtil)
            .prepare(owner, consumer, List.of(), timestamp, List.of(repository));

        String encoded = new ScaPayloadEncoder(cryptoManager, generator).encode(data, scheme);
        byte[] payload = this.decodeBlock(encoded, "ENTITLEMENT DATA");
        byte[] signature = this.decodeBlock(encoded, "SIGNATURE");

        Product sku = new Product().setId("content_access").setName("Content Access");
        Product engineering = new Product().setId("content_access").setName(" Content Access")
            .setProductContent(List.of(repository));
        Pool pool = new Pool().setProduct(sku).setStartDate(Date.from(timestamp)).setEndDate(Date.from(timestamp));
        PromotedContent promoted = new PromotedContent(ContentPathBuilder.from(owner, List.of()));
        org.candlepin.model.dto.Product legacyProduct = extensionUtil.mapProduct(engineering, sku, promoted,
            consumer, pool, Set.of("content-access"));
        byte[] expected = generator.generate(List.of(legacyProduct), consumer.getUuid(), pool, null);
        assertThat(payload).isEqualTo(expected);

        Signature validator = Signature.getInstance(scheme.signatureAlgorithm());
        validator.initVerify(scheme.certificate().getPublicKey());
        validator.update(payload);
        assertThat(validator.verify(signature)).isTrue();
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(payload))) {
            String json = new String(inflater.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("\"enabled\":false", "\"metadata_expire\":600", " Content Access");
        }
    }

    @Test
    void encodesCertificateFromPreparedData() throws Exception {
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(TestConfig.defaults());
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        KeyPair keyPair = cryptoManager.getKeyPairGenerator(scheme).generateKeyPair();
        Instant start = Instant.parse("2026-01-02T03:04:05Z");
        Instant end = start.plusSeconds(86400);
        List<String> paths = List.of("/org/Release+Candidate", "/org/Second");
        ScaCertificateData data = new ScaCertificateData(new DistinguishedName("consumer", "org"), paths);
        ScaCertificateEncoder encoder = new ScaCertificateEncoder(cryptoManager, CryptoUtil.getPemEncoder(), new Huffman());

        String pem = encoder.encode(data, scheme, BigInteger.TEN, keyPair, start, end);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        certificate.verify(scheme.certificate().getPublicKey());
        assertThat(certificate.getSerialNumber()).isEqualTo(BigInteger.TEN);
        assertThat(certificate.getNotBefore()).isEqualTo(Date.from(start));
        assertThat(certificate.getNotAfter()).isEqualTo(Date.from(end));
        assertThat(certificate.getCriticalExtensionOIDs()).containsExactly("2.5.29.15");
        assertThat(certificate.getNonCriticalExtensionOIDs()).containsExactlyInAnyOrder(
            "2.16.840.1.113730.1.1", "2.5.29.37", "2.5.29.35", "2.5.29.14", "2.5.29.19",
            OID.EntitlementVersion.namespace(), OID.EntitlementType.namespace(), OID.EntitlementData.namespace());
        byte[] version = ASN1OctetString.getInstance(certificate.getExtensionValue(OID.EntitlementVersion.namespace()))
            .getOctets();
        assertThat(ASN1UTF8String.getInstance(version).getString()).isEqualTo("3.4");
        byte[] type = ASN1OctetString.getInstance(certificate.getExtensionValue(OID.EntitlementType.namespace())).getOctets();
        assertThat(ASN1UTF8String.getInstance(type).getString()).isEqualTo("OrgLevel");
        byte[] inner = ASN1OctetString.getInstance(certificate.getExtensionValue(OID.EntitlementData.namespace())).getOctets();
        byte[] compressed = ASN1OctetString.getInstance(inner).getOctets();
        assertThat(new Huffman().hydrateContentPackage(compressed)).containsExactlyInAnyOrderElementsOf(paths);
    }

    @Test
    void rejectsMissingPreparedPayloadOrScheme() {
        ScaPayloadEncoder encoder = new ScaPayloadEncoder(mock(CryptoManager.class), mock(EntitlementPayloadGenerator.class));
        ScaPayloadData empty = new ScaPayloadData("consumer", Instant.EPOCH, List.of());

        assertThrows(NullPointerException.class, () -> encoder.encode(null, mock(Scheme.class)));
        assertThrows(NullPointerException.class, () -> encoder.encode(empty, null));
    }
    @Test
    void rejectsMissingCertificateInputs() {
        ScaCertificateEncoder encoder = new ScaCertificateEncoder(mock(CryptoManager.class),
            CryptoUtil.getPemEncoder(), new Huffman());
        ScaCertificateData data = new ScaCertificateData(new DistinguishedName("consumer"), List.of());
        Scheme scheme = mock(Scheme.class);
        KeyPair keyPair = new KeyPair(mock(PublicKey.class), mock(PrivateKey.class));

        assertThrows(NullPointerException.class, () -> encoder.encode(null, scheme, BigInteger.ONE,
            keyPair, Instant.EPOCH, Instant.MAX));
        assertThrows(NullPointerException.class, () -> encoder.encode(data, null, BigInteger.ONE,
            keyPair, Instant.EPOCH, Instant.MAX));
        assertThrows(NullPointerException.class, () -> encoder.encode(data, scheme, null,
            keyPair, Instant.EPOCH, Instant.MAX));
        assertThrows(NullPointerException.class, () -> encoder.encode(data, scheme, BigInteger.ONE,
            null, Instant.EPOCH, Instant.MAX));
        assertThrows(NullPointerException.class, () -> encoder.encode(data, scheme, BigInteger.ONE,
            keyPair, null, Instant.MAX));
        assertThrows(NullPointerException.class, () -> encoder.encode(data, scheme, BigInteger.ONE,
            keyPair, Instant.EPOCH, null));
    }
}

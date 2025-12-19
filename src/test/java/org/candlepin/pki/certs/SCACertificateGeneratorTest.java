/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.Environment;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.OID;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.Signer;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.test.X509HuffmanDecodeUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.inject.Provider;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SCACertificateGeneratorTest extends DatabaseTestFixture {
    @Mock
    private V3CapabilityCheck v3CapabilityCheck;
    private Configuration config;
    private X509V3ExtensionUtil extensionUtil;
    private SCACertificateGenerator generator;
    private Provider<X509CertificateBuilder> x509CertificateBuilderProvider;
    private BouncyCastleKeyPairGenerator keyPairGenerator;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.config = TestConfig.defaults();
        this.extensionUtil = spy(new X509V3ExtensionUtil(
            this.config, this.entitlementCurator, new Huffman()));

        this.generator = getNewGenerator();
    }

    private SCACertificateGenerator getNewGenerator() throws CertificateException, IOException {
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));

        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        this.extensionUtil = spy(new X509V3ExtensionUtil(
            config, this.entitlementCurator, new Huffman()));
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();

        X509CertificateBuilder builder =
            new X509CertificateBuilder(certificateReader, securityProvider, subjectKeyIdentifierWriter);
        x509CertificateBuilderProvider = () -> builder;

        return new SCACertificateGenerator(
            this.extensionUtil,
            this.v3CapabilityCheck,
            new EntitlementPayloadGenerator(new ObjectMapper()),
            this.caCertCurator,
            this.caPayloadCurator,
            this.certSerialCurator,
            this.contentCurator,
            this.consumerCurator,
            this.environmentCurator,
            new BouncyCastlePemEncoder(),
            keyPairGenerator,
            new Signer(certificateReader),
            x509CertificateBuilderProvider,
            this.config
        );
    }

    private X509Certificate getX509Certificate(SCACertificate container) throws CertificateException {
        ByteArrayInputStream istream = new ByteArrayInputStream(container.getCertAsBytes());

        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(istream);
    }

    private ASN1Primitive extractExtensionValue(X509Certificate cert, String oid) throws IOException {
        byte[] extDerValue = cert.getExtensionValue(oid);
        return extDerValue != null ? ASN1Primitive.fromByteArray(extDerValue) : null;
    }

    private byte[] extractEntitlementDataPayload(X509Certificate cert) throws CertificateException,
        IOException {

        ASN1Primitive extValuePrimitive = this.extractExtensionValue(cert, OID.EntitlementData.namespace());
        assertNotNull(extValuePrimitive);
        assertInstanceOf(DEROctetString.class, extValuePrimitive);

        // Yes, this two-stage extraction is necessary (and weird)
        byte[] octets = ((DEROctetString) extValuePrimitive).getOctets();
        return ((DEROctetString) DEROctetString.fromByteArray(octets)).getOctets();
    }

    private String urlEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    @Test
    public void testGetSCAContentPayloadWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () ->  {
            generator.getContentPayload(null);
        });
    }

    @Test
    public void testGetSCAContentPayloadWithNullOwner() throws Exception {
        Consumer consumer = new Consumer()
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetSCAContentPayloadWithNonSCAOwner() throws Exception {
        Owner owner = this.createNonSCAOwner(TestUtil.randomString("owner-"));
        Consumer consumer = this.createConsumer(owner);

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetSCAContentPayloadWithNonV3CertCapableConsumer() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));
        Consumer consumer = this.createConsumer(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        doReturn(false).when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertNull(actual);
    }

    private static Stream<Arguments> arches() {
        return Stream.of(
            Arguments.of(List.of(), null),
            Arguments.of(List.of("arch-1", "arch-2"), null),
            Arguments.of(List.of(), "supp-arch-1, supp-arch-2"),
            Arguments.of(List.of("arch-1", "arch-2"), "supp-arch-1, supp-arch-2"),
            Arguments.of(List.of("arch-1", "arch-1"), "supp-arch-1, supp-arch-1"),
            Arguments.of(List.of("ArCh-1", "aRcH-1"), "SuPp-ArCh-1, sUpP-aRcH-1"),
            Arguments.of(List.of("arch-1", "arch-2", "arch-1"), "supp-arch-1, supp-arch-2, supp-arch-1"),
            Arguments.of(List.of("arch-1", "supp-arch-1"), "supp-arch-1, arch-1"));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @MethodSource("arches")
    public void testGetSCAContentPayloadWithArchesAndSupportedArches(List<String> arches,
        String supportedArches) throws Exception {

        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        if (supportedArches != null) {
            consumer.setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, supportedArches);
        }

        for (String arch : arches) {
            consumer.setFact(Consumer.Facts.ARCHITECTURE, arch);
        }

        consumer = consumerCurator.create(consumer);

        // Combine both the arches and the support arches and convert to lower case to validate case
        // differences between the consumer facts
        Set<String> allArches = arches.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        Util.toList(supportedArches).stream()
            .map(String::toLowerCase)
            .map(arch -> allArches.add(arch));

        List<Content> expectedContent = createContentWithArches(owner, allArches);

        // Create some content with arches that should be filtered and not included in the content payload
        createContentWithArches(owner, List.of("diff-arch-1", "diff-arch-2"));

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload payload = generator.getContentPayload(consumer);

        assertPayload(payload.getPayload(), consumer.getUuid(), expectedContent);
    }

    private List<Content> createContentWithArches(Owner owner, Collection<String> arches) {
        List<Content> createdContent = new ArrayList<>();
        for (String arch : arches) {
            String id = arch + "-content";
            Content content = TestUtil.createContent(id, id)
                .setArches(arch);
            content = this.contentCurator.create(content);

            createdContent.add(content);
            createProductContent(owner, true, content);
        }

        return createdContent;
    }

    @Test
    public void testGetSCAContentPayload() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        consumer = consumerCurator.create(consumer);

        Product engProduct = createProduct();
        this.createPool(owner, engProduct);

        Content content = this.createContent();
        createProductContent(owner, true, content);

        Product skuProd = TestUtil.createProduct(TestUtil.randomString("sku-prod-id-"),
            TestUtil.randomString("sku-prod-name-"));
        skuProd.setProvidedProducts(List.of(engProduct));
        skuProd = this.productCurator.create(skuProd);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp);

        assertPayload(actual.getPayload(), consumer.getUuid(), List.of(content));
    }

    @Test
    public void testGetSCAContentPayloadWithMultipleProducts() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        consumer = consumerCurator.create(consumer);

        Product engProduct1 = createProduct();
        Product engProduct2 = createProduct();
        this.createPool(owner, engProduct1);
        this.createPool(owner, engProduct2);

        Content content1 = TestUtil.createContent("c1-id", "c1-name")
            .setContentUrl("https://c1.test.com");
        content1 = this.contentCurator.create(content1);

        Content content2 = TestUtil.createContent("c2-id", "c2-name")
            .setContentUrl("https://c2.test.com");
        content2 = this.contentCurator.create(content2);

        createProductContent(owner, true, content1);
        createProductContent(owner, true, content2);

        Product skuProd1 = TestUtil.createProduct(TestUtil.randomString("sku-prod-id-1-"),
            TestUtil.randomString("sku-prod-name-1-"));
        skuProd1.setProvidedProducts(List.of(engProduct1));
        skuProd1 = this.productCurator.create(skuProd1);

        Product skuProd2 = TestUtil.createProduct(TestUtil.randomString("sku-prod-id-2-"),
            TestUtil.randomString("sku-prod-name-2-"));
        skuProd2.setProvidedProducts(List.of(engProduct2));
        skuProd2 = this.productCurator.create(skuProd2);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(null, ContentAccessPayload::getPayloadKey)
            .doesNotReturn(null, ContentAccessPayload::getPayload)
            .doesNotReturn(null, ContentAccessPayload::getTimestamp);

        assertPayload(actual.getPayload(), consumer.getUuid(), List.of(content1, content2));
    }

    @Test
    public void testGetSCAContentPayloadWithExistingNonExpiredPayload() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload expected = generator.getContentPayload(consumer);

        ContentAccessPayload actual = generator.getContentPayload(consumer);

        assertThat(actual)
            .returns(expected.getId(), ContentAccessPayload::getId)
            .returns(expected.getOwnerId(), ContentAccessPayload::getOwnerId)
            .returns(expected.getPayload(), ContentAccessPayload::getPayload)
            .returns(expected.getPayloadKey(), ContentAccessPayload::getPayloadKey)
            .returns(expected.getTimestamp(), ContentAccessPayload::getTimestamp);
    }

    @Test
    public void testGetSCAContentPayloadWithExpiredPayloadFromOwnerLastContentUpdate() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));
        Consumer consumer = this.createConsumer(owner);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload initial = generator.getContentPayload(consumer);
        Date initialTimestamp = initial.getTimestamp();
        String initialPayload = initial.getPayload();
        String initialPayloadKey = initial.getPayloadKey();

        // Update the owner's last content update to trigger a new content payload generation
        owner.setLastContentUpdate(TestUtil.createDateOffset(0, 0, 1));

        // Wait a second to gaurantee that the payload timestamps will be different
        Thread.sleep(1000);

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        ContentAccessPayload actual = getNewGenerator()
            .getContentPayload(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(initialTimestamp, ContentAccessPayload::getTimestamp)
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .returns(initialPayload, ContentAccessPayload::getPayload)
            .returns(initialPayloadKey, ContentAccessPayload::getPayloadKey);
    }

    @Test
    public void testGetSCAContentPayloadWithExpiredPayloadFromEnvironmentContentUpdate() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));
        Consumer consumer = this.createConsumer(owner);

        Content envContent = this.createContent();
        this.createProductContent(owner, true, envContent);

        Environment env = this.createEnvironment(owner, TestUtil.randomString("id-"),
            TestUtil.randomString("name-"), null, List.of(consumer), List.of(envContent));

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        ContentAccessPayload initial = generator.getContentPayload(consumer);
        Date initialTimestamp = initial.getTimestamp();
        String initialPayload = initial.getPayload();
        String initialPayloadKey = initial.getPayloadKey();

        // Add a new content to the environment and update the environment's last content update to be after
        // the SCA content payload's timestamp. This should trigger the payload to be regenerated.
        Content envContent2 = this.createContent();
        this.createProductContent(owner, true, envContent2);
        env.addContent(envContent2, true);
        env.setLastContentUpdate(TestUtil.createDateOffset(0, 0, 1));
        environmentCurator.saveOrUpdate(env);

        // Wait a second to gaurantee that the payload timestamps will be different
        Thread.sleep(1000);

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        ContentAccessPayload actual = getNewGenerator()
            .getContentPayload(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(initialTimestamp, ContentAccessPayload::getTimestamp)
            .returns(owner.getId(), ContentAccessPayload::getOwnerId)
            .doesNotReturn(initialPayload, ContentAccessPayload::getPayload)
            .returns(initialPayloadKey, ContentAccessPayload::getPayloadKey);

        assertPayload(actual.getPayload(), consumer.getUuid(), List.of(envContent, envContent2));
    }

    @Test
    public void testGetSCAX509CertificateWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () ->  {
            generator.getX509Certificate(null);
        });
    }

    @Test
    public void testGetSCAX509CertificateWithNullOwner() throws Exception {
        Consumer consumer = new Consumer()
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        SCACertificate actual = generator.getX509Certificate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetSCAX509CertificateWithNonSCAOwner() throws Exception {
        Owner owner = this.createNonSCAOwner(TestUtil.randomString());
        Consumer consumer = this.createConsumer(owner);

        SCACertificate actual = generator.getX509Certificate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetSCAX509CertificateWithNonV3CertCapableConsumer() throws Exception {
        Owner owner = this.createOwner("owner-");
        Consumer consumer = this.createConsumer(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        doReturn(false)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate actual = generator.getX509Certificate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetSCAX509Certificate() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Environment env = this.createEnvironment(owner);
        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue())
            .addEnvironment(env);

        consumer = consumerCurator.create(consumer);

        Product engProduct = createProduct();
        this.createPool(owner, engProduct);

        Content content = this.createContent();
        ProductContent productContent = new ProductContent(engProduct, content, true);
        this.getEntityManager().persist(productContent);

        Product product = TestUtil
            .createProduct(TestUtil.randomString("id-"), TestUtil.randomString("name-"));
        product.setProvidedProducts(List.of(engProduct));
        product = this.productCurator.create(product);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate actual = generator.getX509Certificate(consumer);

        X509Certificate cert = getX509Certificate(actual);
        assertNotNull(cert);

        byte[] payload = extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> expected = List.of("/" + owner.getKey() + "/" + env.getName());

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetSCAX509CertificateWithExistingCertAndPayload() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate expected = generator.getX509Certificate(consumer);

        SCACertificate actual = generator.getX509Certificate(consumer);

        assertThat(actual)
            .returns(expected.getId(), SCACertificate::getId)
            .returns(expected.getKey(), SCACertificate::getKey)
            .returns(expected.getCreated(), SCACertificate::getCreated)
            .returns(expected.getSerial(), SCACertificate::getSerial)
            .returns(expected.getCert(), SCACertificate::getCert);
    }

    @Test
    public void testGetSCAX509CertificateWithExpiredCert() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate initialSCACertificate = generator.getX509Certificate(consumer);
        String initialCert = initialSCACertificate.getCert();
        Date initialUpdated = initialSCACertificate.getUpdated();

        // Update the exiration date to the past to expire the cert
        CertificateSerial serial = initialSCACertificate.getSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, -1));
        certSerialCurator.saveOrUpdate(serial);

        // Wait a second so that the timestamp is different
        Thread.sleep(1000);

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        SCACertificate actual = getNewGenerator()
            .getX509Certificate(consumer);

        assertThat(actual)
            .doesNotReturn(initialCert, SCACertificate::getCert)
            .doesNotReturn(initialUpdated, SCACertificate::getUpdated)
            .extracting(SCACertificate::getSerial)
            .isNotNull()
            .doesNotReturn(serial.getId(), CertificateSerial::getId)
            .doesNotReturn(serial.getSerial(), CertificateSerial::getSerial);
    }

    @Test
    public void testGetSCAX509CertificateWithExistingCertInExpirationThreshold() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate initialCert = generator.getX509Certificate(consumer);
        BigInteger initialSerial = initialCert.getSerial().getSerial();

        // The ConfigProperties.SCA_X509_CERT_EXPIRY_THRESHOLD value is 5 days, so if we set the serial
        // expiration to be in 2 days, we should regenerate the certificate
        CertificateSerial serial = initialCert.getSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 2));
        initialCert.setSerial(serial);

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        SCACertificate actual = getNewGenerator()
            .getX509Certificate(consumer);

        assertThat(actual)
            .isNotNull()
            .extracting(SCACertificate::getSerial)
            .isNotNull()
            .extracting(CertificateSerial::getSerial)
            .isNotEqualTo(initialSerial);
    }

    @Test
    public void testGenerateWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    public void testGenerateWithNullOwner() throws Exception {
        Consumer consumer = new Consumer()
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        SCACertificate actual = generator.generate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGenerateWithNonSCAOwner() throws Exception {
        Owner owner = this.createNonSCAOwner(TestUtil.randomString());
        Consumer consumer = this.createConsumer(owner);

        SCACertificate actual = generator.generate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGenerateWithNonV3CertCapableConsumer() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        doReturn(false).when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate actual = generator.generate(consumer);

        assertNull(actual);
    }

    @Test
    public void testGenerateWithNoExistingCertOrPayload() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Product engProduct = createProduct();
        this.createPool(owner, engProduct);

        Content content = this.createContent();
        ProductContent productContent = new ProductContent(engProduct, content, true);
        this.getEntityManager().persist(productContent);

        Product product = TestUtil
            .createProduct(TestUtil.randomString("id-"), TestUtil.randomString("name-"));
        product.setProvidedProducts(List.of(engProduct));
        product = this.productCurator.create(product);

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate actual = generator.generate(consumer);

        X509Certificate cert = getX509Certificate(actual);
        assertNotNull(cert);

        byte[] payload = extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(List.of("/" + owner.getKey()));

        assertPayload(actual.getCertificate(), consumer.getUuid(), List.of(content));
    }

    @Test
    public void testGenerateWithExistingCertAndPayload() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        consumer = consumerCurator.create(consumer);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate expected = generator.generate(consumer);

        SCACertificate actual = generator.generate(consumer);

        assertThat(actual)
            .returns(expected.getId(), SCACertificate::getId)
            .returns(expected.getKey(), SCACertificate::getKey)
            .returns(expected.getCreated(), SCACertificate::getCreated)
            .returns(expected.getSerial(), SCACertificate::getSerial)
            .returns(expected.getCert(), SCACertificate::getCert);
    }

    @Test
    public void testGenerateWithExpiredCert() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        ConsumerType consumerType = this.createConsumerType();
        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setType(consumerType)
            .setOwner(owner)
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        consumer = consumerCurator.create(consumer);

        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate initialCert = generator.generate(consumer);

        // Update the exiration date to the past to expire the cert
        CertificateSerial serial = initialCert.getSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, -1));

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        SCACertificate actual = getNewGenerator()
            .generate(consumer);

        // Verify the SCACertificate was recreated with a new serial
        assertNotEquals(initialCert.getCert(), actual.getCert());
        assertNotEquals(initialCert.getSerial().getId(), actual.getSerial().getId());
        assertNotEquals(initialCert.getSerial().getExpiration(), actual.getSerial().getExpiration());
        assertNotEquals(initialCert.getUpdated(), actual.getUpdated());
    }

    @Test
    public void testGenerateWithExistingCertInExpirationThreshold() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Consumer consumer = this.createConsumer(owner);
        doReturn(true)
            .when(v3CapabilityCheck)
            .isCertV3Capable(consumer);

        SCACertificate initialCert = generator.generate(consumer);

        // The ConfigProperties.SCA_X509_CERT_EXPIRY_THRESHOLD value is 5 days, so if we set the serial
        // expiration to be in 2 days, we should regenerate the certificate
        CertificateSerial serial = initialCert.getSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 2));
        initialCert.setSerial(serial);

        // Need a new SCACertificateGenerator so that the X509CertificateBuilder does not retain any
        // X509 extensions from the first 'generate' invocation.
        SCACertificate actual = getNewGenerator()
            .generate(consumer);

        assertThat(actual)
            .isNotNull()
            .extracting(SCACertificate::getSerial)
            .isNotNull()
            .extracting(CertificateSerial::getSerial)
            .isNotEqualTo(initialCert.getSerial().getSerial());
    }

    @Test
    public void testGeneratedCertificateIncludesOwnerKeyInContainerContentPath() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));
        Consumer consumer = this.createConsumer(owner);

        List<String> expected = List.of("/" + owner.getKey());

        when(this.v3CapabilityCheck.isCertV3Capable(consumer))
            .thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);

        X509Certificate cert = this.getX509Certificate(result);
        assertNotNull(cert);

        byte[] payload = this.extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testCertificateIncludesEnvironmentNameInContainerContentPath() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString("owner-"));

        Environment env = this.createEnvironment(owner);
        Consumer consumer = this.createConsumer(owner);
        consumer.addEnvironment(env);
        consumer = this.consumerCurator.update(consumer);

        List<String> expected = List.of("/" + owner.getKey() + "/" + env.getName());

        when(this.v3CapabilityCheck.isCertV3Capable(consumer))
            .thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);

        X509Certificate cert = this.getX509Certificate(result);
        assertNotNull(cert);

        byte[] payload = this.extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testCertificateURLEncodesContainerContentPaths() throws Exception {
        Owner owner = this.createOwner("org-#$%&'()*+,123:;=?@[] \".<>\\^_`{|}~£円ßЯ∑");

        Consumer consumer = this.createConsumer(owner);
        Content content = this.createContent();
        Environment env = this.createEnvironment(owner, "id",
            "env-#$%&'()*+,123:;=?@[] \".<>\\^_`{|}~£円ßЯ∑", "", List.of(consumer),
            List.of(content));

        List<String> expected = List.of(
            "/" + this.urlEncode(owner.getKey()) + "/" + this.urlEncode(env.getName()));

        when(this.v3CapabilityCheck.isCertV3Capable(consumer))
            .thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);

        X509Certificate cert = this.getX509Certificate(result);
        assertNotNull(cert);

        byte[] payload = this.extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Verify that any forward slashes (path segment separators) present in either the owner key
     * or environment name do not get encoded.
     */
    @Test
    public void testCertificateDoesNotEncodeForwardSlashesInContainerPathComponents() throws Exception {
        Owner owner = this.createOwner("segmented/owner/key");

        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Environment env1 = this.createEnvironment(owner, TestUtil.randomString(), "env1/path/as/name",
            "", List.of(consumer), List.of(content1));
        Environment env2 = this.createEnvironment(owner, TestUtil.randomString(), "env2/path/as/name",
            "", List.of(consumer), List.of(content2));
        Environment env3 = this.createEnvironment(owner, TestUtil.randomString(), "env3/path/as/name",
            "", List.of(consumer), List.of(content3));

        List<String> expected = List.of(
            "/" + owner.getKey() + "/" + env1.getName(),
            "/" + owner.getKey() + "/" + env2.getName(),
            "/" + owner.getKey() + "/" + env3.getName());

        when(this.v3CapabilityCheck.isCertV3Capable(consumer))
            .thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);

        X509Certificate cert = this.getX509Certificate(result);
        assertNotNull(cert);

        byte[] payload = this.extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Verify that any forward slashes (path segment separators) present in either the owner key
     * or environment name do not get encoded.
     */
    @Test
    public void testCertificateContainerContentPathDiscardsEmptyPathSegments() throws Exception {
        Owner owner = this.createOwner("segmented//owner//key");

        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Environment env1 = this.createEnvironment(owner, TestUtil.randomString(), "/env1/path/as/name",
            "", List.of(consumer), List.of(content1));
        Environment env2 = this.createEnvironment(owner, TestUtil.randomString(),
            "//env2///path////as/////name", "", List.of(consumer), List.of(content2));
        Environment env3 = this.createEnvironment(owner, TestUtil.randomString(),
            "/ /env3/  /path/  /  /as/   /   /   /name", "", List.of(consumer),
            List.of(content3));

        List<String> expected = List.of(
            ("/" + owner.getKey() + "/" + env1.getName()).replaceAll("/[\s/]*/", "/"),
            ("/" + owner.getKey() + "/" + env2.getName()).replaceAll("/[\s/]*/", "/"),
            ("/" + owner.getKey() + "/" + env3.getName()).replaceAll("/[\s/]*/", "/"));

        when(this.v3CapabilityCheck.isCertV3Capable(consumer))
            .thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);

        X509Certificate cert = this.getX509Certificate(result);
        assertNotNull(cert);

        byte[] payload = this.extractEntitlementDataPayload(cert);
        assertNotNull(payload);

        List<String> contentPaths = new X509HuffmanDecodeUtil().extractContentPaths(payload);
        assertThat(contentPaths)
            .isNotNull()
            .isNotEmpty()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertPayload(String payload, String consumerUuid, Collection<Content> expectedContent)
        throws IOException {

        assertNotNull(payload);

        String entJsonData = extractEntitlementDataJson(payload);
        assertNotNull(entJsonData);

        JsonNode node = mapper.readTree(entJsonData);
        String payloadConsumerUuid = node.get("consumer").asText();

        if (consumerUuid != null) {
            assertEquals(consumerUuid, payloadConsumerUuid);
        }

        if (expectedContent == null || expectedContent.isEmpty()) {
            return;
        }

        Set<String> expectedContentUrls = new HashSet<>();
        for (Content content : expectedContent) {
            expectedContentUrls.add(content.getContentUrl());
        }

        JsonNode payloadContent = node.get("products")
            .get(0)
            .get("content");

        List<JsonNode> contentPaths = payloadContent.findValues("path");

        for (JsonNode contentPath : contentPaths) {
            assertThat(expectedContentUrls)
                .contains(contentPath.asText());
        }
    }

    private String extractEntitlementDataJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        String entDataStartDelimiter = "-----BEGIN ENTITLEMENT DATA-----";
        int start = payload.indexOf(entDataStartDelimiter) + entDataStartDelimiter.length();
        int end = payload.indexOf("-----END ENTITLEMENT DATA-----");
        if (start == -1 || end == -1) {
            return null;
        }

        String entData = payload.substring(start, end)
            .trim();

        // Decode the data
        Base64 base64 = new Base64();
        byte[] compressedBody = base64.decode(entData);

        // Decompress the data
        Inflater decompressor = new Inflater();
        decompressor.setInput(compressedBody);
        byte[] decompressedBody = new byte[48000];
        int resultLength;
        try {
            resultLength = decompressor.inflate(decompressedBody);
        }
        catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
        decompressor.end();

        return new String(decompressedBody, 0, resultLength);
    }

}

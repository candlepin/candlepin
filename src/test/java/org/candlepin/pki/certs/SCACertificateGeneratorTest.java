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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
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
import org.candlepin.test.TestUtil;
import org.candlepin.test.X509HuffmanDecodeUtil;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;


@ExtendWith(MockitoExtension.class)
class SCACertificateGeneratorTest {
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private ContentCurator contentCurator;
    @Mock
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private V3CapabilityCheck v3CapabilityCheck;
    private Configuration config;
    private X509V3ExtensionUtil extensionUtil;
    private SCACertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.config = TestConfig.defaults();
        this.extensionUtil = spy(new X509V3ExtensionUtil(
            this.config, this.entitlementCurator, new Huffman()));
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        BouncyCastleKeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        this.extensionUtil = spy(new X509V3ExtensionUtil(
            config, this.entitlementCurator, new Huffman()));
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();

        when(this.contentAccessCertificateCurator.create(any(SCACertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });

        this.generator = new SCACertificateGenerator(
            this.extensionUtil,
            this.v3CapabilityCheck,
            new EntitlementPayloadGenerator(new ObjectMapper()),
            this.contentAccessCertificateCurator,
            this.serialCurator,
            this.contentCurator,
            this.consumerCurator,
            this.environmentCurator,
            new BouncyCastlePemEncoder(),
            keyPairGenerator,
            new Signer(certificateReader),
            () -> new X509CertificateBuilder(certificateReader, securityProvider, subjectKeyIdentifierWriter)
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
    public void shouldCreateCertificate() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);
        when(this.v3CapabilityCheck.isCertV3Capable(consumer)).thenReturn(true);

        SCACertificate result = this.generator.generate(consumer);
        assertNotNull(result);
    }

    @Test
    public void testGeneratedCertificateIncludesOwnerKeyInContainerContentPath() throws Exception {
        Owner owner = this.createOwner();
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
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Content content = this.createContent();
        Environment env = this.createEnvironment(owner, consumer, content);

        List<String> expected = List.of("/" + owner.getKey() + "/" + env.getName());

        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(env));
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
        Owner owner = this.createOwner()
            .setKey("org-#$%&'()*+,123:;=?@[] \".<>\\^_`{|}~£円ßЯ∑");
        Consumer consumer = this.createConsumer(owner);

        Content content = this.createContent();
        Environment env = this.createEnvironment(owner, consumer, content)
            .setName("env-#$%&'()*+,123:;=?@[] \".<>\\^_`{|}~£円ßЯ∑");

        List<String> expected = List.of(
            "/" + this.urlEncode(owner.getKey()) + "/" + this.urlEncode(env.getName()));

        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(env));
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
        Owner owner = this.createOwner()
            .setKey("segmented/owner/key");

        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Environment env1 = this.createEnvironment(owner, consumer, content1)
            .setName("env1/path/as/name");
        Environment env2 = this.createEnvironment(owner, consumer, content2)
            .setName("env2/path/as/name");
        Environment env3 = this.createEnvironment(owner, consumer, content3)
            .setName("env3/path/as/name");

        List<String> expected = List.of(
            "/" + owner.getKey() + "/" + env1.getName(),
            "/" + owner.getKey() + "/" + env2.getName(),
            "/" + owner.getKey() + "/" + env3.getName());

        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(env1, env2, env3));
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
        Owner owner = this.createOwner()
            .setKey("segmented//owner//key");

        Consumer consumer = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Environment env1 = this.createEnvironment(owner, consumer, content1)
            .setName("/env1/path/as/name");
        Environment env2 = this.createEnvironment(owner, consumer, content2)
            .setName("//env2///path////as/////name");
        Environment env3 = this.createEnvironment(owner, consumer, content3)
            .setName("/ /env3/  /path/  /  /as/   /   /   /name");

        List<String> expected = List.of(
            ("/" + owner.getKey() + "/" + env1.getName()).replaceAll("/[\s/]*/", "/"),
            ("/" + owner.getKey() + "/" + env2.getName()).replaceAll("/[\s/]*/", "/"),
            ("/" + owner.getKey() + "/" + env3.getName()).replaceAll("/[\s/]*/", "/"));

        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(env1, env2, env3));
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

    private Owner createOwner() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvironmentMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        return new Owner()
            .setId("test_owner")
            .setKey("test_owner")
            .setContentAccessModeList(entitlementMode + "," + orgEnvironmentMode)
            .setContentAccessMode(orgEnvironmentMode);
    }

    private Consumer createConsumer(Owner owner) {
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        type.setId("test-id");

        return new Consumer()
            .setUuid("test-consumer-uuid")
            .setId("test-consumer-id")
            .setName("Test Consumer")
            .setUsername("bob")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, X509V3ExtensionUtil.CERT_VERSION)
            .setCapabilities(Set.of(new ConsumerCapability("cert_v3")));
    }

    private Content createContent() {
        String id = TestUtil.randomString(6, TestUtil.CHARSET_NUMERIC);

        return new Content(id)
            .setUuid("test_content-" + id)
            .setName("test_content-" + id)
            .setLabel("label")
            .setType("yum")
            .setVendor("vendor")
            .setContentUrl("/content/dist/rhel/$releasever/$basearch/os")
            .setGpgUrl("gpgUrl")
            .setArches("x86_64")
            .setMetadataExpiration(3200L)
            .setRequiredTags("TAG1,TAG2");
    }

    private Environment createEnvironment(Owner owner, Consumer consumer, Content content) {
        String id = TestUtil.randomString(6, TestUtil.CHARSET_NUMERIC);

        Environment environment = new Environment()
            .setId("test_environment-" + id)
            .setName("test_environment-" + id)
            .setOwner(owner);

        EnvironmentContent ec = new EnvironmentContent()
            .setEnvironment(environment)
            .setContent(content)
            .setEnabled(true);

        environment.addEnvironmentContent(ec);

        consumer.addEnvironment(environment);

        return environment;
    }

}

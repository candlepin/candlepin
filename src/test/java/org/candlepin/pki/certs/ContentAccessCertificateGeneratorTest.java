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

import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.dto.Product;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.Signer;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ContentAccessCertificateGeneratorTest {
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
    private Configuration config;
    private ObjectMapper objectMapper;
    private X509V3ExtensionUtil extensionUtil;
    private ContentAccessCertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.config = TestConfig.defaults();
        this.objectMapper = ObjectMapperFactory.getObjectMapper();
        this.extensionUtil = spy(new X509V3ExtensionUtil(this.config, this.entitlementCurator, objectMapper));
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        BouncyCastleKeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();

        when(this.contentAccessCertificateCurator.create(any(ContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });

        this.generator = new ContentAccessCertificateGenerator(
            this.extensionUtil,
            this.contentAccessCertificateCurator,
            this.serialCurator,
            this.contentCurator,
            this.consumerCurator,
            this.environmentCurator,
            new BouncyCastlePemEncoder(),
            keyPairGenerator,
            new Signer(certificateReader),
            () -> new X509CertificateBuilder(certificateReader, securityProvider)
        );
    }

    @Test
    void shouldCreateCertificate() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);

        ContentAccessCertificate result = this.generator.generate(consumer, owner);

        assertNotNull(result);
    }

    @Test
    public void testContainerContentPathShouldUseOwnerKeyInHosted() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Content content = this.createContent();
        Environment environment = this.createEnvironment(owner, consumer, content);
        when(this.environmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));
        String expectedPath = "/" + owner.getKey() + "/" + environment.getName();

        ContentAccessCertificate output = this.generator.generate(consumer, owner);

        Assertions.assertNotNull(output);
        this.verifyContainerContentPath(expectedPath);
    }

    @Test
    public void testContainerContentPathShouldBeContentPrefixInStandalone() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        org.candlepin.model.Content content = this.createContent();
        Environment environment = this.createEnvironment(owner, consumer, content);
        when(this.environmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));

        String expectedPath = "/" + owner.getKey() + "/" + environment.getName();

        ContentAccessCertificate output = this.generator.generate(consumer, owner);

        Assertions.assertNotNull(output);
        this.verifyContainerContentPath(expectedPath);
    }

    @Test
    public void testContentPrefixIncludesEnvironmentWhenPresent() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        org.candlepin.model.Content content = this.createContent();
        Environment environment = this.createEnvironment(owner, consumer, content);
        when(this.environmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));
        String expectedPrefix = "/" + owner.getKey() + "/" + environment.getName();

        ContentAccessCertificate output = this.generator.generate(consumer, owner);

        Assertions.assertNotNull(output);
        this.verifyContainerContentPath(expectedPrefix);
    }

    private void verifyContainerContentPath(String expected) throws Exception {
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);

        verify(this.extensionUtil, times(1)).getByteExtensions(captor.capture());

        List<org.candlepin.model.dto.Product> products = captor.getValue();

        Assertions.assertNotNull(products);
        assertEquals(1, products.size());

        org.candlepin.model.dto.Product product = products.get(0);
        Assertions.assertNotNull(product);

        List<org.candlepin.model.dto.Content> contents = product.getContent();
        Assertions.assertNotNull(contents);
        assertEquals(1, contents.size());

        org.candlepin.model.dto.Content content = contents.get(0);
        Assertions.assertNotNull(content);

        assertEquals(expected, content.getPath());
    }

    private Owner createOwner() {
        String entitlementMode = ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String orgEnvironmentMode = ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();
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
        Content content = new Content();
        content.setUuid("test_content-uuid");
        content.setId("1234");
        content.setName("test_content");
        content.setLabel("test_content-label");
        content.setType("yum");
        content.setVendor("vendor");
        content.setContentUrl("/content/dist/rhel/$releasever/$basearch/os");
        content.setGpgUrl("gpgUrl");
        content.setArches("x86_64");
        content.setMetadataExpiration(3200L);
        content.setRequiredTags("TAG1,TAG2");

        return content;
    }


    private Environment createEnvironment(Owner owner, Consumer consumer, Content content) {
        int rnd = TestUtil.randomInt();

        Environment environment = new Environment()
            .setId("test_environment-" + rnd)
            .setName("test_environment-" + rnd)
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

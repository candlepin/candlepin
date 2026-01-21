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

import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.cache.AnonymousCertContent;
import org.candlepin.cache.AnonymousCertContentCache;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.Signer;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;


@ExtendWith(MockitoExtension.class)
class AnonymousCertificateGeneratorTest {
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private AnonymousCloudConsumerCurator anonConsumerCurator;
    @Mock
    private AnonymousContentAccessCertificateCurator anonymousCertificateCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private ProductServiceAdapter productAdapter;
    @Mock
    private AnonymousCertContentCache contentCache;
    private DevConfig config;
    private AnonymousCertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        this.generator = this.createGenerator();
    }

    private AnonymousCertificateGenerator createGenerator() throws CertificateException, IOException {
        X509V3ExtensionUtil extensionUtil = spy(new X509V3ExtensionUtil(
            config, this.entitlementCurator, new Huffman()));

        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        BouncyCastleKeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));

        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();

        return new AnonymousCertificateGenerator(
            config,
            extensionUtil,
            new EntitlementPayloadGenerator(new ObjectMapper()),
            this.serialCurator,
            this.anonConsumerCurator,
            this.anonymousCertificateCurator,
            this.productAdapter,
            contentCache,
            new BouncyCastlePemEncoder(),
            keyPairGenerator,
            new Signer(certificateReader),
            () -> new X509CertificateBuilder(
                certificateReader, securityProvider, new BouncyCastleSubjectKeyIdentifierWriter())
        );
    }

    @Test
    public void testGetCertificateForAnonConsumerWithNullAnonymousCloudConsumer() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        assertThrows(IllegalArgumentException.class, () -> this.generator.generate(null));
    }

    @Test
    public void testGetCertificateForAnonConsumerWithStandaloneMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        AnonymousCloudConsumer consumer = createAnonConsumer();

        assertThrows(RuntimeException.class, () -> this.generator.generate(consumer));
    }

    @Test
    public void testGetCertificateForAnonConsumerWithExistingCertificate() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        CertificateSerial serial = new CertificateSerial(12345L);
        serial.setExpiration(Util.tomorrow());
        AnonymousContentAccessCertificate expected = createAnonCertificate(serial);
        AnonymousCloudConsumer consumer = createAnonConsumer();
        consumer.setContentAccessCert(expected);

        AnonymousContentAccessCertificate actual = this.generator.generate(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousContentAccessCertificate::getId)
            .returns(expected.getCert(), AnonymousContentAccessCertificate::getCert)
            .returns(expected.getCreated(), AnonymousContentAccessCertificate::getCreated)
            .returns(expected.getUpdated(), AnonymousContentAccessCertificate::getUpdated)
            .returns(expected.getAnonymousCloudConsumer(),
                AnonymousContentAccessCertificate::getAnonymousCloudConsumer)
            .returns(expected.getKey(), AnonymousContentAccessCertificate::getKey)
            .returns(expected.getSerial(), AnonymousContentAccessCertificate::getSerial);
    }

    @Test
    void shouldReturnCachedCertIfPresent() {
        when(this.anonymousCertificateCurator.create(any(AnonymousContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.contentCache.get(anyCollection())).thenReturn(createCertContent());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        AnonymousContentAccessCertificate result = this.generator.generate(consumer);

        assertNotNull(result);
        verifyNoInteractions(this.productAdapter);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldFailToCreateCertWhenNoProductInfoAvailable(List<ProductInfo> productInfo) {
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThatThrownBy(() -> this.generator.generate(consumer))
            .isInstanceOf(CertificateCreationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    public void shouldThrowIllegalStateExceptionWithInvalidCertDurationConfig(int certDuration) {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, String.valueOf(certDuration));

        assertThrows(IllegalStateException.class, () -> {
            this.createGenerator();
        });
    }

    @Test
    public void shouldThrowIllegalStateExceptionWithNonNumberCertDurationConfig() {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, "bad config");

        assertThrows(IllegalStateException.class, () -> {
            this.createGenerator();
        });
    }

    @Test
    public void shouldThrowIllegalStateExceptionWithCertDurationConfigLargerThanMax() throws Exception {
        String duration = String.valueOf(ConfigProperties.CERT_MAX_DURATION + 1);
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, duration);

        assertThrows(IllegalStateException.class, () -> {
            this.createGenerator();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 21, 30, ConfigProperties.CERT_MAX_DURATION })
    public void shouldSetCertExpirationForNewCert(int certDuration) throws Exception {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, String.valueOf(certDuration));
        this.generator = this.createGenerator();

        List<ProductInfo> productInfo = List.of(
            createProductInfo(), createProductInfo(), createProductInfo()
        );

        when(this.anonymousCertificateCurator.create(any(AnonymousContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId(TestUtil.randomString("id-"))
            .setUuid(TestUtil.randomString("uuid-"))
            .setCloudAccountId(TestUtil.randomString("cloudAccountId-"))
            .setCloudInstanceId(TestUtil.randomString("instanceId-"))
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName("aws");

        AnonymousContentAccessCertificate actual = this.generator.generate(consumer);

        assertThat(actual)
            .isNotNull()
            .extracting(AnonymousContentAccessCertificate::getSerial)
            .isNotNull()
            .extracting(CertificateSerial::getExpiration)
            .asInstanceOf(InstanceOfAssertFactories.DATE)
            .isAfterOrEqualTo(TestUtil.createDateOffset(0, 0, certDuration))
            .isBefore(TestUtil.createDateOffset(0, 0, certDuration + 1));
    }

    @Test
    void shouldCreateNewCertWhenMissing() {
        List<ProductInfo> productInfo = List.of(
            createProductInfo(), createProductInfo(), createProductInfo()
        );
        when(this.anonymousCertificateCurator.create(any(AnonymousContentAccessCertificate.class)))
            .thenAnswer(returnsFirstArg());
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        AnonymousContentAccessCertificate result = this.generator.generate(consumer);

        assertNotNull(result);
    }

    private AnonymousCertContent createCertContent() {
        return new AnonymousCertContent("content", List.of(
            createContent(), createContent(), createContent()
        ));
    }

    private static Content createContent() {
        Content content = new Content();
        content.setId(TestUtil.randomString("test_id"));
        content.setPath("test_path");
        return content;
    }

    private ProductInfo createProductInfo() {
        ContentInfo content = mock(ContentInfo.class);
        doReturn("content-id").when(content).getId();
        doReturn("content-name").when(content).getName();
        doReturn("type").when(content).getType();
        doReturn("label").when(content).getLabel();
        doReturn("vendor").when(content).getVendor();
        doReturn("gpg-url").when(content).getGpgUrl();
        doReturn(12345L).when(content).getMetadataExpiration();
        doReturn("required-tags").when(content).getRequiredTags();
        doReturn("arches").when(content).getArches();
        doReturn("url").when(content).getContentUrl();

        ProductContentInfo prodContent = mock(ProductContentInfo.class);
        doReturn(content).when(prodContent).getContent();

        ProductInfo prodInfo = mock(ProductInfo.class);
        doReturn(List.of(prodContent)).when(prodInfo).getProductContent();

        return prodInfo;
    }

    private AnonymousContentAccessCertificate createAnonCertificate(CertificateSerial serial) {
        int i = TestUtil.randomInt();
        AnonymousContentAccessCertificate expected = new AnonymousContentAccessCertificate();
        expected.setId("id-" + i);
        expected.setKey("key-" + i);
        expected.setCert("cert-" + i);
        expected.setSerial(serial);
        return expected;
    }

    private AnonymousCloudConsumer createAnonConsumer() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setId("id");
        consumer.setUuid("uuid");
        consumer.setCloudAccountId("account-id");
        consumer.setCloudInstanceId("instance-id");
        consumer.setCloudProviderShortName(TestUtil.randomString());
        return consumer;
    }

}

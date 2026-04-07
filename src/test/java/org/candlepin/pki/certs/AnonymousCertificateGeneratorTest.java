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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OidUtil;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import tools.jackson.databind.ObjectMapper;

import java.security.KeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: FIXME: Rewrite this test suite. It's very reliant on mocks and doesn't actually test the generator
// very well.



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private DevConfig config;
    private AnonymousCertificateGenerator generator;

    @BeforeEach
    void setUp() throws CertificateException, KeyException {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        this.generator = this.createGenerator();
        this.mockCuratorMethods();
    }

    private AnonymousCertificateGenerator createGenerator(CryptoManager cryptoManager) {
        X509V3ExtensionUtil extensionUtil = spy(new X509V3ExtensionUtil(
            config, this.entitlementCurator, new Huffman()));

        return new AnonymousCertificateGenerator(
            this.config,
            extensionUtil,
            new EntitlementPayloadGenerator(new ObjectMapper()),
            this.serialCurator,
            this.anonConsumerCurator,
            this.anonymousCertificateCurator,
            this.productAdapter,
            CryptoUtil.getPemEncoder(),
            cryptoManager);
    }

    private AnonymousCertificateGenerator createGenerator() {
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(this.config);
        return this.createGenerator(cryptoManager);
    }

    private void mockCuratorMethods() {
        // Cert serial
        Answer<CertificateSerial> createSerialAnswer = iom -> {
            CertificateSerial serial = iom.getArgument(0);
            serial.setSerial(123L);

            return serial;
        };

        doAnswer(createSerialAnswer).when(this.serialCurator).create(any(CertificateSerial.class));
        doAnswer(createSerialAnswer).when(this.serialCurator).create(any(CertificateSerial.class),
            anyBoolean());

        // anon ca cert curator
        doAnswer(returnsFirstArg()).when(this.anonymousCertificateCurator)
            .create(any(AnonymousContentAccessCertificate.class));
    }

    private void mockServiceAdapterProductLookup(Collection<ProductInfo> productInfo) {
        Map<String, ProductInfo> productMap = productInfo.stream()
            .collect(Collectors.toMap(ProductInfo::getId, Function.identity()));

        doAnswer(iom -> {
            Collection<String> productIds = iom.getArgument(0);

            if (productIds == null) {
                return List.of();
            }

            return productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
        }).when(this.productAdapter).getChildrenByProductIds(anyCollection());
    }

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private void assertCertificateMatchesScheme(AnonymousContentAccessCertificate cert, Scheme scheme)
        throws CertificateException, KeyException {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainer(cert);
        String sigAlgorithmOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        assertEquals(sigAlgorithmOid, x509cert.getSigAlgOID());

        PrivateKey pkey = CryptoUtil.extractPrivateKeyFromContainer(cert);
        String keyAlgorithmOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        String receivedKeyAlgorithmOid = oidUtil.getKeyAlgorithmOid(pkey.getAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        assertEquals(keyAlgorithmOid, receivedKeyAlgorithmOid);

        // Verify the cert was issued by the scheme's CA cert, and isn't self-signed
        assertThat(x509cert.getIssuerX500Principal())
            .isNotEqualTo(x509cert.getSubjectX500Principal())
            .isEqualTo(scheme.certificate().getSubjectX500Principal());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateCertificate(Scheme scheme) throws Exception {
        List<ProductInfo> productInfo = Stream.generate(this::createProductInfo)
            .limit(3)
            .toList();

        List<String> productIds = productInfo.stream()
            .map(ProductInfo::getId)
            .toList();

        this.mockServiceAdapterProductLookup(productInfo);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId(TestUtil.randomString("id-"))
            .setUuid(TestUtil.randomString("uuid-"))
            .setCloudAccountId(TestUtil.randomString("cloudAccountId-"))
            .setCloudInstanceId(TestUtil.randomString("instanceId-"))
            .setProductIds(productIds)
            .setCloudProviderShortName("aws");

        CryptoUtil.configureConsumerForSchemes(consumer, scheme);

        AnonymousCertificateGenerator generator = this.createGenerator();
        AnonymousContentAccessCertificate output = generator.generate(consumer);

        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, AnonymousContentAccessCertificate::getCert)
            .doesNotReturn(null, AnonymousContentAccessCertificate::getKey);

        // Verify we can fetch the ueber cert via curator
        assertThat(consumer.getContentAccessCert())
            .isSameAs(output);

        this.assertCertificateMatchesScheme(output, scheme);

        // cert duration is verified in another test
    }

    @Test
    public void testGenerateCertificateWithNoCryptoCapabilities() throws Exception {
        List<ProductInfo> productInfo = Stream.generate(this::createProductInfo)
            .limit(3)
            .toList();

        List<String> productIds = productInfo.stream()
            .map(ProductInfo::getId)
            .toList();

        this.mockServiceAdapterProductLookup(productInfo);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId(TestUtil.randomString("id-"))
            .setUuid(TestUtil.randomString("uuid-"))
            .setCloudAccountId(TestUtil.randomString("cloudAccountId-"))
            .setCloudInstanceId(TestUtil.randomString("instanceId-"))
            .setProductIds(productIds)
            .setCloudProviderShortName("aws");

        CryptoUtil.configureConsumerForDefaultScheme(consumer);

        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(this.config);
        Scheme defaultScheme = cryptoManager.getDefaultCryptoScheme();

        AnonymousCertificateGenerator generator = this.createGenerator(cryptoManager);
        AnonymousContentAccessCertificate output = generator.generate(consumer);

        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, AnonymousContentAccessCertificate::getCert)
            .doesNotReturn(null, AnonymousContentAccessCertificate::getKey);

        // Verify we can fetch the ueber cert via curator
        assertThat(consumer.getContentAccessCert())
            .isSameAs(output);

        // Verify the cert and key are of the intended scheme
        this.assertCertificateMatchesScheme(output, defaultScheme);

        // cert duration is verified in another test
    }

    @Test
    public void testGenerateCertificateThrowsExceptionWhenNoSchemeSupported() throws Exception {
        List<ProductInfo> productInfo = Stream.generate(this::createProductInfo)
            .limit(3)
            .toList();

        List<String> productIds = productInfo.stream()
            .map(ProductInfo::getId)
            .toList();

        this.mockServiceAdapterProductLookup(productInfo);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId(TestUtil.randomString("id-"))
            .setUuid(TestUtil.randomString("uuid-"))
            .setCloudAccountId(TestUtil.randomString("cloudAccountId-"))
            .setCloudInstanceId(TestUtil.randomString("instanceId-"))
            .setProductIds(productIds)
            .setCloudProviderShortName("aws");

        CryptoUtil.configureConsumerWithNoSelectableScheme(consumer);

        AnonymousCertificateGenerator generator = this.createGenerator();

        assertThrows(CryptoCapabilitiesException.class, () -> generator.generate(consumer));

        assertNull(consumer.getContentAccessCert());
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

        assertThrows(CertificateException.class, () -> this.generator.generate(consumer));
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

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldFailToCreateCertWhenNoProductInfoAvailable(List<ProductInfo> productInfo) {
        when(this.productAdapter.getChildrenByProductIds(anyCollection())).thenReturn(productInfo);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThatThrownBy(() -> this.generator.generate(consumer))
            .isInstanceOf(CertificateException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    public void shouldThrowConfigurationExceptionWithInvalidCertDurationConfig(int certDuration) {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, String.valueOf(certDuration));

        assertThrows(ConfigurationException.class, () -> {
            this.createGenerator();
        });
    }

    @Test
    public void shouldThrowConfigurationExceptionWithNonNumberCertDurationConfig() {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, "bad config");

        assertThrows(ConfigurationException.class, () -> {
            this.createGenerator();
        });
    }

    @Test
    public void shouldThrowConfigurationExceptionWithCertDurationConfigLargerThanMax() throws Exception {
        String duration = String.valueOf(ConfigProperties.CERT_MAX_DURATION + 1);
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, duration);

        assertThrows(ConfigurationException.class, () -> {
            this.createGenerator();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 21, 30, ConfigProperties.CERT_MAX_DURATION })
    public void shouldSetCertExpirationForNewCert(int certDuration) throws Exception {
        this.config.setProperty(ConfigProperties.ANON_CERT_DURATION, String.valueOf(certDuration));
        this.generator = this.createGenerator();

        List<ProductInfo> productInfo = Stream.generate(this::createProductInfo)
            .limit(3)
            .toList();

        List<String> productIds = productInfo.stream()
            .map(ProductInfo::getId)
            .toList();

        this.mockServiceAdapterProductLookup(productInfo);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId(TestUtil.randomString("id-"))
            .setUuid(TestUtil.randomString("uuid-"))
            .setCloudAccountId(TestUtil.randomString("cloudAccountId-"))
            .setCloudInstanceId(TestUtil.randomString("instanceId-"))
            .setProductIds(productIds)
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
    public void testGenerateThrowsExceptionIfKeyPairCannotBeGenerated() throws Exception {
        List<ProductInfo> productInfo = Stream.generate(this::createProductInfo)
            .limit(3)
            .toList();

        List<String> productIds = productInfo.stream()
            .map(ProductInfo::getId)
            .toList();

        this.mockServiceAdapterProductLookup(productInfo);

        KeyPairGenerator mockKeyPairGenerator = mock(KeyPairGenerator.class);
        doThrow(new KeyException("kaboom")).when(mockKeyPairGenerator).generateKeyPair();

        CryptoManager mockCryptoManager = spy(CryptoUtil.getCryptoManager(this.config));
        doReturn(mockKeyPairGenerator).when(mockCryptoManager).getKeyPairGenerator(any(Scheme.class));

        AnonymousCloudConsumer consumer = this.createAnonConsumer()
            .setProductIds(productIds);

        AnonymousCertificateGenerator generator = this.createGenerator(mockCryptoManager);

        assertThrows(CertificateException.class, () -> generator.generate(consumer));

        verify(mockKeyPairGenerator, times(1)).generateKeyPair();
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
        doReturn(TestUtil.randomString("sku-")).when(prodInfo).getId();

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

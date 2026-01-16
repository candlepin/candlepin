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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AbstractCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.RepoType;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.Signer;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509V3ExtensionUtil;

import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERUTF8String;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@ExtendWith(MockitoExtension.class)
public class EntitlementCertificateGeneratorTest {
    private static final List<String> TEST_URLS = List.of(
        "/content/dist/rhel/$releasever/$basearch/os",
        "/content/dist/rhel/$releasever/$basearch/debug",
        "/content/dist/rhel/$releasever/$basearch/source/SRPMS",
        "/content/dist/jboss/source",
        "/content/beta/rhel/$releasever/$basearch/os",
        "/content/beta/rhel/$releasever/$basearch/debug",
        "/content/beta/rhel/$releasever/$basearch/source/SRPMS"
    );
    private static final String CONTENT_LABEL = "label";
    private static final String CONTENT_ID = "1234";
    private static final String CONTENT_TYPE = "yum";
    private static final String CONTENT_TYPE_KICKSTART = "kickstart";
    private static final String CONTENT_TYPE_FILE = "file";
    private static final String CONTENT_TYPE_UNKNOWN = "unknown content type";
    private static final String CONTENT_GPG_URL = "gpgUrl";
    private static final String CONTENT_URL = "/content/dist/rhel/$releasever/$basearch/os";
    private static final String CONTENT_VENDOR = "vendor";
    private static final String CONTENT_NAME = "name";
    private static final Long CONTENT_METADATA_EXPIRE = 3200L;
    private static final String ENTITLEMENT_QUANTITY = "10";
    private static final String REQUIRED_TAGS = "TAG1,TAG2";
    private static final String ARCH_LABEL = "x86_64";


    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator entitlementCertificateCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EnvironmentCurator environmentCurator;

    private Owner owner;
    private I18n i18n;
    private KeyPairGenerator keyPairGenerator;
    private EntitlementCertificateGenerator generator;

    @BeforeEach
    public void setUp() throws CertificateException, IOException {
        this.owner = createOwner();
        when(this.ownerCurator.findOwnerById(owner.getOwnerId())).thenReturn(this.owner);
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        this.keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        when(this.serialCurator.saveOrUpdateAll(anyIterable(), anyBoolean(), anyBoolean()))
            .thenAnswer(invocation -> {
                Iterable<CertificateSerial> argument = invocation.getArgument(0);
                for (CertificateSerial serial : argument) {
                    serial.setSerial((long) TestUtil.randomInt());
                }
                return argument;
            });

        DevConfig config = TestConfig.defaults();
        X509ExtensionUtil x509ExtensionUtil = new X509ExtensionUtil(config);
        ObjectMapper mapper = new ObjectMapper();
        X509V3ExtensionUtil x509V3ExtensionUtil = new X509V3ExtensionUtil(
            config, entitlementCurator, new Huffman());
        PemEncoder pemEncoder = new BouncyCastlePemEncoder();
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        Signer signer = new Signer(certificateReader);
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();
        X509CertificateBuilder certificateBuilder = new X509CertificateBuilder(
            certificateReader, securityProvider, subjectKeyIdentifierWriter);
        this.generator = new EntitlementCertificateGenerator(
            x509ExtensionUtil,
            x509V3ExtensionUtil,
            new EntitlementPayloadGenerator(mapper),
            entitlementCertificateCurator,
            serialCurator,
            ownerCurator,
            entitlementCurator,
            i18n,
            config,
            consumerTypeCurator,
            environmentCurator,
            keyPairGenerator,
            pemEncoder,
            signer,
            () -> certificateBuilder
        );
    }

    @Test
    public void temporaryCertificateForUnmappedGuests() {
        Consumer consumer = createConsumer(owner);
        Product product = createProduct();
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        // unmapped guest pools expire in 7 days
        Instant weekFromNow = Instant.now().plus(7, ChronoUnit.DAYS);
        Date oneHourAfterSevenDays = Date.from(weekFromNow.plus(1, ChronoUnit.HOURS));
        Date oneHourBeforeSevenDays = Date.from(weekFromNow.minus(1, ChronoUnit.HOURS));

        assertThat(result.values())
            .hasSize(1)
            .extracting(EntitlementCertificate::getCertificate)
            .extracting(this::toCert)
            .allSatisfy(input -> {
                assertThatCode(() -> input.checkValidity(oneHourBeforeSevenDays))
                    .doesNotThrowAnyException();
                assertThatThrownBy(() -> input.checkValidity(oneHourAfterSevenDays))
                    .isInstanceOf(CertificateExpiredException.class);
            });
    }

    @Test
    public void certificateShouldUsePreDatedPoolIfOlderThanHour() {
        Consumer consumer = createConsumer(owner);
        Product product = createProduct();
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // pool start date is more than an hour ago, use it
        pool.setStartDate(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertThatSingleCert(result)
            // Note: certificates don't capture milliseconds
            .satisfies(input -> assertThat(pool.getStartDate()).isAfterOrEqualTo(input.getNotBefore()));
    }

    @Test
    public void certificateShouldUsePastHourIfPooIsYoungerThanHour() {
        Consumer consumer = createConsumer(owner);
        Product product = createProduct();
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // pool start date is less than an hour ago, so an hour gets used
        pool.setStartDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertThatSingleCert(result)
            // Note: certificates don't capture milliseconds
            .satisfies(input -> assertThat(pool.getStartDate()).isAfter(input.getNotBefore()));
    }

    @Test
    public void tooManyContentSetsShouldGetRejected() {
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(
            createProductWithContent(200)
        );
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // pool start date is less than an hour ago, so an hour gets used
        pool.setStartDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        assertThatThrownBy(() -> generateCertificate(consumer, entitlement, product))
            .isInstanceOf(CertificateSizeException.class);
    }

    @Test
    public void tooManyContentSetsAcrossMultipleProductsShouldGetRejected() {
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(
            createProductWithContent(100),
            createProductWithContent(100)
        );
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // pool start date is less than an hour ago, so an hour gets used
        pool.setStartDate(Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        assertThatThrownBy(() -> generateCertificate(consumer, entitlement, product))
            .isInstanceOf(CertificateSizeException.class);
    }

    @Test
    public void contentExtensionCreation() {
        Consumer consumer = createConsumer(owner);
        Product productWithContent = createProductWithContent(3);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertContentExtension(getX509Certificate(result), product,
            OID.ChannelFamily.METADATA_EXPIRE, CONTENT_METADATA_EXPIRE.toString());
    }

    @Test
    public void contentExtensionsShouldIncludePromotedContent() {
        Product productWithContent = createProductWithContent(3);
        Environment environment = createEnvironment(productWithContent);
        Consumer consumer = createConsumer(owner);
        when(this.environmentCurator.getConsumerEnvironments(consumer)).thenReturn(List.of(environment));
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertContentExtension(getX509Certificate(result), product, OID.ChannelFamily.LABEL, CONTENT_LABEL);
    }

    @Test
    public void contentExtensionsShouldIncludePromotedContentFromMultipleEnvs() {
        Product productWithContent1 = createProductWithContent(3);
        Product productWithContent2 = createProductWithContent(3);
        Environment environment1 = createEnvironment(productWithContent1);
        Environment environment2 = createEnvironment(productWithContent2);
        Consumer consumer = createConsumer(owner)
            .addEnvironment(environment1)
            .addEnvironment(environment2);
        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(environment1, environment2));
        Set<Product> providedProducts = Set.of(productWithContent1, productWithContent2);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertContentExtension(getX509Certificate(result), product, OID.ChannelFamily.LABEL, CONTENT_LABEL);
    }

    @Test
    public void shouldContainContentRequiredTagsExtension() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertContentExtension(getX509Certificate(result), product,
            OID.ChannelFamily.REQUIRED_TAGS, REQUIRED_TAGS);
    }

    @Test
    public void shouldContainContentUrls() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertContentExtension(getX509Certificate(result), product,
            OID.ChannelFamily.DOWNLOAD_URL, CONTENT_URL);
    }

    @Test
    public void contentExtensionsShouldBeAddedDuringCertificateGeneration() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertContentExtension(certificate, product, OID.ChannelFamily.LABEL, CONTENT_LABEL);
        assertContentExtension(certificate, product, OID.ChannelFamily.GPG_URL, CONTENT_GPG_URL);
        assertContentExtension(certificate, product, OID.ChannelFamily.DOWNLOAD_URL, CONTENT_URL);
        assertContentExtension(certificate, product, OID.ChannelFamily.VENDOR_ID, CONTENT_VENDOR);
        assertContentExtension(certificate, product, OID.ChannelFamily.NAME, CONTENT_NAME);
        assertOrderExtension(certificate, OID.Order.QUANTITY, ENTITLEMENT_QUANTITY);
    }

    @Test
    public void managementDisabledByDefault() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.PROVIDES_MANAGEMENT, "0");
    }

    @Test
    public void managementEnabledByAttribute() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        pool.getProduct().setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "1");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.PROVIDES_MANAGEMENT, "1");
    }

    @Test
    public void stackingIdByAttribute() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "3456");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.STACKING_ID, "3456");
    }

    @Test
    public void virtOnlyByAttribute() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // note that "true" gets recoded to "1" to match other bools in the cert
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.VIRT_ONLY, "1");
    }

    @Test
    public void orderNumberAttribute() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        // note that "true" gets recoded to "1" to match other bools in the cert
        pool.setOrderNumber("this_order");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.NUMBER, "this_order");
    }

    @Test
    public void supportValuesPresentOnCertIfAttributePresent() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        pool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        pool.getProduct().setAttribute(Product.Attributes.SUPPORT_TYPE, "Level 3");
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertOrderExtension(certificate, OID.Order.SUPPORT_LEVEL, "Premium");
        assertOrderExtension(certificate, OID.Order.SUPPORT_TYPE, "Level 3");
    }

    @Test
    public void supportValuesAbsentOnCertIfNoSupportAttributes() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        assertThat(certificate.getNonCriticalExtensionOIDs())
            .isNotEmpty()
            .doesNotContain(
                OID.Order.SUPPORT_TYPE.value(),
                OID.Order.SUPPORT_LEVEL.value()
            );
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.0", "3.1", "3.2", "3.3", "3.4"})
    public void ensureV3CertificateCreationOkWhenConsumerSupportsV3Certs(String certVersion) {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, certVersion);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        byte[] extensionValue = certificate.getExtensionValue(OID.EntitlementVersion.namespace());
        assertThat(extensionValue)
            .asString()
            .contains("3.4");
    }

    @Test
    public void ensureV3CertIsCreatedWhenV3CapabilityPresent() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner, ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        consumer.setCapabilities(Set.of(new ConsumerCapability("cert_v3")));
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        byte[] extensionValue = certificate.getExtensionValue(OID.EntitlementVersion.namespace());
        assertThat(extensionValue)
            .asString()
            .contains("3.4");
    }

    @Test
    public void ensureV1CertIsCreatedWhenV3factNotPresent() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner);
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        byte[] extensionValue = certificate.getExtensionValue(OID.EntitlementVersion.namespace());
        assertThat(extensionValue)
            .isNull();
    }

    @Test
    public void ensureV3CertIsCreatedWhenHypervisor() {
        Product productWithContent = createProductWithContent(3);
        Consumer consumer = createConsumer(owner, ConsumerType.ConsumerTypeEnum.HYPERVISOR);
        consumer.setCapabilities(Set.of(new ConsumerCapability("cert_v3")));
        Set<Product> providedProducts = Set.of(productWithContent);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        X509Certificate certificate = getX509Certificate(result);

        byte[] extensionValue = certificate.getExtensionValue(OID.EntitlementVersion.namespace());
        assertThat(extensionValue)
            .asString()
            .contains("3.4");
    }

    @Test
    public void shouldFilterProductContent() {
        Product productWithContent1 = createProductWithContent(3);
        Product productWithContent2 = createProductWithContent(3);
        Environment environment1 = createEnvironment(productWithContent1);
        Environment environment2 = createEnvironment(productWithContent2);
        Consumer consumer = createConsumer(owner)
            .addEnvironment(environment1)
            .addEnvironment(environment2);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "2.4");
        when(this.environmentCurator.getConsumerEnvironments(consumer))
            .thenReturn(List.of(environment1));
        Set<Product> providedProducts = Set.of(productWithContent1, productWithContent2);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);
        when(this.entitlementCurator.listEntitledProductIds(any(Consumer.class), any(Pool.class), anySet()))
            .thenReturn(Set.of(product.getId()));

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        X509Certificate certificate = getX509Certificate(result);
        Map<String, String> extensions = getExtensions(certificate);
        List<String> expectedContentOIDs = buildOIDs(OID.ChannelFamily.NAME,
            getContentIds(productWithContent1));
        List<String> unExpectedContentOIDs = buildOIDs(OID.ChannelFamily.NAME,
            getContentIds(productWithContent2));

        for (String oid : expectedContentOIDs) {
            assertThat(extensions)
                .containsKey(oid);
        }
        for (String oid : unExpectedContentOIDs) {
            assertThat(extensions)
                .doesNotContainKey(oid);
        }
    }

    @Test
    public void testPrepareV1Extensions() {
        Consumer consumer = createConsumer(owner);
        Product productWithContent1 = createProductWithContent(1);
        Product productWithContent2 = createProductWithContent(1, CONTENT_TYPE_FILE);
        Set<Product> providedProducts = Set.of(productWithContent1, productWithContent2);
        Product product = createProduct().setProvidedProducts(providedProducts);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        String yumContent = getContentIds(productWithContent1).get(0);
        String fileContent = getContentIds(productWithContent2).get(0);
        String yumOid = OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.YUM, yumContent);
        String fileOid = OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.FILE, fileContent);

        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .hasEntrySatisfying(yumOid, s -> assertThat(s).contains(CONTENT_URL))
            .hasEntrySatisfying(fileOid, s -> assertThat(s).contains(CONTENT_URL));
    }

    @Test
    public void testPrepareV1ExtensionsBrandedProduct() {
        Consumer consumer = createConsumer(owner);
        Product product = createProduct()
            .setAttribute(Product.Attributes.BRANDING_TYPE, "os");
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);
        String brandingOid = OID.ProductCertificate.BRAND_TYPE.value(product.getId());

        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .hasEntrySatisfying(brandingOid, s -> assertThat(s).contains("os"));
    }

    @Test
    public void testPrepareV1ExtensionsNoCompatibleArch() {
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.ARCHITECTURE, "x86_64");
        String wrongArches = "s390x,s390,ppc64,ia64";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, wrongArches);
        Product wrongArchProduct = createProduct()
            .setAttribute(Product.Attributes.ARCHITECTURE, "ALL")
            .addContent(wrongArchContent, false);
        Product product = createProduct().setProvidedProducts(Set.of(wrongArchProduct));
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        String contentId = wrongArchContent.getId();
        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.YUM, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.FILE, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.KICKSTART, contentId));
    }

    @Test
    public void testPrepareV1ExtensionsKickstartContent() {
        Consumer consumer = createConsumer(owner);
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE_KICKSTART, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        Product productWithContent = createProduct()
            .setAttribute(Product.Attributes.ARCHITECTURE, "ALL")
            .addContent(wrongArchContent, false);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        String contentId = wrongArchContent.getId();
        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.YUM, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.FILE, contentId))
            .containsKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.KICKSTART, contentId));
    }

    @Test
    public void testPrepareV1ExtensionsFileContent() {
        Consumer consumer = createConsumer(owner);
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE_FILE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        Product productWithContent = createProduct()
            .setAttribute(Product.Attributes.ARCHITECTURE, "ALL")
            .addContent(wrongArchContent, false);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        String contentId = wrongArchContent.getId();
        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.YUM, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.KICKSTART, contentId))
            .containsKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.FILE, contentId));
    }

    @Test
    public void testPrepareV1ExtensionsFileUnknownContentType() {
        Consumer consumer = createConsumer(owner);
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE_UNKNOWN, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        Product productWithContent = createProduct()
            .setAttribute(Product.Attributes.ARCHITECTURE, "ALL")
            .addContent(wrongArchContent, false);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        String contentId = wrongArchContent.getId();
        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.YUM, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.KICKSTART, contentId))
            .doesNotContainKey(OID.ChannelFamily.DOWNLOAD_URL.value(RepoType.FILE, contentId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ns-1", "ns-2", "some long namespace"})
    public void testPrepareV3ExtensionsIncludesEntitlementNamespaceForNamespacedProducts(String namespace) {
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.4");
        Product productWithContent = createProductWithContent(1);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        product.setNamespace(namespace);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .hasEntrySatisfying(OID.EntitlementNamespace.namespace(),
                value -> assertThat(value).contains(namespace));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    public void testPrepareV3ExtensionsOmitsEntitlementNamespaceForGlobalProducts(String namespace) {
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.4");
        Product productWithContent = createProductWithContent(1);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        product.setNamespace(namespace);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        Map<String, String> extensions = getExtensions(getX509Certificate(result));
        assertThat(extensions)
            .doesNotContainKey(OID.EntitlementNamespace.namespace());
    }

    @Test
    public void testDetachedEntitlementDataNotAddedToCertV1() {
        Consumer consumer = createConsumer(owner);
        Product productWithContent = createProductWithContent(1);
        Product product = createProduct().setProvidedProducts(Set.of(productWithContent));
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertThat(getCertificate(result).getCertificate())
            .doesNotContain("ENTITLEMENT DATA");
    }

    @Test
    public void testContentExtensionConsumerNoArchFact() {
        owner.setContentPrefix("");
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.4");
        consumer.setFact(Consumer.Facts.ARCHITECTURE, "x86_64");
        Product product = createProduct();
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, "/single", CONTENT_GPG_URL, "s390");
        product.addContent(wrongArchContent, false);
        for (String url : TEST_URLS) {
            int i = TestUtil.randomInt();
            Content content = createContent(CONTENT_NAME + i, CONTENT_ID + i, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL);

            product.addContent(content, false);
        }

        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertThatCertPayload(getX509Certificate(result))
            .hasSize(TEST_URLS.size())
            .containsExactlyInAnyOrderElementsOf(TEST_URLS);
    }

    @Test
    public void testSingleSegmentContent() {
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.4");
        Product product = createProduct();
        Content content = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, "/single", CONTENT_GPG_URL, ARCH_LABEL);
        product.addContent(content, false);
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        assertThatCertPayload(getX509Certificate(result))
            .hasSize(1)
            .containsExactly("/owner_1/single");
    }

    @Test
    public void testContentExtensionLargeSet() {
        Consumer consumer = createConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.4");
        Product product = createProduct();
        for (int i = 0; i < 550; i++) {
            String url = "/content/dist" + i + "/jboss/source" + i;
            Content content = createContent(CONTENT_NAME + i, CONTENT_ID + i, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL);

            product.addContent(content, false);
        }
        Subscription subscription = createSubscription(product);
        Pool pool = createPool(product, subscription);
        Entitlement entitlement = createEntitlement(pool, consumer, owner);

        Map<String, EntitlementCertificate> result = generateCertificate(consumer, entitlement, product);

        List<String> expected = IntStream.range(0, 550)
            .mapToObj(value -> "/owner_1/content/dist%s/jboss/source%s".formatted(value, value))
            .toList();
        assertThatCertPayload(getX509Certificate(result))
            .hasSize(550)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    private ListAssert<String> assertThatCertPayload(X509Certificate certificate) {
        DEROctetString derValue = (DEROctetString) extensionValue(
            certificate, OID.EntitlementData.namespace());

        byte[] value = derValue == null ? new byte[0] : derValue.getOctets();
        Huffman decode = new Huffman();
        List<String> payload;
        try {
            payload = decode.hydrateContentPackage(value);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return assertThat(payload);
    }

    private ASN1Primitive extensionValue(X509Certificate certificate, String extensionId) {
        try {
            byte[] derValue = certificate.getExtensionValue(extensionId);
            if (derValue == null) {
                return null;
            }
            ASN1Primitive value = ASN1Primitive.fromByteArray(derValue);
            if (value instanceof DEROctetString) {
                byte[] octetString = ((DEROctetString) value).getOctets();
                return DEROctetString.fromByteArray(octetString);
            }
            else if (value instanceof DERUTF8String) {
                return value;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private List<String> buildOIDs(OID.ChannelFamily oid, List<String> contentIds) {
        return contentIds.stream()
            .map(contentId -> oid.value(RepoType.YUM, contentId))
            .toList();
    }

    private void assertContentExtension(X509Certificate certificate, Product product,
        OID.ChannelFamily oidType, String value) {
        for (String contentId : getContentIds(product)) {
            String oid = oidType.value(RepoType.YUM, contentId);
            byte[] extensionValue = certificate.getExtensionValue(oid);

            assertThat(extensionValue)
                .asString()
                .contains(value);
        }
    }

    private void assertOrderExtension(X509Certificate certificate, OID.Order oidType, String value) {
        byte[] extensionValue = certificate.getExtensionValue(oidType.value());

        assertThat(extensionValue)
            .asString()
            .contains(value);
    }

    private Environment createEnvironment(Product product) {
        Environment environment = new Environment()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString())
            .setOwner(this.owner);
        product.getProductContent().stream()
            .map(ProductContent::getContent)
            .forEach(content -> environment.addContent(content, true));
        return environment;
    }

    private List<String> getContentIds(Product product) {
        return Stream.concat(
                Stream.of(product),
                product.getProvidedProducts().stream()
            )
            .map(Product::getProductContent)
            .flatMap(Collection::stream)
            .map(ProductContent::getContentId)
            .toList();
    }

    private Product createProductWithContent(int numberOfContent) {
        return createProductWithContent(numberOfContent, CONTENT_TYPE);
    }

    private Product createProductWithContent(int numberOfContent, String contentType) {
        int id = TestUtil.randomInt();
        Product product = new Product("" + id, "Provided " + id, "variant", "version", ARCH_LABEL, "SVC");
        for (Content content : generateContent(numberOfContent, "PP" + id, contentType)) {
            product.addContent(content, false);
        }
        return product;
    }

    private Set<Content> generateContent(int numberToGenerate, String prefix, String contentType) {
        Set<Content> productContent = new HashSet<>();
        for (int i = 1; i <= numberToGenerate; i++) {
            productContent.add(createContent(prefix + CONTENT_NAME + i,
                "" + i,
                prefix + CONTENT_LABEL + i,
                contentType,
                CONTENT_VENDOR,
                CONTENT_URL,
                CONTENT_GPG_URL,
                ARCH_LABEL));
        }
        return productContent;
    }

    private Content createContent(String name, String id, String label, String type, String vendor,
        String url, String gpgUrl, String arches) {

        TestUtil.createOwner("Example-Corporation");
        Content content = TestUtil.createContent(id + TestUtil.randomInt(10_000), name)
            .setUuid(id + "_uuid")
            .setLabel(label)
            .setType(type)
            .setVendor(vendor)
            .setContentUrl(url)
            .setGpgUrl(gpgUrl)
            .setArches(arches)
            .setRequiredTags(REQUIRED_TAGS)
            .setMetadataExpiration(CONTENT_METADATA_EXPIRE);

        return content;
    }

    private ObjectAssert<X509Certificate> assertThatSingleCert(Map<String, EntitlementCertificate> result) {
        return assertThat(getX509Certificate(result))
            .isNotNull();
    }

    private EntitlementCertificate getCertificate(Map<String, EntitlementCertificate> certificates) {
        return certificates.values().stream()
            .toList().get(0);
    }

    private X509Certificate getX509Certificate(Map<String, EntitlementCertificate> certificates) {
        return certificates.values().stream()
            .map(AbstractCertificate::getCertificate)
            .map(this::toCert)
            .toList().get(0);
    }

    private Map<String, String> getExtensions(X509Certificate certificate) {
        return Stream.concat(
            certificate.getCriticalExtensionOIDs().stream(),
            certificate.getNonCriticalExtensionOIDs().stream()
        ).collect(Collectors.toMap(Function.identity(),
            oid -> new String(certificate.getExtensionValue(oid))));
    }

    private Map<String, EntitlementCertificate> generateCertificate(
        Consumer consumer, Entitlement entitlement, Product product) {
        Map<String, PoolQuantity> poolQuantities = Map.of(
            entitlement.getPool().getId(), new PoolQuantity(entitlement.getPool(), 10)
        );
        Map<String, Entitlement> entitlements = Map.of(
            entitlement.getPool().getId(), entitlement
        );
        Map<String, Product> products = Map.of(
            entitlement.getPool().getId(), product
        );
        return this.generator.generate(consumer, poolQuantities, entitlements, products, true);
    }

    private static Entitlement createEntitlement(Pool pool, Consumer consumer, Owner owner) {
        return new Entitlement()
            .setId("test_ent")
            .setPool(pool)
            .setQuantity(10)
            .setConsumer(consumer)
            .setOwner(owner);
    }

    private X509Certificate toCert(String cert) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8)));
        }
        catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static Pool createPool(Product product, Subscription subscription) {
        Pool pool = new Pool();
        pool.setId(TestUtil.randomString());
        pool.setQuantity(10L);
        pool.setProduct(product);
        pool.setStartDate(subscription.getStartDate());
        pool.setEndDate(subscription.getEndDate());
        return pool;
    }

    private static Subscription createSubscription(Product product) {
        Subscription subscription = TestUtil.createSubscription(null, product);
        subscription.setId("1");
        subscription.setQuantity(10L);
        return subscription;
    }

    private static Product createProduct() {
        String productId = "12345" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, "a product");
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);
        return product;
    }

    private Owner createOwner() {
        return new Owner()
            .setId(TestUtil.randomString())
            .setKey("test_key")
            .setContentPrefix("owner_1");
    }

    private Consumer createConsumer(Owner owner) {
        return createConsumer(owner, ConsumerType.ConsumerTypeEnum.SYSTEM);
    }

    private Consumer createConsumer(Owner owner, ConsumerType.ConsumerTypeEnum type) {
        ConsumerType consumerType = new ConsumerType(type);
        consumerType.setId("test-id");

        Consumer consumer = new Consumer()
            .setUuid("test-consumer")
            .setName("Test Consumer")
            .setUsername("bob")
            .setOwner(owner)
            .setType(consumerType)
            .setCreated(new Date())
            .setUpdated(new Date());

        when(this.consumerTypeCurator.getConsumerType(consumer)).thenReturn(consumerType);

        return consumer;
    }

}

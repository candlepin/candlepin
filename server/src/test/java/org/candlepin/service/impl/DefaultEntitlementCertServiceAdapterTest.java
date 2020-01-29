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
package org.candlepin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.TestingModules;
import org.candlepin.common.config.Configuration;
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
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.pki.impl.JSSProviderLoader;
import org.candlepin.test.TestUtil;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509V3ExtensionUtil;
import org.candlepin.util.X509V3ExtensionUtil.HuffNode;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Named;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18nFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.InflaterOutputStream;

import javax.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DefaultEntitlementCertServiceAdapterTest {

    private static final String CONTENT_LABEL = "label";
    private static final String CONTENT_ID = "1234";
    private static final String CONTENT_ID_FILE = "2456";
    private static final String CONTENT_ID_KICKSTART = "2457";
    private static final String CONTENT_ID_UNKNOWN = "2458";
    private static final String CONTENT_TYPE = "yum";
    private static final String CONTENT_TYPE_KICKSTART = "kickstart";
    private static final String CONTENT_TYPE_FILE = "file";
    private static final String CONTENT_TYPE_UNKNOWN = "unknown content type";
    private static final String CONTENT_GPG_URL = "gpgUrl";
    private static final String CONTENT_URL = "/content/dist/rhel/$releasever/$basearch/os";
    private static final String CONTENT_URL_UNKNOWN_TYPE = "/unknown/content/type";
    private static final String CONTENT_VENDOR = "vendor";
    private static final String CONTENT_NAME = "name";
    private static final Long CONTENT_METADATA_EXPIRE = 3200L;
    private static final String ENTITLEMENT_QUANTITY = "10";
    private static final String REQUIRED_TAGS = "TAG1,TAG2";
    private static final String ARCH_LABEL = "x86_64";

    private DefaultEntitlementCertServiceAdapter certServiceAdapter;
    private X509V3ExtensionUtil v3extensionUtil;

    @Inject private PKIUtility realPKI;
    @Inject private Configuration config;
    @Inject private X509ExtensionUtil extensionUtil;
    @Inject @Named("X509V3ExtensionUtilObjectMapper") private ObjectMapper mapper;

    @Mock private Configuration mockConfig;
    @Mock private X509V3ExtensionUtil mockV3extensionUtil;
    @Mock private X509ExtensionUtil mockExtensionUtil;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private CertificateSerialCurator serialCurator;
    @Mock private EntitlementCurator entCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private KeyPairCurator keyPairCurator;
    @Mock private PKIUtility mockedPKI;
    @Mock private ProductCurator productCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;

    private Consumer consumer;
    private Product product;
    private Product largeContentProduct;
    private Subscription subscription;
    private Entitlement entitlement;
    private Entitlement largeContentEntitlement;
    private Pool pool;
    private Content content;
    private Content kickstartContent;
    private Content fileContent;
    private Content unknownTypeContent;
    private Content noArchContent;
    private Owner owner;
    private Set<Content> superContent;
    private Set<Content> largeContent;

    private static KeyPair keyPair;

    private String[] testUrls = {
        "/content/dist/rhel/$releasever/$basearch/os",
        "/content/dist/rhel/$releasever/$basearch/debug",
        "/content/dist/rhel/$releasever/$basearch/source/SRPMS",
        "/content/dist/jboss/source",
        "/content/beta/rhel/$releasever/$basearch/os",
        "/content/beta/rhel/$releasever/$basearch/debug",
        "/content/beta/rhel/$releasever/$basearch/source/SRPMS"
    };

    static {
        JSSProviderLoader.addProvider();
    }

    @BeforeAll
    public static void keyPair() throws Exception {
        ClassLoader cl = DefaultEntitlementCertServiceAdapterTest.class.getClassLoader();
        InputStream keyStream = cl.getResourceAsStream("test.key");

        keyPair = null;
        assert keyStream != null;
        try (PEMParser reader = new PEMParser(new InputStreamReader(keyStream))) {
            keyPair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) reader.readObject());
        }
    }

    @BeforeEach
    public void setUp() {
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest()
        );
        injector.injectMembers(this);

        v3extensionUtil = new X509V3ExtensionUtil(config, entCurator, mapper);
        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            mockedPKI, extensionUtil, v3extensionUtil,
            mock(EntitlementCertificateCurator.class),
            keyPairCurator, serialCurator, ownerCurator, entCurator,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config, productCurator, this.mockConsumerTypeCurator, this.mockEnvironmentCurator);

        product = TestUtil.createProduct("12345", "a product");
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);

        largeContentProduct = TestUtil.createProduct("67890", "large content product");
        largeContentProduct.setAttribute(Product.Attributes.VERSION, "version");
        largeContentProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        largeContentProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        largeContentProduct.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);

        content = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        content.setMetadataExpiration(CONTENT_METADATA_EXPIRE);
        content.setRequiredTags(REQUIRED_TAGS);

        kickstartContent = createContent(CONTENT_NAME, CONTENT_ID_KICKSTART,
            CONTENT_LABEL, CONTENT_TYPE_KICKSTART, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        kickstartContent.setMetadataExpiration(CONTENT_METADATA_EXPIRE);
        kickstartContent.setRequiredTags(REQUIRED_TAGS);

        fileContent = createContent(CONTENT_NAME, CONTENT_ID_FILE, CONTENT_LABEL,
            CONTENT_TYPE_FILE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        fileContent.setMetadataExpiration(CONTENT_METADATA_EXPIRE);
        fileContent.setRequiredTags(REQUIRED_TAGS);

        unknownTypeContent = createContent(CONTENT_NAME, CONTENT_ID_UNKNOWN, CONTENT_LABEL,
            CONTENT_TYPE_UNKNOWN, CONTENT_VENDOR, CONTENT_URL_UNKNOWN_TYPE,
            CONTENT_GPG_URL, ARCH_LABEL);
        unknownTypeContent.setMetadataExpiration(CONTENT_METADATA_EXPIRE);
        unknownTypeContent.setRequiredTags(REQUIRED_TAGS);

        String emptyArches = "";
        noArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, emptyArches);

        superContent = new HashSet<>();
        int index = 0;
        for (String url : testUrls) {
            ++index;

            superContent.add(createContent(CONTENT_NAME + "-" + index, CONTENT_ID + "-" + index,
                CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL));
        }

        largeContent = new HashSet<>();
        index = 0;
        for (String url : largeTestUrls) {
            ++index;

            largeContent.add(createContent(CONTENT_NAME + "-" + index, CONTENT_ID + "-" + index,
                CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL));
        }

        subscription = TestUtil.createSubscription(null, product);
        subscription.setId("1");
        subscription.setQuantity(1L);

        Subscription largeContentSubscription = TestUtil
            .createSubscription(null, largeContentProduct);
        largeContentSubscription.setId("2");
        largeContentSubscription.setQuantity(1L);

        owner = new Owner();
        owner.setId(TestUtil.randomString());
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);

        pool = new Pool();
        pool.setQuantity(1L);
        pool.setProduct(product);
        pool.setStartDate(subscription.getStartDate());
        pool.setEndDate(subscription.getEndDate());
        Pool largeContentPool = new Pool();
        largeContentPool.setProduct(largeContentProduct);

        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        type.setId("test-id");

        consumer = new Consumer("Test Consumer", "bob", owner, type).setUuid("test-consumer");

        when(this.mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(type);
        when(this.mockConsumerTypeCurator.get(eq(type.getId()))).thenReturn(type);

        entitlement = new Entitlement();
        entitlement.setQuantity(new Integer(ENTITLEMENT_QUANTITY));
        entitlement.setConsumer(consumer);
        entitlement.setPool(pool);
        entitlement.setOwner(owner);
        largeContentEntitlement = new Entitlement();
        largeContentEntitlement.setQuantity(new Integer(ENTITLEMENT_QUANTITY));
        largeContentEntitlement.setConsumer(consumer);
        largeContentEntitlement.setPool(largeContentPool);
        largeContentEntitlement.setOwner(owner);

        product.addContent(content, false);

        Set empty = new HashSet<String>();
        when(productCurator.getPoolProvidedProductUuids(anyString())).thenReturn(empty);

        // when(productAdapter.getProductById(eq(product.getOwner()), eq(product.getId())))
        //     .thenReturn(product);
        // when(productAdapter.getProductById(
        //     eq(largeContentProduct.getOwner()), eq(largeContentProduct.getId()))
        // ).thenReturn(largeContentProduct);
    }

    private Content createContent(String name, String id, String label, String type, String vendor,
        String url, String gpgUrl, String arches) {

        TestUtil.createOwner("Example-Corporation");
        Content content = TestUtil.createContent(id, name);

        content.setUuid(id + "_uuid");
        content.setLabel(label);
        content.setType(type);
        content.setVendor(vendor);
        content.setContentUrl(url);
        content.setGpgUrl(gpgUrl);
        content.setArches(arches);

        return content;
    }

    protected Environment mockEnvironment(Environment environment) {
        if (environment != null) {
            // Ensure the environment has an ID
            if (environment.getId() == null) {
                environment.setId("test-env-" + environment.getName() + "-" + TestUtil.randomInt());
            }

            when(this.mockEnvironmentCurator.get(eq(environment.getId()))).thenReturn(environment);

            doAnswer((Answer<Environment>) invocation -> {
                Object[] args = invocation.getArguments();
                Consumer consumer = (Consumer) args[0];
                EnvironmentCurator curator = (EnvironmentCurator) invocation.getMock();
                Environment environment1 = null;

                if (consumer == null) {
                    throw new IllegalArgumentException("consumer is null");
                }

                if (consumer.getEnvironmentId() != null) {
                    environment1 = curator.get(consumer.getEnvironmentId());

                    if (environment1 == null) {
                        throw new IllegalStateException("No such environment: " +
                            consumer.getEnvironmentId());
                    }
                }

                return environment1;
            }).when(this.mockEnvironmentCurator).getConsumerEnvironment(any(Consumer.class));
        }

        return environment;
    }

    @Test
    public void temporaryCertificateForUnmappedGuests() throws Exception {
        Date now = new Date();
        consumer.setCreated(now);
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

        // Set up an adapter with a real PKIUtil
        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            realPKI, extensionUtil, v3extensionUtil,
            mock(EntitlementCertificateCurator.class),
            keyPairCurator, serialCurator, ownerCurator, entCurator,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config, productCurator, this.mockConsumerTypeCurator, this.mockEnvironmentCurator);

        X509Certificate result = certServiceAdapter.createX509Certificate(consumer, owner, pool,
            entitlement, product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        // unmapped guest pools expire in 7 days, 7 * 24 = 168
        Date oneHourAfterSevenDays = new Date(now.getTime() + 169 * 60 * 60 * 1000);
        Date oneHourBeforeSevenDays = new Date(now.getTime() + 167 * 60 * 60 * 1000);

        result.checkValidity(oneHourBeforeSevenDays);
        assertThrows(CertificateExpiredException.class, () -> result.checkValidity(oneHourAfterSevenDays));
    }

    @Test
    public void predateEntitlementCerts() throws Exception {
        consumer.setCreated(new Date());

        // Set up an adapter with a real PKIUtil
        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            realPKI, extensionUtil, v3extensionUtil,
            mock(EntitlementCertificateCurator.class),
            keyPairCurator, serialCurator, ownerCurator, entCurator,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config, productCurator, this.mockConsumerTypeCurator, this.mockEnvironmentCurator);

        // pool start date is more than an hour ago, use it
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -2);
        pool.setStartDate(cal.getTime());
        X509Certificate result = certServiceAdapter.createX509Certificate(consumer, owner, pool,
            entitlement, product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);
        // cert does not capture the milliseconds. truncating.
        assertEquals(result.getNotBefore().getTime(), pool.getStartDate().getTime() / 1000 * 1000);
        // pool start date is less than an hour ago, so an hour gets used
        cal.add(Calendar.MINUTE, 90);
        pool.setStartDate(cal.getTime());
        result = certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(), getProductModels(product,
            new HashSet<>(), "prefix", entitlement), new BigInteger("1234"), keyPair, true);
        assertTrue(result.getNotBefore().getTime() < pool.getStartDate().getTime() / 1000 * 1000);
    }

    @Test
    public void tooManyContentSetsAcrossMultipleProducts() {
        Set<Product> providedProducts = new HashSet<>();

        Product pp1 = new Product("12346", "Provided 1", "variant", "version", ARCH_LABEL, "SVC");
        for (Content content : generateContent(100, "PP1")) {
            pp1.addContent(content, false);
        }

        providedProducts.add(pp1);

        Product pp2 = new Product("12347", "Provided 2", "variant", "version", ARCH_LABEL, "SVC");
        for (Content content : generateContent(100, "PP2")) {
            pp2.addContent(content, false);
        }

        providedProducts.add(pp2);

        // TODO: Is this even needed anymore?
        // subscription.setProvidedProducts(providedProducts);
        List<org.candlepin.model.dto.Product> productModels = getProductModels(product, providedProducts,
            "prefix", entitlement);
        assertThrows(CertificateSizeException.class, () ->  certServiceAdapter.createX509Certificate(consumer,
            owner, pool, entitlement, product, providedProducts, productModels,
            new BigInteger("1234"), keyPair, true)
        );
    }

    private Set<Content> generateContent(int numberToGenerate, String prefix) {
        Set<Content> productContent = new HashSet<>();
        for (int i = 0; i < numberToGenerate; i++) {
            productContent.add(createContent(prefix + CONTENT_NAME + i,
                prefix + CONTENT_ID + i,
                prefix + CONTENT_LABEL + i,
                CONTENT_TYPE,
                CONTENT_VENDOR,
                CONTENT_URL,
                CONTENT_GPG_URL,
                ARCH_LABEL));
        }
        return productContent;
    }

    @Test
    public void tooManyContentSets() {
        Set<Content> productContent = generateContent(X509ExtensionUtil.V1_CONTENT_LIMIT + 1, "TestContent");

        product.setProductContent(null);
        for (Content content : productContent) {
            product.addContent(content, false);
        }
        assertThrows(CertificateSizeException.class, () -> certServiceAdapter.createX509Certificate(consumer,
            owner, pool, entitlement, product, new HashSet<>(), getProductModels(product, new HashSet<>(),
            "prefix", entitlement), new BigInteger("1234"), keyPair, true)
        );
    }

    @Test
    public void testContentExtentionCreation() throws CertificateSizeException {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), null, new HashMap<>(),
            entitlement.getConsumer(), product);
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));

        // Nullify this, and make sure it's not there.
        content.setMetadataExpiration(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "", new HashMap<>(), entitlement.getConsumer(), product);
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));
    }

    @Test
    public void testContentExtentionIncludesPromotedContent()
        throws CertificateSizeException {

        // Environment, with promoted content:
        Environment e = this.mockEnvironment(new Environment("env1", "Env 1", owner));

        e.getEnvironmentContent().add(new EnvironmentContent(e, content, true));
        this.consumer.setEnvironment(e);

        Map<String, EnvironmentContent> promotedContent = new HashMap<>();
        promotedContent.put(content.getId(), e.getEnvironmentContent().iterator().next());
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), null, promotedContent, entitlement.getConsumer(), product);
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(content.getLabel()));
    }


    @Test
    public void testContentRequiredTagsExtention()  throws CertificateSizeException {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), null, new HashMap<>(),
            entitlement.getConsumer(), product);
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(REQUIRED_TAGS));

        // Nullify this, and make sure it's not there.
        content.setRequiredTags(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "", new HashMap<>(), entitlement.getConsumer(), product);
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS));

        // Empty string, and make sure it's not there.
        content.setRequiredTags("");
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "", new HashMap<>(), entitlement.getConsumer(), product);
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS));
    }

    @Test
    public void testPrefixesShouldBeUsed() throws Exception {
        owner.setContentPrefix("/somePrefix/");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(), getProductModels(product, new HashSet<>(),
            "prefix", entitlement), new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl("/somePrefix" + CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), nullable(String.class));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testPrefixExpandsEnvIfConsumerHasOne() throws Exception {
        owner.setContentPrefix("/someorg/$env/");

        // Setup an environment for the consumer:
        Environment e = this.mockEnvironment(new Environment("env1", "Awesome Environment #1", owner));
        e.getEnvironmentContent().add(new EnvironmentContent(e, content, true));
        this.consumer.setEnvironment(e);

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class), argThat(
            new ListContainsContentUrl("/someorg/Awesome+Environment+%231" + CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), nullable(String.class));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testURLEncoding() throws Exception {
        owner.setContentPrefix("/some org/$env/");

        // Setup an environment for the consumer:
        Environment e = this.mockEnvironment(new Environment("env1", "Awesome Environment #1", owner));
        e.getEnvironmentContent().add(new EnvironmentContent(e, content, true));
        this.consumer.setEnvironment(e);

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class), argThat(
            new ListContainsContentUrl("/some+org/Awesome+Environment+%231" + CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class),
            any(KeyPair.class), any(BigInteger.class), nullable(String.class));
    }

    @Test
    public void testPrefixIgnoresEnvIfConsumerHasNone() throws Exception {
        owner.setContentPrefix("/someorg/$env/");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl("/someorg/$env" + CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), nullable(String.class));
    }

    @Test
    public void testPrefixesAreNotUsedForUeberCertificate() throws Exception {
        owner.setContentPrefix("/somePrefix/");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, false);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), nullable(String.class));
    }

    @Test
    public void testBlankPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix("");
        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), nullable(String.class));
    }

    @Test
    public void testNullPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix(null);

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class), nullable(String.class));
    }

    @Test
    public void testFilterProductContent() {
        Product modProduct = new Product("12345", "a product", "variant", "version", ARCH_LABEL, "SVC");

        // Use this set for successful providing queries:
        Set<Entitlement> successResult = new HashSet<>();
        successResult.add(new Entitlement()); // just need something in there

        Content normalContent = createContent(CONTENT_NAME, CONTENT_ID,
            CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        // Change label to prevent an equals match:
        Content modContent = createContent(CONTENT_NAME, CONTENT_ID + "_2",
            "differentlabel", CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        modContent.setLabel("mod content");
        Set<String> modifiedProductIds = new HashSet<>(Arrays.asList("product1", "product2"));
        modContent.setModifiedProductIds(modifiedProductIds);

        modProduct.addContent(normalContent, false);
        modProduct.addContent(modContent, false);

        // First check that if we have no entitlements providing the modified
        // products,
        // the content set is filtered out:
        // Mod content should get filtered out because we have no ents providing
        // the product it modifies:
        assertEquals(1, extensionUtil.filterProductContent(
            modProduct, consumer, new HashMap<>(), false, new HashSet<>(), false).size());

        // Now mock that we have an entitlement providing one of the modified
        // products,
        // and we should see both content sets included in the cert:
        Set<String> entitledProdIds = new HashSet<>();
        entitledProdIds.add("product2");
        assertEquals(2, extensionUtil.filterProductContent(
            modProduct, consumer, new HashMap<>(), false,
            entitledProdIds, false).size());

        // Make sure that we filter by environment when asked.
        Environment environment = this.mockEnvironment(new Environment());
        consumer.setEnvironment(environment);

        Map<String, EnvironmentContent> promotedContent = new HashMap<>();
        promotedContent.put(normalContent.getId(), new EnvironmentContent(environment, normalContent, true));

        assertEquals(1, extensionUtil.filterProductContent(
            modProduct, consumer, promotedContent, true, entitledProdIds, false).size());
    }

    @Test
    public void contentExtentionsShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentExtensions()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void entitlementQuantityShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsEntitlementExtensions()), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void managementDisabledByDefault() throws Exception {
        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("0")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void managementEnabledByAttribute() throws Exception {
        pool.getProduct().setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "1");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("1")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void stackingIdByAttribute() throws Exception {
        pool.getProduct().setAttribute(Product.Attributes.STACKING_ID, "3456");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsStackingId("3456")), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }
    @Test
    public void virtOnlyByAttribute() throws Exception {
        //note that "true" gets recoded to "1" to match other bools in the cert
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "true");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsVirtOnlyKey("1")), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void orderNumberAttribute() throws Exception {
        //note that "true" gets recoded to "1" to match other bools in the cert
        pool.setOrderNumber("this_order");
        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsOrderNumberKey("this_order")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void supportValuesPresentOnCertIfAttributePresent() throws Exception {

        pool.getProduct().setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        pool.getProduct().setAttribute(Product.Attributes.SUPPORT_TYPE, "Level 3");

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportLevel("Premium")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportType("Level 3")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    protected DefaultEntitlementCertServiceAdapter initCertServiceAdapter() {
        return new DefaultEntitlementCertServiceAdapter(
            mockedPKI, mockExtensionUtil, mockV3extensionUtil,
            mock(EntitlementCertificateCurator.class),
            keyPairCurator, serialCurator, ownerCurator, entCurator,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            mockConfig, productCurator, this.mockConsumerTypeCurator, this.mockEnvironmentCurator);
    }

    @Test
    public void ensureV3CertificateCreationOkWhenConsumerSupportsV3Dot1Certs()
        throws Exception {

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        subscription.getProduct().setAttribute(Product.Attributes.RAM, "4");
        subscription.getProduct().setAttribute(Product.Attributes.ROLES, "role1, role2 ");

        DefaultEntitlementCertServiceAdapter entAdapter = this.initCertServiceAdapter();

        entAdapter.createX509Certificate(consumer, owner, pool, entitlement, product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);
    }

    @Test
    public void supportValuesAbsentOnCertIfNoSupportAttributes()
        throws Exception {

        certServiceAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportLevel()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportType()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class));
    }

    @Test
    public void ensureV3CertIsCreatedWhenEnableCertV3ConfigIsTrue() throws Exception {
        consumer.setFact("system.certificate_version", "3.0");

        DefaultEntitlementCertServiceAdapter entAdapter = this.initCertServiceAdapter();

        entAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);
        verify(mockV3extensionUtil).getExtensions();
        verify(mockV3extensionUtil).getByteExtensions(eq(product), any(List.class),
            nullable(String.class), any(Map.class));
        verifyZeroInteractions(mockExtensionUtil);
    }

    @Test
    public void ensureV3CertIsCreatedWhenV3CapabilityPresent() throws Exception {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-id");

        consumer.setType(ctype);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);

        Set<ConsumerCapability> set = new HashSet<>();
        set.add(new ConsumerCapability(consumer, "cert_v3"));
        consumer.setCapabilities(set);

        DefaultEntitlementCertServiceAdapter entAdapter = this.initCertServiceAdapter();

        entAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);
        verify(mockV3extensionUtil).getExtensions();
        verify(mockV3extensionUtil).getByteExtensions(eq(product), any(List.class), nullable(String.class),
            any(Map.class));
        verifyZeroInteractions(mockExtensionUtil);
    }


    @Test
    public void ensureV1CertIsCreatedWhenV3factNotPresent() throws Exception {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-id");

        consumer.setType(ctype);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);

        DefaultEntitlementCertServiceAdapter entAdapter = this.initCertServiceAdapter();

        entAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);

        // Verify v1
        verify(mockExtensionUtil).consumerExtensions(eq(consumer));
        verifyZeroInteractions(mockV3extensionUtil);
    }

    @Test
    public void ensureV3CertIsCreatedWhenHypervisor() throws Exception {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.HYPERVISOR);
        ctype.setId("test-id");

        consumer.setType(ctype);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);

        DefaultEntitlementCertServiceAdapter entAdapter = this.initCertServiceAdapter();

        entAdapter.createX509Certificate(consumer, owner, pool, entitlement,
            product, new HashSet<>(),
            getProductModels(product, new HashSet<>(), "prefix", entitlement),
            new BigInteger("1234"), keyPair, true);
        verify(mockV3extensionUtil).getExtensions();
        verify(mockV3extensionUtil).getByteExtensions(eq(product), any(List.class),
            nullable(String.class), any(Map.class));
        verifyZeroInteractions(mockExtensionUtil);
    }

    @Test
    public void testCleanUpPrefixNoChange() throws Exception {
        String[] prefixes = {"/",
                             "/some_prefix/",
                             "/some-prefix/",
                             "/some.prefix/",
                             "/Some1Prefix2/"};

        for (String prefix : prefixes) {
            assertEquals(prefix, certServiceAdapter.cleanUpPrefix(prefix));
        }
    }

    private Boolean extMapHasContentType(Content cont, Map<String, String> extMap,
        String contentType) {
        return extMap.containsKey("1.3.6.1.4.1.2312.9.2." +
            cont.getId() + "." + contentType + ".1");
    }

    private Boolean extMapHasProductBrandType(Product product, Map<String, String> extMap) {
        return extMap.containsKey("1.3.6.1.4.1.2312.9.1." +
            product.getId() + "." + "5");
    }

    private Boolean extMapProductBrandTypeMatches(Product product, Map<String, String> extMap,
        String brandType) {
        String brandTypeOid = "1.3.6.1.4.1.2312.9.1." +
            product.getId() + "." + "5";
        String extBrandType = extMap.get(brandTypeOid);

        return extBrandType.equals(brandType);
    }

    @Test
    public void testPrepareV1Extensions() {
        Set<Product> products = new HashSet<>();

        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));
        assertTrue(map.containsKey(CONTENT_URL));

        // do we have a yum content type oid
        assertTrue(extMapHasContentType(content, extMap, "1"));
        assertFalse(extMapHasContentType(content, extMap, "2"));
    }

    @Test
    public void testPrepareV1ExtensionsBrandedProduct() {
        Set<Product> products = new HashSet<>();

        product.setAttribute(Product.Attributes.BRANDING_TYPE, "os");
        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));
        assertTrue(extMapHasProductBrandType(product, extMap));
        assertTrue(extMapProductBrandTypeMatches(product, extMap, "os"));
    }

    @Test
    public void testPrepareV1ExtensionsNoCompatibleArch() {
        Set<Product> products = new HashSet<>();

        // product with no compatible content, but marked as 'ALL' arch
        Product wrongArchProduct = TestUtil.createProduct("12345", "a product");
        wrongArchProduct.setAttribute(Product.Attributes.VERSION, "version");
        wrongArchProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        wrongArchProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        wrongArchProduct.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        // no x86_64, ie ARCH_LABEL
        String wrongArches = "s390x,s390,ppc64,ia64";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, wrongArches);

        wrongArchProduct.addContent(wrongArchContent, false);

        products.add(wrongArchProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertFalse(isEncodedContentValid(map));
        assertFalse(map.containsKey(CONTENT_URL));
        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(kickstartContent, extMap, "null"));
    }

    @Test
    public void testPrepareV1ExtensionsKickstartContent() {
        Set<Product> products = new HashSet<>();

        // product with a kickstart content
        Product kickstartProduct = TestUtil.createProduct("12345", "a product");
        kickstartProduct.setAttribute(Product.Attributes.VERSION, "version");
        kickstartProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        kickstartProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        kickstartProduct.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        kickstartProduct.addContent(kickstartContent, false);

        products.add(kickstartProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));
        assertTrue(map.containsKey(CONTENT_TYPE_KICKSTART));
        assertTrue(map.containsKey(CONTENT_URL));

        assertFalse(extMapHasContentType(kickstartContent, extMap, "1"));
        assertFalse(extMapHasContentType(kickstartContent, extMap, "2"));
        assertTrue(extMapHasContentType(kickstartContent, extMap, "3"));
        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(kickstartContent, extMap, "null"));
    }

    @Test
    public void testPrepareV1ExtensionsFileContent() {
        Set<Product> products = new HashSet<>();

        // product with a kickstart content
        Product fileProduct = TestUtil.createProduct("12345", "a product");
        fileProduct.setAttribute(Product.Attributes.VERSION, "version");
        fileProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        fileProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        fileProduct.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        fileProduct.addContent(fileContent, false);
        products.clear();
        products.add(fileProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));
        assertTrue(map.containsKey(CONTENT_TYPE_FILE));
        assertTrue(map.containsKey(CONTENT_URL));

        assertFalse(extMapHasContentType(fileContent, extMap, "1"));
        assertTrue(extMapHasContentType(fileContent, extMap, "2"));
        assertFalse(extMapHasContentType(fileContent, extMap, "3"));
        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(fileContent, extMap, "null"));
    }


    @Test
    public void testPrepareV1ExtensionsFileUnknownContentType() {
        Set<Product> products = new HashSet<>();

        // product with a kickstart content
        Product unknownContentTypeProduct = TestUtil.createProduct("12345", "a product");
        unknownContentTypeProduct.setAttribute(Product.Attributes.VERSION, "version");
        unknownContentTypeProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        unknownContentTypeProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        unknownContentTypeProduct.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);

        unknownContentTypeProduct.addContent(unknownTypeContent, false);
        products.clear();
        products.add(unknownContentTypeProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer,
            entitlement.getQuantity(), "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        // we skip content of unknown type for v1 certs
        assertFalse(isEncodedContentValid(map));
        assertFalse(map.containsKey(CONTENT_URL_UNKNOWN_TYPE));
        assertFalse(map.containsKey(CONTENT_TYPE_UNKNOWN));

        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "1"));
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "2"));
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "3"));

        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "null"));
    }

    @Test
    public void testPrepareV1ExtensionsKnownAndUnknownContentTypes() {
        Set<Product> products = new HashSet<>();

        // product with a kickstart content
        Product product = TestUtil.createProduct("12345", "a product");
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);

        product.addContent(content, false);
        product.addContent(fileContent, false);
        product.addContent(kickstartContent, false);
        product.addContent(unknownTypeContent, false);

        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, pool, consumer, entitlement.getQuantity(),
            "", null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        // we skip content of unknown type for v1 certs, but other
        // content should still get added
        assertTrue(isEncodedContentValid(map));

        // other contents are in there
        assertTrue(map.containsKey(CONTENT_URL));

        // unknown is not
        assertFalse(map.containsKey(CONTENT_TYPE_UNKNOWN));
        assertFalse(map.containsKey(CONTENT_URL_UNKNOWN_TYPE));

        // we have a yum,file, and kickstart content and
        // we do not have any unknown content types
        assertTrue(extMapHasContentType(content, extMap, "1"));
        assertTrue(extMapHasContentType(fileContent, extMap, "2"));
        assertTrue(extMapHasContentType(kickstartContent, extMap, "3"));

        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "1"));
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "2"));
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "3"));

        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(unknownTypeContent, extMap, "null"));
    }

    private List<org.candlepin.model.dto.Product> getProductModels(Product sku, Set<Product> providedProducts,
        String prefix, Entitlement e) {

        return v3extensionUtil.createProducts(
            sku, providedProducts, prefix, new HashMap<>(),
            e.getConsumer(), e.getPool());
    }

    @Test
    public void testPrepareV3EntitlementData() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);
        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setFact("uname.machine", "x86_64");

        Product product = pool.getProduct();

        product.setAttribute(Product.Attributes.WARNING_PERIOD, "20");
        product.setAttribute(Product.Attributes.SOCKETS, "4");
        product.setAttribute(Product.Attributes.RAM, "8");
        product.setAttribute(Product.Attributes.CORES, "4");
        product.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "true");
        product.setAttribute(Product.Attributes.STACKING_ID, "45678");
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        product.setAttribute(Product.Attributes.SUPPORT_LEVEL, "slevel");
        product.setAttribute(Product.Attributes.SUPPORT_TYPE, "stype");
        pool.setAccountNumber("account1");
        pool.setContractNumber("contract1");
        pool.setOrderNumber("order1");
        for (ProductContent pc : product.getProductContent()) {
            pc.setEnabled(false);
        }

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), (X509V3ExtensionUtil.CERT_VERSION));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);
        assertEquals(data.get("consumer"), "test-consumer");
        assertEquals(data.get("quantity"), 10);

        Map<String, Object> subs = (Map<String, Object>) data.get("subscription");
        assertEquals(subs.get("sku"), subscription.getProduct().getId());
        assertEquals(subs.get("name"), subscription.getProduct().getName());
        assertEquals(subs.get("warning"), 20);
        assertEquals(subs.get("sockets"), 4);
        assertEquals(subs.get("ram"), 8);
        assertEquals(subs.get("cores"), 4);
        assertTrue((Boolean) subs.get("management"));
        assertEquals(subs.get("stacking_id"), "45678");
        assertTrue((Boolean) subs.get("virt_only"));

        Map<String, Object> service = (Map<String, Object>) subs.get("service");
        assertEquals(service.get("level"), "slevel");
        assertEquals(service.get("type"), "stype");
        Map<String, Object> order = (Map<String, Object>) data.get("order");
        assertEquals(order.get("number"), pool.getOrderNumber());
        assertEquals(((Integer) order.get("quantity")).intValue(), (long) subscription.getQuantity());
        assertNotNull(order.get("start"));
        assertNotNull(order.get("end"));
//        assertEquals(order.get("contract"), subscription.getContractNumber());
//        assertEquals(order.get("account"), subscription.getAccountNumber());

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents;
        for (Map<String, Object> prod : prods) {
            assertEquals(prod.get("id"), product.getId());
            assertEquals(prod.get("name"), product.getName());
            assertEquals(prod.get("version"), product.getAttributeValue(Product.Attributes.VERSION));
            String arch = product.hasAttribute(Product.Attributes.ARCHITECTURE) ?
                product.getAttributeValue(Product.Attributes.ARCHITECTURE) : "";
            StringTokenizer st = new StringTokenizer(arch, ",");
            while (st.hasMoreElements()) {
                assertTrue(((List) prod.get("architectures")).contains(st.nextElement()));
            }

            contents = (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {
                assertEquals(cont.get("id"), CONTENT_ID);
                assertEquals(cont.get("name"), CONTENT_NAME);
                assertEquals(cont.get("type"), CONTENT_TYPE);
                assertEquals(cont.get("label"), CONTENT_LABEL);
                assertEquals(cont.get("vendor"), CONTENT_VENDOR);
                assertEquals(cont.get("gpg_url"), CONTENT_GPG_URL);
                assertEquals(cont.get("path"), "prefix" + CONTENT_URL);
                assertFalse((Boolean) cont.get("enabled"));
                assertEquals(cont.get("metadata_expire"), 3200);

                List<String> arches = new ArrayList<>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);

                String rTags = content.getRequiredTags();
                st = new StringTokenizer(rTags, ",");
                while (st.hasMoreElements()) {
                    assertTrue(((List) cont.get("required_tags")).contains(st.nextElement()));
                }
            }
        }
    }

    private void setupEntitlements(String consumerArch, String certVersion) {
        consumer.setFact("system.certificate_version", certVersion);
        consumer.setFact("uname.machine", consumerArch);

        ProductData pdata = subscription.getProduct();

        pdata.setAttribute(Product.Attributes.WARNING_PERIOD, "20");
        pdata.setAttribute(Product.Attributes.SOCKETS, "4");
        pdata.setAttribute(Product.Attributes.RAM, "8");
        pdata.setAttribute(Product.Attributes.CORES, "4");
        pdata.setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "true");
        pdata.setAttribute(Product.Attributes.STACKING_ID, "45678");

        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "true");
        pdata.setAttribute(Product.Attributes.SUPPORT_LEVEL, "slevel");
        pdata.setAttribute(Product.Attributes.SUPPORT_TYPE, "stype");

        subscription.setAccountNumber("account1");
        subscription.setContractNumber("contract1");
        subscription.setOrderNumber("order1");

        if (pdata.getProductContent() != null) {
            for (ProductContentData pcd : pdata.getProductContent()) {
                pcd.setEnabled(false);
            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoConsumerArch() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);

        setupEntitlements(null, X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ExtensionWrapper> extensions = certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>) Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents;
        for (Map<String, Object> prod : prods) {
            String arch = product.hasAttribute(Product.Attributes.ARCHITECTURE) ?
                product.getAttributeValue(Product.Attributes.ARCHITECTURE) : "";
            StringTokenizer st = new StringTokenizer(arch, ",");
            while (st.hasMoreElements()) {
                assertTrue(((List) prod.get("architectures")).contains(st.nextElement()));
            }

            contents = (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {
                assertEquals(cont.get("id"), CONTENT_ID);
                assertEquals(cont.get("path"), "prefix" + CONTENT_URL);
                assertFalse((Boolean) cont.get("enabled"));

                // since we dont know the consumer arch, we dont filter
                // any contents out
                List<String> arches = new ArrayList<>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);
            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoContentArch() throws IOException {
        Set<Product> products = new HashSet<>();

        // our content with no arch should inherit this arch
        Product inheritedArchProduct = TestUtil.createProduct("12345", "a product");
        inheritedArchProduct.setAttribute(Product.Attributes.VERSION, "version");
        inheritedArchProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        inheritedArchProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        inheritedArchProduct.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);
        inheritedArchProduct.addContent(noArchContent, false);
        products.add(inheritedArchProduct);

        setupEntitlements(ARCH_LABEL, X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ExtensionWrapper> extensions = certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), (X509V3ExtensionUtil.CERT_VERSION));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents;
        for (Map<String, Object> prod : prods) {

            String arch = product.hasAttribute(Product.Attributes.ARCHITECTURE) ?
                product.getAttributeValue(Product.Attributes.ARCHITECTURE) : "";
            StringTokenizer st = new StringTokenizer(arch, ",");
            while (st.hasMoreElements()) {
                assertTrue(((List) prod.get("architectures")).contains(st.nextElement()));
            }

            contents = (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {

                // We dont set an arch on Content, but we inherit it
                // from product, so the arch should match ARCH_LABEL,
                // that the Product was created with
                List<String> arches = new ArrayList<>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);

            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoCompatibleArch() throws IOException {
        Set<Product> products = new HashSet<>();

        // product with no compatible content, but marked as 'ALL' arch
        Product wrongArchProduct = TestUtil.createProduct("12345", "a product");
        wrongArchProduct.setAttribute(Product.Attributes.VERSION, "version");
        wrongArchProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        wrongArchProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        wrongArchProduct.setAttribute(Product.Attributes.ARCHITECTURE, "ALL");

        // no x86_64, ie ARCH_LABEL
        String wrongArches = "s390x,s390,ppc64,ia64";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, wrongArches);

        wrongArchProduct.addContent(wrongArchContent, false);
        products.clear();
        products.add(wrongArchProduct);
        setupEntitlements(ARCH_LABEL, X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ExtensionWrapper> extensions = certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), (X509V3ExtensionUtil.CERT_VERSION));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents;
        for (Map<String, Object> prod : prods) {

            String arch = wrongArchProduct.hasAttribute(Product.Attributes.ARCHITECTURE) ?
                wrongArchProduct.getAttributeValue(Product.Attributes.ARCHITECTURE) : "";
            StringTokenizer st = new StringTokenizer(arch, ",");
            while (st.hasMoreElements()) {
                assertTrue(((List) prod.get("architectures")).contains(st.nextElement()));
            }

            contents = (List<Map<String, Object>>) prod.get("content");
            assertTrue(contents.isEmpty());
        }
    }


    @Test
    public void testPrepareV3EntitlementDataForDefaults() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setFact("uname.machine", "x86_64");

        subscription.getProduct().setAttribute(Product.Attributes.WARNING_PERIOD, "0");
        subscription.getProduct().setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "false");
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "false");
        for (ProductContent pc : product.getProductContent()) {
            pc.setEnabled(true);
        }

        Set<X509ExtensionWrapper> extensions = certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), (X509V3ExtensionUtil.CERT_VERSION));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>) Util.fromJson(stringValue , Map.class);
        assertEquals(data.get("consumer"), "test-consumer");

        // each has been set to the default and should not be populated in the cert
        Map<String, Object> subs = (Map<String, Object>) data.get("subscription");
        assertNull(subs.get("warning"));
        assertNull(subs.get("management"));
        assertNull(subs.get("virt_only"));

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        for (Map<String, Object> prod : prods) {
            List<Map<String, Object>> contents = (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {
                assertNull(cont.get("enabled"));
            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataForBooleans() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);

        consumer.setUuid("test-consumer");
        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setFact("uname.machine", "x86_64");

        pool.getProduct().setAttribute(Product.Attributes.MANAGEMENT_ENABLED, "1");
        entitlement.getPool().setAttribute(Product.Attributes.VIRT_ONLY, "1");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions();
        Map<String, X509ExtensionWrapper> map = new HashMap<>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), (X509V3ExtensionUtil.CERT_VERSION));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(
                getProductModels(product, products, "prefix", entitlement),
                consumer, pool, entitlement.getQuantity());
        String stringValue;
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>) Util.fromJson(stringValue , Map.class);
        assertEquals(data.get("consumer"), "test-consumer");

        // each has been set to the default and should not be populated in the cert
        Map<String, Object> subs = (Map<String, Object>) data.get("subscription");
        assertTrue((Boolean) subs.get("management"));
        assertTrue((Boolean) subs.get("virt_only"));
    }

    @Test
    public void testDetachedEntitlementDataNotAddedToCertV1() throws Exception {
        when(keyPairCurator.getConsumerKeyPair(any(Consumer.class))).thenReturn(keyPair);
        when(serialCurator.saveOrUpdateAll(any(), anyBoolean(), anyBoolean()))
            .then(new Answer<Iterable<CertificateSerial>>() {

                @Override
                public Iterable<CertificateSerial> answer(InvocationOnMock invocationOnMock)
                    throws Throwable {
                    Iterable<CertificateSerial> certificateSerials = invocationOnMock.getArgument(0);
                    certificateSerials
                        .forEach(certificateSerial -> certificateSerial.setId(Util.generateUniqueLong()));
                    return certificateSerials;
                }
            });

        when(mockedPKI
            .createX509Certificate(any(String.class), any(Set.class), any(Set.class), any(Date.class),
                any(Date.class), any(KeyPair.class), any(BigInteger.class), nullable(String.class)))
            .thenReturn(mock(X509Certificate.class));
        when(mockedPKI.getPemEncoded(any(X509Certificate.class))).thenReturn("".getBytes());
        when(mockedPKI.getPemEncoded(any(PrivateKey.class))).thenReturn("".getBytes());

        final CertificateSerial serial = mock(CertificateSerial.class);
        when(serial.getId()).thenReturn(1L);

        pool.setId("poolId");

        EntitlementCertificate cert = certServiceAdapter.generateEntitlementCert(entitlement, product);

        assertTrue(!cert.getCert().contains("ENTITLEMENT DATA"));
    }

    @Test
    public void testContentExtension() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);

        product.setProductContent(null);
        for (Content content : superContent) {
            product.addContent(content, false);
        }

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setFact("uname.machine", "x86_64");

        Set<X509ByteExtensionWrapper> byteExtensions = certServiceAdapter.prepareV3ByteExtensions(
            product, getProductModels(product, products, "prefix", entitlement), "prefix", null);

        Map<String, X509ByteExtensionWrapper> byteMap = new HashMap<>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList;
        try {
            contentSetList = v3extensionUtil.hydrateContentPackage(
                byteMap.get("1.3.6.1.4.1.2312.9.7").getValue());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(7, contentSetList.size());
        for (String url : testUrls) {
            assertTrue(contentSetList.contains("/prefix" + url));
        }
    }

    @Test
    public void testContentExtensionConsumerNoArchFact() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(product);
        // set of content for an incompatible arch, which should
        // be in the cert, since this consumer has no arch fact therefore
        // should match everything
        String wrongArches = "s390";
        String noArchUrl = "/some/place/nice";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL, CONTENT_TYPE,
            CONTENT_VENDOR, noArchUrl, CONTENT_GPG_URL, wrongArches);

        product.setProductContent(null);
        for (Content content : superContent) {
            product.addContent(content, false);
        }
        product.addContent(wrongArchContent, false);

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ByteExtensionWrapper> byteExtensions = certServiceAdapter.prepareV3ByteExtensions(
            product, getProductModels(product, products, "prefix", entitlement),
            "prefix", null);
        Map<String, X509ByteExtensionWrapper> byteMap = new HashMap<>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList;
        try {
            contentSetList = v3extensionUtil.hydrateContentPackage(
                byteMap.get("1.3.6.1.4.1.2312.9.7").getValue());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(8, contentSetList.size());
        for (String url : testUrls) {
            assertTrue(contentSetList.contains("/prefix" + url));
        }
        // verify our new wrong arch url is in there
        assertTrue(contentSetList.contains("/prefix" + noArchUrl));
    }

    @Test
    public void testSpecificLargeContent() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(largeContentProduct);

        largeContentProduct.setProductContent(null);
        for (Content content : largeContent) {
            largeContentProduct.addContent(content, false);
        }

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ByteExtensionWrapper> byteExtensions = certServiceAdapter.prepareV3ByteExtensions(
            product, getProductModels(product, products, "prefix", largeContentEntitlement),
            "prefix", null);
        Map<String, X509ByteExtensionWrapper> byteMap = new HashMap<>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList;
        try {
            contentSetList = v3extensionUtil.hydrateContentPackage(
                byteMap.get("1.3.6.1.4.1.2312.9.7").getValue());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(largeContent.size(), contentSetList.size());
        for (String url : largeTestUrls) {
            assertTrue(contentSetList.contains("/prefix" + url));
        }
        List<String> testList = Arrays.asList(largeTestUrls);
        for (String url : contentSetList) {
            assertTrue(testList.contains(url.substring(7)));
        }
    }

    @Test
    public void testSingleSegmentContent() throws IOException {
        Set<Product> products = new HashSet<>();
        products.add(largeContentProduct);

        largeContentProduct.setProductContent(null);
        largeContentProduct.addContent(createContent(CONTENT_NAME, CONTENT_ID,
            CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, "/single", CONTENT_GPG_URL, ARCH_LABEL), false);

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);

        Set<X509ByteExtensionWrapper> byteExtensions = certServiceAdapter.prepareV3ByteExtensions(
            product, getProductModels(product, products, "", largeContentEntitlement),
            "", null);
        Map<String, X509ByteExtensionWrapper> byteMap = new HashMap<>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList;
        try {
            contentSetList = v3extensionUtil.hydrateContentPackage(
                byteMap.get("1.3.6.1.4.1.2312.9.7").getValue());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue(contentSetList.contains("/single"));
    }

    @Test
    public void testContentExtensionLargeSet() throws IOException {
        Set<Product> products = new HashSet<>();
        Product extremeProduct = TestUtil.createProduct("12345", "a product");
        extremeProduct.setAttribute(Product.Attributes.VERSION, "version");
        extremeProduct.setAttribute(Product.Attributes.VARIANT, "variant");
        extremeProduct.setAttribute(Product.Attributes.TYPE, "SVC");
        extremeProduct.setAttribute(Product.Attributes.ARCHITECTURE, ARCH_LABEL);
        products.add(extremeProduct);

        for (int i = 0; i < 550; i++) {
            String url = "/content/dist" + i + "/jboss/source" + i;
            Content content = createContent(CONTENT_NAME + i, CONTENT_ID + i, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL);

            extremeProduct.addContent(content, false);
        }

        consumer.setUuid("test-consumer");
        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setFact("uname.machine", "x86_64");

        certServiceAdapter.prepareV3Extensions();
        Set<X509ByteExtensionWrapper> byteExtensions = certServiceAdapter.prepareV3ByteExtensions(
            extremeProduct, getProductModels(extremeProduct, products, "prefix", entitlement),
            "prefix", null);
        Map<String, X509ByteExtensionWrapper> byteMap = new HashMap<>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList;
        try {
            contentSetList = v3extensionUtil.hydrateContentPackage(
                byteMap.get("1.3.6.1.4.1.2312.9.7").getValue());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(550, contentSetList.size());
        for (int i = 0; i < 550; i++) {
            String url = "/content/dist" + i + "/jboss/source" + i;
            assertTrue(contentSetList.contains("/prefix" + url));
        }
    }

    @Test
    public void testPathTreeCommonHeadAndTail() {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            org.candlepin.model.dto.Content cont = new org.candlepin.model.dto.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" + i + "/leg/foot/heel");
            contentList.add(cont);
        }
        PathNode location = v3extensionUtil.makePathTree(contentList, v3extensionUtil.new PathNode());
        v3extensionUtil.printTree(location, 0);
        assertEquals(location.getChildren().size(), 1);
        assertEquals(location.getChildren().get(0).getName(), "head");
        location = location.getChildren().get(0).getConnection();
        assertEquals(location.getChildren().size(), 1);
        assertEquals(location.getChildren().get(0).getName(), "neck");
        location = location.getChildren().get(0).getConnection();
        assertEquals(location.getChildren().size(), 1);
        assertEquals(location.getChildren().get(0).getName(), "shoulders");
        location = location.getChildren().get(0).getConnection();
        assertEquals(location.getChildren().size(), 20);

        // find the common footer nodes and make sure they are merged.
        long legId = -1;
        long footId = -1;
        long heelId = -1;
        for (NodePair np : location.getChildren()) {
            // np is a "heart" pair
            assertTrue(np.getName().startsWith("heart"));

            // now waist node
            PathNode waist = np.getConnection();
            assertEquals(waist.getChildren().size(), 1);
            assertTrue(waist.getChildren().get(0).getName().startsWith("waist"));

            // go to "leg" node
            PathNode leg = waist.getChildren().get(0).getConnection();
            if (legId == -1) {
                legId = leg.getId();
            }
            else {
                assertEquals(leg.getId(), legId);
            }
            assertEquals(leg.getChildren().size(), 1);
            assertEquals(leg.getChildren().get(0).getName(), "leg");

            // go to "foot" node
            PathNode foot = leg.getChildren().get(0).getConnection();
            if (footId == -1) {
                footId = foot.getId();
            }
            else {
                assertEquals(foot.getId(), footId);
            }
            assertEquals(foot.getChildren().size(), 1);
            assertEquals(foot.getChildren().get(0).getName(), "foot");

            // go to "heel" node
            PathNode heel = foot.getChildren().get(0).getConnection();
            if (heelId == -1) {
                heelId = heel.getId();
            }
            else {
                assertEquals(heel.getId(), heelId);
            }
            assertEquals(heel.getChildren().size(), 1);
            assertEquals(heel.getChildren().get(0).getName(), "heel");
        }
    }

    @Test
    public void testPathTreeSortsChildNodesAlphabetically() {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();

        org.candlepin.model.dto.Content contentA = new org.candlepin.model.dto.Content();
        contentA.setPath("/AAA");
        org.candlepin.model.dto.Content contentB = new org.candlepin.model.dto.Content();
        contentB.setPath("/BBB");
        org.candlepin.model.dto.Content contentC = new org.candlepin.model.dto.Content();
        contentC.setPath("/CCC");

        contentList.add(contentB);
        contentList.add(contentC);
        contentList.add(contentA);

        PathNode location = v3extensionUtil.makePathTree(contentList, v3extensionUtil.new PathNode());

        assertEquals(3, location.getChildren().size(), 3);
        assertEquals("AAA", location.getChildren().get(0).getName());
        assertEquals("BBB", location.getChildren().get(1).getName());
        assertEquals("CCC", location.getChildren().get(2).getName());
    }

    @Test
    public void testPathDictionary() throws IOException {
        List<org.candlepin.model.dto.Content> contentList = new ArrayList<>();
        org.candlepin.model.dto.Content cont;

        for (int i = 0; i < 20; i++) {
            cont = new org.candlepin.model.dto.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" + i + "/leg/foot/heel");
            contentList.add(cont);
        }

        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/leg");
        contentList.add(cont);
        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/foot");
        contentList.add(cont);
        cont = new org.candlepin.model.dto.Content();
        cont.setPath("/head/neck/shoulders/chest/torso/leg");
        contentList.add(cont);

        PathNode location = v3extensionUtil.makePathTree(contentList, v3extensionUtil.new PathNode());
        List<String> nodeStrings = v3extensionUtil.orderStrings(location);
        assertEquals(nodeStrings.size(), 48);

        // frequency sorted
        assertEquals(nodeStrings.get(46), "foot");
        assertEquals(nodeStrings.get(47), "leg");
    }

    @Test
    public void testHuffNodeTrieCreationAndTreeSearch() {
        String[] paths = {"01110", "01111", "0110", "1110", "1111", "010", "100", "101", "110", "00"};
        List<HuffNode> huffNodes = new ArrayList<>();
        List<Object> members = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Object o = new Object();
            huffNodes.add(v3extensionUtil.new HuffNode(o, i));
            members.add(o);
        }
        HuffNode trieParent = v3extensionUtil.makeTrie(huffNodes);
        v3extensionUtil.printTrie(trieParent, 0);
        assertEquals(trieParent.getWeight(), 55);
        assertEquals(trieParent.getLeft().getWeight(), 22);
        assertEquals(trieParent.getRight().getWeight(), 33);

        int idx = 0;
        for (Object o : members) {
            assertEquals(paths[idx], v3extensionUtil.findHuffPath(trieParent, o));
            Object found = v3extensionUtil.findHuffNodeValueByBits(trieParent, paths[idx++]);
            assertEquals(o, found);
        }
    }

    private String processPayload(byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InflaterOutputStream ios = new InflaterOutputStream(baos);
        ios.write(payload);
        ios.finish();
        return baos.toString();
    }

    private Map<String, X509ExtensionWrapper> getEncodedContent(
        Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, X509ExtensionWrapper> encodedContent = new HashMap<>();

        for (X509ExtensionWrapper ext : contentExtensions) {
            encodedContent.put(ext.getValue(), ext);
        }

        return encodedContent;
    }

    private Map<String, String> getEncodedContentMap(
        Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, String> encodedContentMap = new HashMap<>();

        for (X509ExtensionWrapper ext : contentExtensions) {
            encodedContentMap.put(ext.getOid(), ext.getValue());
        }
        return encodedContentMap;

    }

    private boolean isEncodedContentValid(Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(contentExtensions);

        return isEncodedContentValid(encodedContent);
    }

    private boolean isEncodedContentValid(Map<String, X509ExtensionWrapper> encodedContent) {

        return encodedContent.containsKey(CONTENT_LABEL) &&
            // encodedContent.containsKey(CONTENT_ENABLED) &&
            encodedContent.containsKey(CONTENT_GPG_URL) &&
            encodedContent.containsKey(CONTENT_URL) &&
            encodedContent.containsKey(CONTENT_VENDOR) &&
            encodedContent.containsKey(CONTENT_NAME);
    }

    class ListContainsContentExtensions implements ArgumentMatcher<Set<X509ExtensionWrapper>> {

        @Override
        public boolean matches(Set<X509ExtensionWrapper> argument) {
            return isEncodedContentValid(argument);
        }
    }

    static class ListContainsEntitlementExtensions implements ArgumentMatcher<Set<X509ExtensionWrapper>> {

        public boolean matches(Set<X509ExtensionWrapper> argument) {
            Map<String, X509ExtensionWrapper> encodedContent = new HashMap<>();

            for (X509ExtensionWrapper ext : argument) {
                encodedContent.put(ext.getOid(), ext);
            }

            return encodedContent.containsKey("1.3.6.1.4.1.2312.9.4.11") &&
                encodedContent.get("1.3.6.1.4.1.2312.9.4.11")
                    .getValue()
                    .equals(ENTITLEMENT_QUANTITY);
        }
    }

    static class OidMatcher implements ArgumentMatcher<Set<X509ExtensionWrapper>> {

        protected String value;
        protected String oid;

        public OidMatcher(String value, String oid) {
            this.value = value;
            this.oid = oid;
        }

        public boolean matches(Set<X509ExtensionWrapper> argument) {
            Map<String, X509ExtensionWrapper> encodedContent = new HashMap<>();

            for (X509ExtensionWrapper ext : argument) {
                encodedContent.put(ext.getOid(), ext);
            }

            return encodedContent.containsKey(oid) && encodedContent.get(oid).getValue().equals(value);
        }
    }

    static class ListContainsProvidesManagement extends OidMatcher {
        public ListContainsProvidesManagement(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.14");
        }
    }

    static class ListContainsSupportLevel extends OidMatcher {
        public ListContainsSupportLevel(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.15");
        }
    }

    static class ListContainsSupportType extends OidMatcher {
        public ListContainsSupportType(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.16");
        }
    }

    static class ListContainsStackingId extends OidMatcher {
        public ListContainsStackingId(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.17");
        }
    }
    static class ListContainsVirtOnlyKey extends OidMatcher {
        public ListContainsVirtOnlyKey(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.18");
        }
    }
    static class ListContainsOrderNumberKey extends OidMatcher {
        public ListContainsOrderNumberKey(String value) {
            super(value, "1.3.6.1.4.1.2312.9.4.2");
        }
    }

    static class ListContainsContentUrl extends OidMatcher {
        public ListContainsContentUrl(String value, String contentID) {
            super(value, "1.3.6.1.4.1.2312.9.2." + contentID + ".1.6");
        }
    }

    static class ListContainsContentTypeYum extends OidMatcher {
        public ListContainsContentTypeYum(String value, String contentID) {
            super(value, "1.3.6.1.4.1.2312.9.2." + contentID + ".1");
        }
    }

    static class ListContainsContentTypeFile extends OidMatcher {
        public ListContainsContentTypeFile(String value, String contentID) {
            super(value, "1.3.6.1.4.1.2312.9.2." + contentID + ".2");
        }
    }

    static class ListContainsContentTypeKickstart extends OidMatcher {
        public ListContainsContentTypeKickstart(String value, String contentID) {
            super(value, "1.3.6.1.4.1.2312.9.2." + contentID + ".3");
        }
    }

    static class OidAbsentMatcher implements ArgumentMatcher<Set<X509ExtensionWrapper>> {
        protected String oid;

        public OidAbsentMatcher(String oid) {
            this.oid = oid;
        }

        @Override
        public boolean matches(Set<X509ExtensionWrapper> argument) {
            Map<String, X509ExtensionWrapper> encodedContent = new HashMap<>();

            for (X509ExtensionWrapper ext : argument) {
                encodedContent.put(ext.getOid(), ext);
            }

            return !encodedContent.containsKey(oid);
        }
    }

    static class ListDoesNotContainSupportLevel extends OidAbsentMatcher {
        public ListDoesNotContainSupportLevel() {
            super("1.3.6.1.4.1.2312.9.4.15");
        }
    }

    static class ListDoesNotContainSupportType extends OidAbsentMatcher {
        public ListDoesNotContainSupportType() {
            super("1.3.6.1.4.1.2312.9.4.16");
        }
    }

    private String[] largeTestUrls = {
        "/content/beta/rhel/server/6/$releasever/$basearch/sap/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/sap/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/sap/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/sap/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/sap/source/SRPMS",
        "/content/dist/rhel/server/5/$releasever/$basearch/sap/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/sap/debug",
        "/content/beta/rhel/server/5/$releasever/$basearch/sap/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/sap/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/sap/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/sap/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/sap/debug",
        "/content/beta/rhel/server/5/$releasever/$basearch/source/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/optional/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/cf-tools/1.0/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/vt/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/cf-tools/1.0/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/optional/source/SRPMS",
        "/content/dist/rhel/server/5/$releasever/$basearch/debug",
        "/content/dist/rhel/server/6/$releasever/$basearch/supplementary/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/supplementary/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/optional/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/source/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/productivity/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/cf-tools/1.0/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/supplementary/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/vt/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/optional/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/debug",
        "/content/dist/rhel/server/6/$releasever/$basearch/supplementary/iso",
        "/content/beta/rhel/server/5/$releasever/$basearch/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/subscription-asset-manager/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/subscription-asset-manager/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/source/iso",
        "/content/beta/rhel/server/5/$releasever/$basearch/vt/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/subscription-asset-manager/1/debug",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/productivity/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/cf-tools/1.0/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/cf-tools/1.0/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/source/iso",
        "/content/dist/rhel/server/6/$releasever/$basearch/cf-tools/1.0/source/SRPMS",
        "/content/dist/rhel/server/5/$releasever/$basearch/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/productivity/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/vt/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/cf-tools/1.0/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/supplementary/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/cf-tools/1.0/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/iso",
        "/content/beta/rhel/server/5/$releasever/$basearch/iso",
        "/content/beta/rhel/server/6/$releasever/$basearch/supplementary/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/supplementary/iso",
        "/content/beta/rhel/server/5/$releasever/$basearch/cf-tools/1.0/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/optional/os",
        "/content/beta/rhel/server/6/$releasever/$basearch/supplementary/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/subscription-asset-manager/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/supplementary/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/iso",
        "/content/dist/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/debug",
        "/content/dist/rhel/server/6/$releasever/$basearch/subscription-asset-manager/1/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/debug",
        "/content/beta/rhel/server/6/$releasever/$basearch/supplementary/source/SRPMS",
        "/content/dist/rhel/server/5/$releasever/$basearch/vt/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/os",
        "/content/beta/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/subscription-asset-manager/1/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/supplementary/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/vt/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/supplementary/debug",
        "/content/dist/rhel/server/6/$releasever/$basearch/optional/debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/cf-tools/1.0/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/source/SRPMS",
        "/content/rhb/rhel/client/6/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/workstation/5/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/client/5/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/client/5/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/client/5/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/computenode/6/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/server/6/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/workstation/6/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/server/6/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/workstation/5/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/computenode/6/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/workstation/6/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/workstation/6/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/computenode/6/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/client/6/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/server/6/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/workstation/5/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/client/6/$releasever/$basearch/devtoolset/source/SRPMS",
        "/content/rhb/rhel/server/5/$releasever/$basearch/devtoolset/debug",
        "/content/rhb/rhel/server/5/$releasever/$basearch/devtoolset/os",
        "/content/rhb/rhel/server/5/$releasever/$basearch/devtoolset/source/SRPMS"
    };
}

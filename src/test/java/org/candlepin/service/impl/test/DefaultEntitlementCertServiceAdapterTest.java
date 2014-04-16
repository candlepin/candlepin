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
package org.candlepin.service.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.InflaterOutputStream;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.Subscription;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509V3ExtensionUtil;
import org.candlepin.util.X509V3ExtensionUtil.HuffNode;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18nFactory;

/**
 * DefaultEntitlementCertServiceAdapter
 */
@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
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
    @Mock
    private PKIUtility mockedPKI;
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private ProductServiceAdapter productAdapter;
    @Mock
    private EntitlementCurator entCurator;
    @Mock
    private KeyPairCurator keyPairCurator;

    @Mock
    private Consumer consumer;

    private X509ExtensionUtil extensionUtil;
    private X509V3ExtensionUtil v3extensionUtil;
    private Product product;
    private Product largeContentProduct;
    private Subscription subscription;
    private Subscription largeContentSubscription;
    private Entitlement entitlement;
    private Entitlement largeContentEntitlement;
    private Pool pool;
    private Pool largeContentPool;
    private Content content;
    private Content kickstartContent;
    private Content fileContent;
    private Content unknownTypeContent;
    private Content noArchContent;
    private Owner owner;
    private Set<Content> superContent;
    private Set<Content> largeContent;

    private String[] testUrls = {"/content/dist/rhel/$releasever/$basearch/os",
        "/content/dist/rhel/$releasever/$basearch/debug",
        "/content/dist/rhel/$releasever/$basearch/source/SRPMS",
        "/content/dist/jboss/source",
        "/content/beta/rhel/$releasever/$basearch/os",
        "/content/beta/rhel/$releasever/$basearch/debug",
        "/content/beta/rhel/$releasever/$basearch/source/SRPMS"};

    @Before
    public void setUp() {
        Config config = new CandlepinCommonTestConfig();
        extensionUtil = new X509ExtensionUtil(config);
        v3extensionUtil = new X509V3ExtensionUtil(config, entCurator);

        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            mockedPKI, extensionUtil, v3extensionUtil,
            mock(EntitlementCertificateCurator.class), keyPairCurator,
            serialCurator, productAdapter, entCurator,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config);


        product = new Product("12345", "a product", "variant", "version",
            ARCH_LABEL, "SVC");
        largeContentProduct = new Product("67890", "large content product", "variant",
            "version", ARCH_LABEL, "SVC");

        content = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        content.setMetadataExpire(CONTENT_METADATA_EXPIRE);
        content.setRequiredTags(REQUIRED_TAGS);

        kickstartContent = createContent(CONTENT_NAME, CONTENT_ID_KICKSTART,
            CONTENT_LABEL, CONTENT_TYPE_KICKSTART, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        kickstartContent.setMetadataExpire(CONTENT_METADATA_EXPIRE);
        kickstartContent.setRequiredTags(REQUIRED_TAGS);

        fileContent = createContent(CONTENT_NAME, CONTENT_ID_FILE, CONTENT_LABEL,
            CONTENT_TYPE_FILE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, ARCH_LABEL);
        fileContent.setMetadataExpire(CONTENT_METADATA_EXPIRE);
        fileContent.setRequiredTags(REQUIRED_TAGS);

        unknownTypeContent = createContent(CONTENT_NAME, CONTENT_ID_UNKNOWN, CONTENT_LABEL,
            CONTENT_TYPE_UNKNOWN, CONTENT_VENDOR, CONTENT_URL_UNKNOWN_TYPE,
            CONTENT_GPG_URL, ARCH_LABEL);
        unknownTypeContent.setMetadataExpire(CONTENT_METADATA_EXPIRE);
        unknownTypeContent.setRequiredTags(REQUIRED_TAGS);

        String emptyArches = "";
        noArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, emptyArches);

        superContent = new HashSet<Content>();
        for (String url : testUrls) {
            superContent.add(createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL));
        }

        largeContent = new HashSet<Content>();
        for (String url : largeTestUrls) {
            largeContent.add(createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL));
        }

        subscription = new Subscription(null, product, new HashSet<Product>(),
            1L, new Date(), new Date(), new Date());
        subscription.setId("1");
        largeContentSubscription = new Subscription(null, largeContentProduct,
            new HashSet<Product>(),
            1L, new Date(), new Date(), new Date());
        largeContentSubscription.setId("2");

        owner = new Owner();

        pool = new Pool();
        pool.setQuantity(1L);
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());
        pool.setStartDate(subscription.getStartDate());
        pool.setEndDate(subscription.getEndDate());
        largeContentPool = new Pool();
        largeContentPool.setProductId(largeContentProduct.getId());
        largeContentPool.setProductName(largeContentProduct.getName());

        when(consumer.getType()).thenReturn(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
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

        product.setContent(Collections.singleton(content));
    }

    private Content createContent(String name, String id, String label,
        String type, String vendor, String url, String gpgUrl, String arches) {
        Content c = new Content(name, id, label, type, vendor, url, gpgUrl, arches);

        return c;
    }

    @Test(expected = CertificateSizeException.class)
    public void tooManyContentSetsAcrossMultipleProducts() throws Exception {
        Set<Product> providedProducts = new HashSet<Product>();
        Product pp1 = new Product("12346", "Provided 1", "variant", "version",
            ARCH_LABEL, "SVC");
        pp1.setContent(generateContent(100, "PP1"));
        providedProducts.add(pp1);

        Product pp2 = new Product("12347", "Provided 2", "variant", "version",
            ARCH_LABEL, "SVC");
        pp2.setContent(generateContent(100, "PP2"));
        providedProducts.add(pp2);

        subscription.setProvidedProducts(providedProducts);

        certServiceAdapter.createX509Certificate(entitlement,
            product, providedProducts, new BigInteger("1234"), keyPair(), true);
    }

    private Set<Content> generateContent(int numberToGenerate, String prefix) {
        Set<Content> productContent = new HashSet<Content>();
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

    @Test(expected = CertificateSizeException.class)
    public void tooManyContentSets() throws Exception {
        Set<Content> productContent = generateContent(X509ExtensionUtil.V1_CONTENT_LIMIT +
            1, "TestContent");

        product.setContent(productContent);
        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
    }

    @Test
    public void testContentExtentionCreation() throws CertificateSizeException {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil
            .contentExtensions(product.getProductContent(), null,
                new HashMap<String, EnvironmentContent>(), entitlement.getConsumer());
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));

        // Nullify this, and make sure it's not there.
        content.setMetadataExpire(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "",
            new HashMap<String, EnvironmentContent>(), entitlement.getConsumer());
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));
    }

    @Test
    public void testContentExtentionIncludesPromotedContent()
        throws CertificateSizeException {

        // Environment, with promoted content:
        Environment e = new Environment("env1", "Env 1", owner);
        e.getEnvironmentContent().add(new EnvironmentContent(e, content.getId(), true));
        when(entitlement.getConsumer().getEnvironment()).thenReturn(e);

        Map<String, EnvironmentContent> promotedContent =
            new HashMap<String, EnvironmentContent>();
        promotedContent.put(content.getId(), e.getEnvironmentContent().iterator().next());
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil
            .contentExtensions(product.getProductContent(), null,
                promotedContent, entitlement.getConsumer());
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(content.getLabel()));
    }


    @Test
    public void testContentRequiredTagsExtention()  throws CertificateSizeException {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil
            .contentExtensions(product.getProductContent(), null,
                new HashMap<String, EnvironmentContent>(), entitlement.getConsumer());
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(REQUIRED_TAGS.toString()));

        // Nullify this, and make sure it's not there.
        content.setRequiredTags(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "",
            new HashMap<String, EnvironmentContent>(), entitlement.getConsumer());
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS.toString()));

        // Empty string, and make sure it's not there.
        content.setRequiredTags("");
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "",
            new HashMap<String, EnvironmentContent>(), entitlement.getConsumer());
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS.toString()));
    }

    @Test
    public void testPrefixesShouldBeUsed() throws Exception {
        owner.setContentPrefix("/somePrefix/");

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl("/somePrefix" + CONTENT_URL,
                CONTENT_ID)), any(Set.class), any(Date.class), any(Date.class),
            any(KeyPair.class), any(BigInteger.class), any(String.class));
    }

    @Test
    public void testPrefixExpandsEnvIfConsumerHasOne() throws Exception {
        owner.setContentPrefix("/someorg/$env/");

        // Setup an environment for the consumer:
        Environment e = new Environment("env1", "Awesome Environment #1", owner);
        e.getEnvironmentContent().add(new EnvironmentContent(e, content.getId(), true));
        when(entitlement.getConsumer().getEnvironment()).thenReturn(e);

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl("/someorg/Awesome+Environment+%231" +
                CONTENT_URL, CONTENT_ID)), any(Set.class), any(Date.class),
                any(Date.class), any(KeyPair.class), any(BigInteger.class),
                any(String.class));
    }

    @Test
    public void testURLEncoding() throws Exception {
        owner.setContentPrefix("/some org/$env/");

        // Setup an environment for the consumer:
        Environment e = new Environment("env1", "Awesome Environment #1", owner);
        e.getEnvironmentContent().add(new EnvironmentContent(e, content.getId(), true));
        when(entitlement.getConsumer().getEnvironment()).thenReturn(e);

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl("/some+org/Awesome+Environment+%231" +
                CONTENT_URL, CONTENT_ID)), any(Set.class), any(Date.class), any(Date.class),
                any(KeyPair.class), any(BigInteger.class), any(String.class));
    }

    @Test
    public void testPrefixIgnoresEnvIfConsumerHasNone() throws Exception {
        owner.setContentPrefix("/someorg/$env/");

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl("/someorg/$env" + CONTENT_URL,
                CONTENT_ID)), any(Set.class), any(Date.class), any(Date.class),
            any(KeyPair.class), any(BigInteger.class), any(String.class));
    }

    @Test
    public void testPrefixesAreNotUsedForUeberCertificate() throws Exception {
        owner.setContentPrefix("/somePrefix/");

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), false);

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL,
                CONTENT_ID)), any(Set.class), any(Date.class), any(Date.class),
            any(KeyPair.class), any(BigInteger.class), any(String.class));
    }

    @Test
    public void testBlankPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix("");

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), any(String.class));
    }

    @Test
    public void testNullPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix(null);

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Set.class), any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), any(String.class));
    }

    @Test
    public void testFilterProductContent() {
        Product modProduct = new Product("12345", "a product", "variant",
            "version", ARCH_LABEL, "SVC");

        // Use this set for successful providing queries:
        Set<Entitlement> successResult = new HashSet<Entitlement>();
        successResult.add(new Entitlement()); // just need something in there

        Content normalContent = createContent(CONTENT_NAME, CONTENT_ID,
            CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        // Change label to prevent an equals match:
        Content modContent = createContent(CONTENT_NAME, CONTENT_ID + "_2",
            "differentlabel", CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL, ARCH_LABEL);
        modContent.setLabel("mod content");
        Set<String> modifiedProductIds = new HashSet<String>(
            Arrays.asList(new String[]{ "product1", "product2" }));
        modContent.setModifiedProductIds(modifiedProductIds);

        modProduct.addContent(normalContent);
        modProduct.addContent(modContent);

        // First check that if we have no entitlements providing the modified
        // products,
        // the content set is filtered out:
        when(
            this.entCurator.listProviding(any(Consumer.class), eq("product1"),
                any(Date.class), any(Date.class))).thenReturn(
                    new HashSet<Entitlement>());
        // Mod content should get filtered out because we have no ents providing
        // the product it modifies:
        assertEquals(1,
            extensionUtil.filterProductContent(modProduct, entitlement, entCurator,
                new HashMap<String, EnvironmentContent>(), false)
                .size());

        // Now mock that we have an entitlement providing one of the modified
        // products,
        // and we should see both content sets included in the cert:
        when(
            this.entCurator.listProviding(any(Consumer.class), eq("product2"),
                any(Date.class), any(Date.class))).thenReturn(successResult);
        assertEquals(2,
            extensionUtil.filterProductContent(modProduct, entitlement, entCurator,
                new HashMap<String, EnvironmentContent>(), false)
                .size());

        // Make sure that we filter by environment when asked.
        Environment environment = new Environment();
        when(consumer.getEnvironment()).thenReturn(environment);

        Map<String, EnvironmentContent> promotedContent =
            new HashMap<String, EnvironmentContent>();
        promotedContent.put(normalContent.getId(), new EnvironmentContent(environment,
            "content", true));

        assertEquals(1,
            extensionUtil.filterProductContent(modProduct, entitlement, entCurator,
                promotedContent, true)
                .size());
    }

    @Test
    public void contentExtentionsShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentExtensions()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void entitlementQuantityShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsEntitlementExtensions()), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void managementDisabledByDefault() throws Exception {

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("0")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void managementEnabledByAttribute() throws Exception {

        pool.addProductAttribute(new ProductPoolAttribute("management_enabled", "1", "p"));
        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("1")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void stackingIdByAttribute() throws Exception {

        pool.addProductAttribute(new ProductPoolAttribute("stacking_id", "3456", "p"));
        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsStackingId("3456")), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }
    @Test
    public void virtOnlyByAttribute() throws Exception {
        //note that "true" gets recoded to "1" to match other bools in the cert
        PoolAttribute attr = new PoolAttribute("virt_only", "true");
        entitlement.getPool().addAttribute(attr);
        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsVirtOnlyKey("1")), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void orderNumberAttribute() throws Exception {
        //note that "true" gets recoded to "1" to match other bools in the cert
        pool.setOrderNumber("this_order");
        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsOrderNumberKey("this_order")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void supportValuesPresentOnCertIfAttributePresent() throws Exception {

        pool.setProductAttribute("support_level", "Premium", "p");
        pool.setProductAttribute("support_type", "Level 3", "p");

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportLevel("Premium")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportType("Level 3")), any(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void ensureV3CertificateCreationOkWhenConsumerSupportsV3Dot1Certs()
        throws Exception {
        Config mockConfig = mock(Config.class);

        when(consumer.getFact(eq("system.certificate_version"))).thenReturn("3.2");
        ProductAttribute attr = new ProductAttribute("ram", "4");
        subscription.getProduct().addAttribute(attr);

        X509V3ExtensionUtil mockV3extensionUtil = mock(X509V3ExtensionUtil.class);
        X509ExtensionUtil mockExtensionUtil = mock(X509ExtensionUtil.class);

        DefaultEntitlementCertServiceAdapter entAdapter =
            new DefaultEntitlementCertServiceAdapter(
                mockedPKI, mockExtensionUtil, mockV3extensionUtil,
                mock(EntitlementCertificateCurator.class), keyPairCurator,
                serialCurator, productAdapter, entCurator,
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
                mockConfig);

        entAdapter.createX509Certificate(entitlement, product,
            new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
    }

    @Test
    public void supportValuesAbsentOnCertIfNoSupportAttributes()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportLevel()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportType()), any(Set.class), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void ensureV3CertIsCreatedWhenEnableCertV3ConfigIsTrue() throws Exception {
        Config mockConfig = mock(Config.class);

        when(consumer.getFact(eq("system.certificate_version"))).thenReturn("3.0");

        X509V3ExtensionUtil mockV3extensionUtil = mock(X509V3ExtensionUtil.class);
        X509ExtensionUtil mockExtensionUtil = mock(X509ExtensionUtil.class);

        DefaultEntitlementCertServiceAdapter entAdapter =
            new DefaultEntitlementCertServiceAdapter(
                mockedPKI, mockExtensionUtil, mockV3extensionUtil,
                mock(EntitlementCertificateCurator.class), keyPairCurator,
                serialCurator, productAdapter, entCurator,
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
                mockConfig);

        entAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
        verify(mockV3extensionUtil).getExtensions(eq(entitlement), any(String.class),
            any(Map.class));
        verify(mockV3extensionUtil).getByteExtensions(any(Set.class),
            eq(entitlement), any(String.class), any(Map.class));
        verifyZeroInteractions(mockExtensionUtil);
    }

    @Test
    public void ensureV3CertIsCreatedWhenV3CapabilityPresent() throws Exception {
        Config mockConfig = mock(Config.class);

        when(consumer.getType()).thenReturn(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));

        Set<ConsumerCapability> set = new HashSet<ConsumerCapability>();
        set.add(new ConsumerCapability(consumer, "cert_v3"));
        when(consumer.getCapabilities()).thenReturn(set);

        X509V3ExtensionUtil mockV3extensionUtil = mock(X509V3ExtensionUtil.class);
        X509ExtensionUtil mockExtensionUtil = mock(X509ExtensionUtil.class);

        DefaultEntitlementCertServiceAdapter entAdapter =
            new DefaultEntitlementCertServiceAdapter(
                mockedPKI, mockExtensionUtil, mockV3extensionUtil,
                mock(EntitlementCertificateCurator.class), keyPairCurator,
                serialCurator, productAdapter, entCurator,
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
                mockConfig);

        entAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
        verify(mockV3extensionUtil).getExtensions(eq(entitlement), any(String.class),
            any(Map.class));
        verify(mockV3extensionUtil).getByteExtensions(any(Set.class),
            eq(entitlement), any(String.class), any(Map.class));
        verifyZeroInteractions(mockExtensionUtil);
    }


    @Test
    public void ensureV1CertIsCreatedWhenV3factNotPresent() throws Exception {
        Config mockConfig = mock(Config.class);

        when(consumer.getType()).thenReturn(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));

        X509V3ExtensionUtil mockV3extensionUtil = mock(X509V3ExtensionUtil.class);
        X509ExtensionUtil mockExtensionUtil = mock(X509ExtensionUtil.class);

        DefaultEntitlementCertServiceAdapter entAdapter =
            new DefaultEntitlementCertServiceAdapter(
                mockedPKI, mockExtensionUtil, mockV3extensionUtil,
                mock(EntitlementCertificateCurator.class), keyPairCurator,
                serialCurator, productAdapter, entCurator,
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
                mockConfig);

        entAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
        // Verify v1
        verify(mockExtensionUtil).consumerExtensions(eq(consumer));
        verifyZeroInteractions(mockV3extensionUtil);
    }

    @Test
    public void ensureV3CertIsCreatedWhenHypervisor() throws Exception {
        Config mockConfig = mock(Config.class);

        when(consumer.getType()).thenReturn(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.HYPERVISOR));

        X509V3ExtensionUtil mockV3extensionUtil = mock(X509V3ExtensionUtil.class);
        X509ExtensionUtil mockExtensionUtil = mock(X509ExtensionUtil.class);

        DefaultEntitlementCertServiceAdapter entAdapter =
            new DefaultEntitlementCertServiceAdapter(
                mockedPKI, mockExtensionUtil, mockV3extensionUtil,
                mock(EntitlementCertificateCurator.class), keyPairCurator,
                serialCurator, productAdapter, entCurator,
                I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
                mockConfig);

        entAdapter.createX509Certificate(entitlement,
            product, new HashSet<Product>(), new BigInteger("1234"), keyPair(), true);
        verify(mockV3extensionUtil).getExtensions(eq(entitlement), any(String.class),
            any(Map.class));
        verify(mockV3extensionUtil).getByteExtensions(any(Set.class),
            eq(entitlement), any(String.class), any(Map.class));
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

    private Boolean extMapProductBrandTypeMatches(Product product, Map<String,
        String> extMap, String brandType) {
        String brandTypeOid = "1.3.6.1.4.1.2312.9.1." +
            product.getId() + "." + "5";
        String extBrandType = extMap.get(brandTypeOid);

        return extBrandType.equals(brandType);
    }

    @Test
    public void testPrepareV1Extensions() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));

        assertTrue(map.containsKey(CONTENT_URL));
        // do we have a yum content type oid
        assertTrue(extMapHasContentType(content, extMap, "1"));
        assertFalse(extMapHasContentType(content, extMap, "2"));
    }

    @Test
    public void testPrepareV1ExtensionsBrandedProduct() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        ProductAttribute brandAttr = new ProductAttribute("brand_type", "os");
        product.addAttribute(brandAttr);
        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertTrue(isEncodedContentValid(map));
        assertTrue(extMapHasProductBrandType(product, extMap));
        assertTrue(extMapProductBrandTypeMatches(product, extMap, "os"));

    }

    @Test
    public void testPrepareV1ExtensionsNoCompatibleArch() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with no compatible content, but marked as 'ALL' arch
        Product wrongArchProduct = new Product("12345", "a product",
            "variant", "version", "ALL", "SVC");

        // no x86_64, ie ARCH_LABEL
        String wrongArches = "s390x,s390,ppc64,ia64";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, wrongArches);

        wrongArchProduct.setContent(Collections.singleton(wrongArchContent));
        products.clear();
        products.add(wrongArchProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
        Map<String, X509ExtensionWrapper> map = getEncodedContent(extensions);
        Map<String, String> extMap = getEncodedContentMap(extensions);

        assertFalse(isEncodedContentValid(map));
        assertFalse(map.containsKey(CONTENT_URL));
        // make sure we don't set content type to "null"
        assertFalse(extMapHasContentType(kickstartContent, extMap, "null"));
    }

    @Test
    public void testPrepareV1ExtensionsKickstartContent() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with a kickstart content
        Product kickstartProduct = new Product("12345", "a product",
            "variant", "version", "ALL", "SVC");

        kickstartProduct.setContent(Collections.singleton(kickstartContent));
        products.clear();
        products.add(kickstartProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
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
    public void testPrepareV1ExtensionsFileContent() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with a kickstart content
        Product fileProduct = new Product("12345", "a product",
            "variant", "version", "ALL", "SVC");

        fileProduct.setContent(Collections.singleton(fileContent));
        products.clear();
        products.add(fileProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
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
    public void testPrepareV1ExtensionsFileUnknownContentType() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with a kickstart content
        Product unknownContentTypeProduct = new Product("12345", "a product",
            "variant", "version", ARCH_LABEL, "SVC");

        unknownContentTypeProduct.setContent(Collections.singleton(unknownTypeContent));
        products.clear();
        products.add(unknownContentTypeProduct);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
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
    public void testPrepareV1ExtensionsKnownAndUnknownContentTypes() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with a kickstart content
        Product product = new Product("12345", "a product",
            "variant", "version", ARCH_LABEL, "SVC");

        Set<Content> multipleContents = new HashSet<Content>();
        multipleContents.add(content);
        multipleContents.add(fileContent);
        multipleContents.add(kickstartContent);
        multipleContents.add(unknownTypeContent);

        product.setContent(multipleContents);
        products.clear();
        products.add(product);
        setupEntitlements(ARCH_LABEL, "1.0");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV1Extensions(products, entitlement, "",
                null);
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

    @Test
    public void testPrepareV3EntitlementData() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();
        products.add(product);
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn("x86_64");

        pool.setProductAttribute("warning_period", "20", "p");
        pool.setProductAttribute("sockets", "4", "p");
        pool.setProductAttribute("ram", "8", "p");
        pool.setProductAttribute("cores", "4", "p");
        pool.setProductAttribute("management_enabled", "true", "p");
        pool.setProductAttribute("stacking_id", "45678", "p");
        pool.setAttribute("virt_only", "true");
        pool.setProductAttribute("support_level", "slevel", "p");
        pool.setProductAttribute("support_type", "stype", "p");
        pool.setAccountNumber("account1");
        pool.setContractNumber("contract1");
        pool.setOrderNumber("order1");
        for (ProductContent pc : product.getProductContent()) {
            pc.setEnabled(false);
        }

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), ("3.2"));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
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
        assertTrue(((Integer) order.get("quantity")).intValue() ==
            subscription.getQuantity());
        assertNotNull(order.get("start"));
        assertNotNull(order.get("end"));
//        assertEquals(order.get("contract"), subscription.getContractNumber());
//        assertEquals(order.get("account"), subscription.getAccountNumber());

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents = null;
        for (Map<String, Object> prod : prods) {
            assertEquals(prod.get("id"), product.getId());
            assertEquals(prod.get("name"), product.getName());
            assertEquals(prod.get("version"), product.getAttributeValue("version"));
            String arch = product.hasAttribute("arch") ?
                product.getAttributeValue("arch") : "";
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

                List<String> arches = new ArrayList<String>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);

                String rTags = content.getRequiredTags();
                st = new StringTokenizer(rTags, ",");
                while (st.hasMoreElements()) {
                    assertTrue(((List) cont.get("required_tags"))
                        .contains(st.nextElement()));
                }
            }
        }
    }

    private void setupEntitlements(String consumerArch, String certVersion) {
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn(certVersion);
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn(
            consumerArch);

        subscription.getProduct().setAttribute("warning_period", "20");
        subscription.getProduct().setAttribute("sockets", "4");
        subscription.getProduct().setAttribute("ram", "8");
        subscription.getProduct().setAttribute("cores", "4");
        subscription.getProduct().setAttribute("management_enabled", "true");
        subscription.getProduct().setAttribute("stacking_id", "45678");
        entitlement.getPool().setAttribute("virt_only", "true");
        subscription.getProduct().setAttribute("support_level", "slevel");
        subscription.getProduct().setAttribute("support_type", "stype");
        subscription.setAccountNumber("account1");
        subscription.setContractNumber("contract1");
        subscription.setOrderNumber("order1");
        for (ProductContent pc : product.getProductContent()) {
            pc.setEnabled(false);
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoConsumerArch() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();
        products.add(product);

        setupEntitlements(null, "3.2");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents = null;
        for (Map<String, Object> prod : prods) {
            String arch = product.hasAttribute("arch") ?
                product.getAttributeValue("arch") : "";
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
                List<String> arches = new ArrayList<String>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);

            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoContentArch() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // our content with no arch should inherit this arch
        Product inheritedArchProduct = new Product("12345", "a product",
            "variant", "version", ARCH_LABEL, "SVC");

        inheritedArchProduct.setContent(Collections.singleton(noArchContent));
        products.add(inheritedArchProduct);
        setupEntitlements(ARCH_LABEL, "3.2");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), ("3.2"));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents = null;
        for (Map<String, Object> prod : prods) {

            String arch = product.hasAttribute("arch") ?
                product.getAttributeValue("arch") : "";
            StringTokenizer st = new StringTokenizer(arch, ",");
            while (st.hasMoreElements()) {
                assertTrue(((List) prod.get("architectures")).contains(st.nextElement()));
            }

            contents = (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {

                // We dont set an arch on Content, but we inherit it
                // from product, so the arch should match ARCH_LABEL,
                // that the Product was created with
                List<String> arches = new ArrayList<String>();
                arches.add(ARCH_LABEL);
                assertEquals(cont.get("arches"), arches);

            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataNoCompatibleArch() throws IOException,
        GeneralSecurityException {
        Set<Product> products = new HashSet<Product>();

        // product with no compatible content, but marked as 'ALL' arch
        Product wrongArchProduct = new Product("12345", "a product",
            "variant", "version", "ALL", "SVC");

        // no x86_64, ie ARCH_LABEL
        String wrongArches = "s390x,s390,ppc64,ia64";
        Content wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL, wrongArches);

        wrongArchProduct.setContent(Collections.singleton(wrongArchContent));
        products.clear();
        products.add(wrongArchProduct);
        setupEntitlements(ARCH_LABEL, "3.2");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), ("3.2"));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        List<Map<String, Object>> contents = null;
        for (Map<String, Object> prod : prods) {

            String arch = wrongArchProduct.hasAttribute("arch") ?
                wrongArchProduct.getAttributeValue("arch") : "";
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
        Set<Product> products = new HashSet<Product>();
        products.add(product);
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn("x86_64");

        subscription.getProduct().setAttribute("warning_period", "0");
        subscription.getProduct().setAttribute("management_enabled", "false");
        entitlement.getPool().setAttribute("virt_only", "false");
        for (ProductContent pc : product.getProductContent()) {
            pc.setEnabled(true);
        }

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), ("3.2"));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);
        assertEquals(data.get("consumer"), "test-consumer");

        // each has been set to the default and should not be populated in the cert
        Map<String, Object> subs = (Map<String, Object>) data.get("subscription");
        assertNull(subs.get("warning"));
        assertNull(subs.get("management"));
        assertNull(subs.get("virt_only"));

        List<Map<String, Object>> prods = (List<Map<String, Object>>) data.get("products");
        for (Map<String, Object> prod : prods) {
            List<Map<String, Object>> contents =
                (List<Map<String, Object>>) prod.get("content");
            for (Map<String, Object> cont : contents) {
                assertNull(cont.get("enabled"));
            }
        }
    }

    @Test
    public void testPrepareV3EntitlementDataForBooleans() throws IOException {
        Set<Product> products = new HashSet<Product>();
        products.add(product);
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn("x86_64");

        pool.setProductAttribute("management_enabled", "1", "p");
        entitlement.getPool().setAttribute("virt_only", "1");

        Set<X509ExtensionWrapper> extensions =
            certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Map<String, X509ExtensionWrapper> map =
            new HashMap<String, X509ExtensionWrapper>();
        for (X509ExtensionWrapper ext : extensions) {
            map.put(ext.getOid(), ext);
        }
        assertTrue(map.containsKey("1.3.6.1.4.1.2312.9.6"));
        assertEquals(map.get("1.3.6.1.4.1.2312.9.6").getValue(), ("3.2"));

        byte[] payload = v3extensionUtil.createEntitlementDataPayload(products, entitlement,
            "prefix", null);
        String stringValue = "";
        try {
            stringValue = processPayload(payload);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> data = (Map<String, Object>)
            Util.fromJson(stringValue , Map.class);
        assertEquals(data.get("consumer"), "test-consumer");

        // each has been set to the default and should not be populated in the cert
        Map<String, Object> subs = (Map<String, Object>) data.get("subscription");
        assertTrue((Boolean) subs.get("management"));
        assertTrue((Boolean) subs.get("virt_only"));
    }

    @Test
    public void testDetachedEntitlementDataNotAddedToCertV1()
        throws Exception {

        KeyPair keyPair = new BouncyCastlePKIUtility(null, null).generateNewKeyPair();
        when(keyPairCurator.getConsumerKeyPair(any(Consumer.class))).thenReturn(keyPair);

        when(mockedPKI.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "".getBytes());
        when(mockedPKI.getPemEncoded(any(Key.class))).thenReturn("".getBytes());

        CertificateSerial serial = mock(CertificateSerial.class);
        when(serial.getId()).thenReturn(1L);
        when(serialCurator.create(any(CertificateSerial.class))).thenReturn(serial);

        EntitlementCertificate cert =
            certServiceAdapter.generateEntitlementCert(entitlement, subscription,
                product);

        assertTrue(!cert.getCert().contains("ENTITLEMENT DATA"));
    }

    @Test
    public void testContentExtension() throws IOException {
        Set<Product> products = new HashSet<Product>();
        products.add(product);
        product.setContent(superContent);
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn("x86_64");
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");

        Set<X509ByteExtensionWrapper> byteExtensions =
            certServiceAdapter.prepareV3ByteExtensions(products, entitlement, "prefix",
                null);
        Map<String, X509ByteExtensionWrapper> byteMap =
            new HashMap<String, X509ByteExtensionWrapper>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList = new ArrayList<String>();
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
        Set<Product> products = new HashSet<Product>();
        products.add(product);
        // set of content for an incompatible arch, which should
        // be in the cert, since this consumer has no arch fact therefore
        // should match everything
        Content wrongArchContent = new Content();
        String wrongArches = "s390";
        String noArchUrl = "/some/place/nice";
        wrongArchContent = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, noArchUrl, CONTENT_GPG_URL, wrongArches);
        product.setContent(superContent);
        product.addContent(wrongArchContent);

        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getFact("uname.machine")).thenReturn(null);
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");

        Set<X509ByteExtensionWrapper> byteExtensions =
            certServiceAdapter.prepareV3ByteExtensions(products, entitlement, "prefix",
                null);
        Map<String, X509ByteExtensionWrapper> byteMap =
            new HashMap<String, X509ByteExtensionWrapper>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList = new ArrayList<String>();
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
        Set<Product> products = new HashSet<Product>();
        products.add(largeContentProduct);
        largeContentProduct.setContent(largeContent);
        when(largeContentEntitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(largeContentEntitlement.getConsumer().getUuid()).thenReturn("test-consumer");

        Set<X509ByteExtensionWrapper> byteExtensions =
            certServiceAdapter.prepareV3ByteExtensions(products, largeContentEntitlement,
                "prefix", null);
        Map<String, X509ByteExtensionWrapper> byteMap =
            new HashMap<String, X509ByteExtensionWrapper>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList = new ArrayList<String>();
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
    public void testContentExtensionLargeSet() throws IOException {
        Set<Product> products = new HashSet<Product>();
        Product extremeProduct = new Product("12345", "a product", "variant", "version",
            ARCH_LABEL, "SVC");
        products.add(extremeProduct);
        Set<Content> extremeContent = new HashSet<Content>();
        for (int i = 0; i < 550; i++) {
            String url = "/content/dist" + i + "/jboss/source" + i;
            extremeContent.add(createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
                CONTENT_TYPE, CONTENT_VENDOR, url, CONTENT_GPG_URL, ARCH_LABEL));
        }
        extremeProduct.setContent(extremeContent);
        when(entitlement.getConsumer().getFact("system.certificate_version"))
            .thenReturn("3.2");
        when(entitlement.getConsumer().getUuid()).thenReturn("test-consumer");

        certServiceAdapter.prepareV3Extensions(entitlement, "prefix", null);
        Set<X509ByteExtensionWrapper> byteExtensions =
            certServiceAdapter.prepareV3ByteExtensions(products, entitlement, "prefix",
                null);
        Map<String, X509ByteExtensionWrapper> byteMap =
            new HashMap<String, X509ByteExtensionWrapper>();
        for (X509ByteExtensionWrapper ext : byteExtensions) {
            byteMap.put(ext.getOid(), ext);
        }

        assertTrue(byteMap.containsKey("1.3.6.1.4.1.2312.9.7"));
        List<String> contentSetList = new ArrayList<String>();
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
        List<org.candlepin.json.model.Content> contentList =
            new ArrayList<org.candlepin.json.model.Content>();
        for (int i = 0; i < 20; i++) {
            org.candlepin.json.model.Content cont =
                new org.candlepin.json.model.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" +
                i + "/leg/foot/heel");
            contentList.add(cont);
        }
        PathNode location = v3extensionUtil.makePathTree(contentList,
            v3extensionUtil.new PathNode());
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
        List<org.candlepin.json.model.Content> contentList =
            new ArrayList<org.candlepin.json.model.Content>();

        org.candlepin.json.model.Content contentA = new org.candlepin.json.model.Content();
        contentA.setPath("/AAA");
        org.candlepin.json.model.Content contentB = new org.candlepin.json.model.Content();
        contentB.setPath("/BBB");
        org.candlepin.json.model.Content contentC = new org.candlepin.json.model.Content();
        contentC.setPath("/CCC");

        contentList.add(contentB);
        contentList.add(contentC);
        contentList.add(contentA);

        PathNode location = v3extensionUtil.makePathTree(contentList,
            v3extensionUtil.new PathNode());

        assertEquals(3, location.getChildren().size(), 3);
        assertEquals("AAA", location.getChildren().get(0).getName());
        assertEquals("BBB", location.getChildren().get(1).getName());
        assertEquals("CCC", location.getChildren().get(2).getName());
    }

    @Test
    public void testPathDictionary() throws IOException {
        List<org.candlepin.json.model.Content> contentList =
            new ArrayList<org.candlepin.json.model.Content>();
        org.candlepin.json.model.Content cont = null;
        for (int i = 0; i < 20; i++) {
            cont = new org.candlepin.json.model.Content();
            cont.setPath("/head/neck/shoulders/heart" + i + "/waist" +
                i + "/leg/foot/heel");
            contentList.add(cont);
        }
        cont = new org.candlepin.json.model.Content();
        cont.setPath("/head/neck/shoulders/chest/leg");
        contentList.add(cont);
        cont = new org.candlepin.json.model.Content();
        cont.setPath("/head/neck/shoulders/chest/foot");
        contentList.add(cont);
        cont = new org.candlepin.json.model.Content();
        cont.setPath("/head/neck/shoulders/chest/torso/leg");
        contentList.add(cont);

        PathNode location = v3extensionUtil.makePathTree(contentList,
            v3extensionUtil.new PathNode());
        List<String> nodeStrings = v3extensionUtil.orderStrings(location);
        assertEquals(nodeStrings.size(), 48);
        // frequency sorted
        assertEquals(nodeStrings.get(46), "foot");
        assertEquals(nodeStrings.get(47), "leg");
    }

    @Test
    public void testHuffNodeTrieCreationAndTreeSearch() {
        String[] paths = {"01110", "01111", "0110", "1110",
            "1111", "010", "100", "101", "110", "00"};
        List<HuffNode> huffNodes = new ArrayList<HuffNode>();
        List<Object> members = new ArrayList<Object>();
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
            Object found = v3extensionUtil.findHuffNodeValueByBits(trieParent,
                paths[idx++]);
            assertEquals(o, found);
        }
    }

    private String processPayload(byte[] payload)
        throws IOException, UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InflaterOutputStream ios = new InflaterOutputStream(baos);
        ios.write(payload);
        ios.finish();
        return baos.toString();
    }

    private Map<String, X509ExtensionWrapper> getEncodedContent(
        Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, X509ExtensionWrapper> encodedContent =
            new HashMap<String, X509ExtensionWrapper>();

        for (X509ExtensionWrapper ext : contentExtensions) {
            encodedContent.put(ext.getValue(), ext);
        }
        return encodedContent;
    }

    private Map<String, String> getEncodedContentMap(
        Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, String> encodedContentMap =
            new HashMap<String, String>();

        for (X509ExtensionWrapper ext : contentExtensions) {
            encodedContentMap.put(ext.getOid(), ext.getValue());
        }
        return encodedContentMap;

    }

    private boolean isEncodedContentValid(Set<X509ExtensionWrapper> contentExtensions) {
        Map<String, X509ExtensionWrapper> encodedContent =
            getEncodedContent(contentExtensions);

        return isEncodedContentValid(encodedContent);
    }

    private boolean isEncodedContentValid(Map<String,
            X509ExtensionWrapper> encodedContent) {

        return encodedContent.containsKey(CONTENT_LABEL) &&
            // encodedContent.containsKey(CONTENT_ENABLED) &&
            encodedContent.containsKey(CONTENT_GPG_URL) &&
            encodedContent.containsKey(CONTENT_URL) &&
            encodedContent.containsKey(CONTENT_VENDOR) &&
            encodedContent.containsKey(CONTENT_NAME);
    }


    private KeyPair keyPair() {
        return new KeyPair(new PublicKey() {

            @Override
            public String getFormat() {
                return null;
            }

            @Override
            public byte[] getEncoded() {
                return null;
            }

            @Override
            public String getAlgorithm() {
                return null;
            }
        },

            new PrivateKey() {

                @Override
                public String getFormat() {
                    return null;
                }

                @Override
                public byte[] getEncoded() {
                    return null;
                }

                @Override
                public String getAlgorithm() {
                    return null;
                }
            }
        );
    }

    class ListContainsContentExtensions extends
        ArgumentMatcher<Set<X509ExtensionWrapper>> {

        public boolean matches(Object list) {
            return isEncodedContentValid((Set) list);
        }
    }

    static class ListContainsEntitlementExtensions extends
        ArgumentMatcher<Set<X509ExtensionWrapper>> {

        public boolean matches(Object list) {
            Map<String, X509ExtensionWrapper> encodedContent =
                new HashMap<String, X509ExtensionWrapper>();

            for (X509ExtensionWrapper ext : (Set<X509ExtensionWrapper>) list) {
                encodedContent.put(ext.getOid(), ext);
            }

            return encodedContent.containsKey("1.3.6.1.4.1.2312.9.4.11") &&
                encodedContent.get("1.3.6.1.4.1.2312.9.4.11")
                    .getValue()
                    .equals(ENTITLEMENT_QUANTITY);
        }
    }

    abstract static class OidMatcher extends
        ArgumentMatcher<Set<X509ExtensionWrapper>> {

        protected String value;
        protected String oid;

        public OidMatcher(String value, String oid) {
            this.value = value;
            this.oid = oid;
        }

        public boolean matches(Object list) {
            Map<String, X509ExtensionWrapper> encodedContent =
                new HashMap<String, X509ExtensionWrapper>();

            for (X509ExtensionWrapper ext : (Set<X509ExtensionWrapper>) list) {
                encodedContent.put(ext.getOid(), ext);
            }

            return encodedContent.containsKey(oid) &&
                encodedContent.get(oid).getValue().equals(value);
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


    abstract static class OidAbsentMatcher extends
        ArgumentMatcher<Set<X509ExtensionWrapper>> {

        protected String oid;

        public OidAbsentMatcher(String oid) {
            this.oid = oid;
        }

        public boolean matches(Object list) {
            Map<String, X509ExtensionWrapper> encodedContent =
                new HashMap<String, X509ExtensionWrapper>();

            for (X509ExtensionWrapper ext : (Set<X509ExtensionWrapper>) list) {
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
        "/content/beta/rhel/server/6/$releasever/$basearch/subscription-asset-manager/" +
            "debug",
        "/content/dist/rhel/server/5/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/subscription-asset-manager/" +
            "source/SRPMS",
        "/content/beta/rhel/server/6/$releasever/$basearch/os",
        "/content/dist/rhel/server/6/$releasever/$basearch/rhev-agent/3.0/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/source/SRPMS",
        "/content/beta/rhel/server/5/$releasever/$basearch/supplementary/os",
        "/content/dist/rhel/server/5/$releasever/$basearch/source/iso",
        "/content/beta/rhel/server/5/$releasever/$basearch/vt/source/SRPMS",
        "/content/dist/rhel/server/6/$releasever/$basearch/subscription-asset-manager/1/" +
            "debug",
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
        "/content/dist/rhel/server/6/$releasever/$basearch/subscription-asset-manager/1/" +
            "source/SRPMS",
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
        "/content/rhb/rhel/server/5/$releasever/$basearch/devtoolset/source/SRPMS"};
}

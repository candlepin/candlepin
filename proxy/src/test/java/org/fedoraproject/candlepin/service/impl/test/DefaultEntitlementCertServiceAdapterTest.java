/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.service.impl.test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * DefaultEntitlementCertServiceAdapter
 */

@RunWith(MockitoJUnitRunner.class)
public class DefaultEntitlementCertServiceAdapterTest {

    private static final String CONTENT_LABEL = "label";
    private static final String CONTENT_ID = "1234";
    private static final String CONTENT_TYPE = "yum";
    private static final String CONTENT_GPG_URL = "gpgUrl";
    private static final String CONTENT_URL = "contentUrl";
    private static final String CONTENT_VENDOR = "vendor";
    private static final String CONTENT_NAME = "name";
    private static final Long CONTENT_METADATA_EXPIRE = 3200L;
    private static final String ENTITLEMENT_QUANTITY = "10";
    private static final String REQUIRED_TAGS = "TAG1,TAG2";

    private DefaultEntitlementCertServiceAdapter certServiceAdapter;
    @Mock
    private PKIUtility mockedPKI;
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private ProductServiceAdapter productAdapter;
    @Mock
    private EntitlementCurator entCurator;
    private X509ExtensionUtil extensionUtil;
    private Product product;
    private Subscription subscription;
    private Entitlement entitlement;
    private Pool pool;
    private Content content;
    private Owner owner;

    @Before
    public void setUp() {
        extensionUtil = new X509ExtensionUtil();

        certServiceAdapter = new DefaultEntitlementCertServiceAdapter(
            mockedPKI, extensionUtil, null, null, serialCurator,
            productAdapter, entCurator);

        product = new Product("12345", "a product", "variant", "version",
            "arch", "SVC");

        content = createContent(CONTENT_NAME, CONTENT_ID, CONTENT_LABEL,
            CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL, CONTENT_GPG_URL);
        content.setMetadataExpire(CONTENT_METADATA_EXPIRE);
        content.setRequiredTags(REQUIRED_TAGS);

        subscription = new Subscription(null, product, new HashSet<Product>(),
            1L, new Date(), new Date(), new Date());
        subscription.setId("1");

        owner = new Owner();

        pool = new Pool();
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());

        entitlement = new Entitlement();
        entitlement.setQuantity(new Integer(ENTITLEMENT_QUANTITY));
        entitlement.setConsumer(Mockito.mock(Consumer.class));
        entitlement.setStartDate(subscription.getStartDate());
        entitlement.setEndDate(subscription.getEndDate());
        entitlement.setFlexExpiryDays(60);
        entitlement.setPool(pool);
        entitlement.setOwner(owner);

        product.setContent(Collections.singleton(content));
    }

    private Content createContent(String name, String id, String label,
        String type, String vendor, String url, String gpgUrl) {
        Content c = new Content(name, id, label, type, vendor, url, gpgUrl);
        return c;
    }

    @Test
    public void testContentExtentionCreation() {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil
            .contentExtensions(product.getProductContent(), null);
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));

        // Nullify this, and make sure it's not there.
        content.setMetadataExpire(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "");
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(CONTENT_METADATA_EXPIRE.toString()));
    }

    @Test
    public void testContentRequiredTagsExtention() {
        Set<X509ExtensionWrapper> contentExtensions = extensionUtil
            .contentExtensions(product.getProductContent(), null);
        Map<String, X509ExtensionWrapper> encodedContent = getEncodedContent(
            contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertTrue(encodedContent.containsKey(REQUIRED_TAGS.toString()));

        // Nullify this, and make sure it's not there.
        content.setRequiredTags(null);
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "");
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS.toString()));

        // Empty string, and make sure it's not there.
        content.setRequiredTags("");
        contentExtensions = extensionUtil.contentExtensions(
            product.getProductContent(), "");
        encodedContent = getEncodedContent(contentExtensions);
        assertTrue(isEncodedContentValid(encodedContent));
        assertFalse(encodedContent.containsKey(REQUIRED_TAGS.toString()));
    }
    @Test
    public void testPrefixesShouldBeUsed() throws Exception {
        owner.setContentPrefix("/somePrefix/");

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(
            any(String.class),
            argThat(new ListContainsContentUrl("/somePrefix/" + CONTENT_URL,
                CONTENT_ID)), any(Date.class), any(Date.class),
            any(KeyPair.class), any(BigInteger.class), any(String.class));
    }

    @Test
    public void testBlankPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix("");

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), any(String.class));
    }

    @Test
    public void testNullPrefixesShouldNotEffectAnything() throws Exception {
        owner.setContentPrefix(null);

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentUrl(CONTENT_URL, CONTENT_ID)),
            any(Date.class), any(Date.class), any(KeyPair.class),
            any(BigInteger.class), any(String.class));
    }

    @Test
    public void testFilterProductContent() {
        Product modProduct = new Product("12345", "a product", "variant",
            "version", "arch", "SVC");

        // Use this set for successful providing queries:
        Set<Entitlement> successResult = new HashSet<Entitlement>();
        successResult.add(new Entitlement()); // just need something in there

        Content normalContent = createContent(CONTENT_NAME, CONTENT_ID,
            CONTENT_LABEL, CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL);
        // Change label to prevent an equals match:
        Content modContent = createContent(CONTENT_NAME, CONTENT_ID,
            "differentlabel", CONTENT_TYPE, CONTENT_VENDOR, CONTENT_URL,
            CONTENT_GPG_URL);
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
        // the
        // product it modifies:
        assertEquals(1,
            certServiceAdapter.filterProductContent(modProduct, entitlement)
                .size());

        // Now mock that we have an entitlement providing one of the modified
        // products,
        // and we should see both content sets included in the cert:
        when(
            this.entCurator.listProviding(any(Consumer.class), eq("product2"),
                any(Date.class), any(Date.class))).thenReturn(successResult);
        assertEquals(2,
            certServiceAdapter.filterProductContent(modProduct, entitlement)
                .size());
    }

    @Test
    public void contentExtentionsShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsContentExtensions()), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void entitlementQuantityShouldBeAddedDuringCertificateGeneration()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsEntitlementExtensions()), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void managementDisabledByDefault() throws Exception {

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("0")), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void managementEnabledByAttribute() throws Exception {

        ProductAttribute attr = new ProductAttribute("management_enabled", "1");
        subscription.getProduct().addAttribute(attr);
        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsProvidesManagement("1")), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void supportValuesPresentOnCertIfAttributePresent() throws Exception {

        ProductAttribute attr = new ProductAttribute("support_level", "Premium");
        subscription.getProduct().addAttribute(attr);
        attr = new ProductAttribute("support_type", "Level 3");
        subscription.getProduct().addAttribute(attr);

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportLevel("Premium")), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListContainsSupportType("Level 3")), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }

    @Test
    public void supportValuesAbsentOnCertIfNoSupportAttributes()
        throws Exception {

        certServiceAdapter.createX509Certificate(entitlement, subscription,
            product, new BigInteger("1234"), keyPair());

        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportLevel()), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
        verify(mockedPKI).createX509Certificate(any(String.class),
            argThat(new ListDoesNotContainSupportType()), any(Date.class),
            any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
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

    static class ListContainsContentUrl extends OidMatcher {

        public ListContainsContentUrl(String value, String contentID) {
            super(value, "1.3.6.1.4.1.2312.9.2." + contentID + ".1.6");
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

}

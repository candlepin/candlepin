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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.DERUTF8String;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * DefaultEntitlementCertServiceAdapter
 */

@RunWith(MockitoJUnitRunner.class)
public class DefaultEntitlementCertServiceAdapterTest {

    private static final String CONTENT_LABEL = "label";
    private static final long CONTENT_ID = 1234L;
    private static final String CONTENT_TYPE = "type";
    private static final String CONTENT_ENABLED = "enabled";
    private static final String CONTENT_GPG_URL = "gpgUrl";
    private static final String CONTENT_URL = "contentUrl";
    private static final String CONTENT_VENDOR = "vendor";
    private static final String CONTENT_NAME = "name";
    private static final String ENTITLEMENT_QUANTITY = "10";

    private DefaultEntitlementCertServiceAdapter certServiceAdapter;
    @Mock private PKIUtility mockedPKI;
    @Mock private CertificateSerialCurator serialCurator;
    private X509ExtensionUtil extensionUtil;
    private Product product;
    private Subscription subscription;
    private Entitlement entitlement;

    @Before
    public void setUp() {
        extensionUtil = new X509ExtensionUtil();
        
        certServiceAdapter 
            = new DefaultEntitlementCertServiceAdapter(mockedPKI, 
                extensionUtil, null, null, serialCurator);
        
        product = new Product("a_product", "a product", 
                              "variant", "version", "arch", 
                              "SVC");
        
        Content content = new Content(CONTENT_NAME, CONTENT_ID,
                                      CONTENT_LABEL, CONTENT_TYPE,
                                      CONTENT_VENDOR, CONTENT_URL,
                                      CONTENT_GPG_URL);
        content.setType(CONTENT_TYPE);
        content.setLabel(CONTENT_LABEL);
        content.setId(CONTENT_ID);
        
        subscription = new Subscription(null, product, new HashSet<Product>(), 
                1L, new Date(), new Date(), new Date());
        subscription.setId(1L);
        
        entitlement = new Entitlement();
        entitlement.setQuantity(new Integer(ENTITLEMENT_QUANTITY));
        
        product.setContent(Collections.singleton(content));
    }
    
    @Test
    public void testContentExtentionCreation() {
        // AAAH!  This should be pulled out to its own test class!
        Set<X509ExtensionWrapper> content = extensionUtil.contentExtensions(product);
        assertTrue(isEncodedContentValid(content));
    }

    @Test
    public void contentExtentionsShouldBeAddedDuringCertificateGeneration() 
        throws Exception {
        
        certServiceAdapter.createX509Certificate(mock(Consumer.class),
            entitlement, subscription, product, mock(Date.class), new BigInteger("1234"),
            keyPair());
        
        verify(mockedPKI).createX509Certificate(any(String.class), 
            argThat(new ListContainsContentExtensions()), 
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }
    
    @Test
    public void entitlementQuantityShouldBeAddedDuringCertificateGeneration() 
        throws Exception {
        
        certServiceAdapter.createX509Certificate(mock(Consumer.class),
            entitlement, subscription, product, mock(Date.class), new BigInteger("1234"),
            keyPair());
        
        verify(mockedPKI).createX509Certificate(any(String.class), 
            argThat(new ListContainsEntitlementExtensions()), 
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            any(String.class));
    }
    
    private boolean isEncodedContentValid(Set<X509ExtensionWrapper> content) {
        Map<String, X509ExtensionWrapper> encodedContent 
            = new HashMap<String, X509ExtensionWrapper>();

        
        for (X509ExtensionWrapper ext : content) {
            encodedContent.put(
                ((DERUTF8String) ext.getAsn1Encodable()).getString(), ext);
        }
       
        
        
        return encodedContent.containsKey(CONTENT_LABEL) &&
   //         encodedContent.containsKey(CONTENT_ENABLED) &&
            encodedContent.containsKey(CONTENT_GPG_URL) &&
            encodedContent.containsKey(CONTENT_URL) &&
            encodedContent.containsKey(CONTENT_VENDOR) &&
            encodedContent.containsKey(CONTENT_NAME);
    }
    
    private KeyPair keyPair() {
        return new KeyPair(
            new PublicKey() {
                
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
    
    class ListContainsContentExtensions 
        extends ArgumentMatcher<Set<X509ExtensionWrapper>> {
        
        public boolean matches(Object list) {
            return isEncodedContentValid((Set) list);
        }
    }
    
    class ListContainsEntitlementExtensions 
        extends ArgumentMatcher<Set<X509ExtensionWrapper>> {
        
        public boolean matches(Object list) {
            Map<String, X509ExtensionWrapper> encodedContent 
                = new HashMap<String, X509ExtensionWrapper>();

        
            for (X509ExtensionWrapper ext : (Set<X509ExtensionWrapper>) list) {
                encodedContent.put(ext.getOid(), ext);
            }
            
            return encodedContent.containsKey("1.3.6.1.4.1.2312.9.4.13") &&
                ((DERUTF8String) 
                    encodedContent.get("1.3.6.1.4.1.2312.9.4.13").getAsn1Encodable())
               .toString().equals(ENTITLEMENT_QUANTITY);
        }
    }
}

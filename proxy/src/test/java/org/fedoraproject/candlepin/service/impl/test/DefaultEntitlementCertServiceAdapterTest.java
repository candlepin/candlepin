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
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.DERUTF8String;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapterTest {

    private static final String CONTENT_LABEL = "label";
    private static final long CONTENT_HASH = 1234L;
    private static final String CONTENT_TYPE = "type";
    private static final String CONTENT_ENABLED = "enabled";
    private static final String CONTENT_GPG_URL = "gpgUrl";
    private static final String CONTENT_URL = "contentUrl";
    private static final String CONTENT_VENDOR = "vendor";
    private static final String CONTENT_NAME = "name";

    private DefaultEntitlementCertServiceAdapter certServiceAdapter;
    private PKIUtility mockedPKI;
    private X509ExtensionUtil extensionUtil;
    private Product product;
    private Subscription subscription;

    @Before
    public void setUp() {
        mockedPKI = mock(PKIUtility.class);
        extensionUtil = new X509ExtensionUtil();
        
        certServiceAdapter 
            = new DefaultEntitlementCertServiceAdapter(mockedPKI, 
                extensionUtil, null, null);
        
        product = new Product("a_product", "a product", 
                              "a product", "variant", "version", 
                              "arch", "SVC", new HashSet<Product>(),
                              new HashSet<Content>());
        
        Content content = new Content(CONTENT_NAME, CONTENT_HASH,
                                      CONTENT_LABEL, CONTENT_TYPE,
                                      CONTENT_VENDOR, CONTENT_URL,
                                      CONTENT_GPG_URL);
        content.setType(CONTENT_TYPE);
        content.setLabel(CONTENT_LABEL);
        content.setHash(CONTENT_HASH);
        
        subscription = new Subscription(null, "productId", 1L, new Date(), new Date(),
            new Date());
        subscription.setId(1L);
        
        product.setContent(Collections.singleton(content));
    }
    
    @Test
    public void testContentExtentionCreation() {
        // AAAH!  This should be pulled out to its own test class!
        List<X509ExtensionWrapper> content = extensionUtil.contentExtensions(product);
        assertTrue(isEncodedContentValid(content));
    }

    @Test
    public void contentExtentionsShouldBeAddedDuringCertificateGeneration() 
        throws Exception {
        
        certServiceAdapter.createX509Certificate(mock(Consumer.class),
            subscription, product, mock(Date.class), new BigInteger("1234"),
            keyPair());
        
        verify(mockedPKI).createX509Certificate(any(String.class), 
            argThat(new ListContainsContentExtensions()), 
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class));
    }
    
    private boolean isEncodedContentValid(List<X509ExtensionWrapper> content) {
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
        extends ArgumentMatcher<List<X509ExtensionWrapper>> {
        
        public boolean matches(Object list) {
            return isEncodedContentValid((List) list);
        }
    }
}

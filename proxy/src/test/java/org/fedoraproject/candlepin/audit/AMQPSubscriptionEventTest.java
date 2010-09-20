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
package org.fedoraproject.candlepin.audit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;

import com.google.common.collect.Sets;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class AMQPSubscriptionEventTest {

    @Mock private Config config;
    @Mock private ObjectMapper mapper;
    @Mock private Principal principal;
    @Mock private PKIReader reader;
    private PKIUtility pkiutil;

    private AMQPBusEventAdapter eventAdapter;

    @Before
    public void init() {
        this.eventAdapter = new AMQPBusEventAdapter(config, mapper, reader, pkiutil);
    }

    @Test
    public void subscriptionCreated() throws IOException {
        verifySubscriptionEvent(Event.Type.CREATED);
    }
    
    @Test public void subscriptionModified() throws IOException {
        verifySubscriptionEvent(Event.Type.MODIFIED);
    }

    // This is pretty crazy - should these be broken up into smaller tests
    // for each entry?
    private void verifySubscriptionEvent(Event.Type type) throws IOException {
        // given
        Event event = new Event(type, Event.Target.SUBSCRIPTION,
            principal, 1L, 1L, 33L, "Old Subscription", "New Subscription");

        Subscription sub = mock(Subscription.class, Mockito.RETURNS_DEEP_STUBS);

        when(mapper.readValue("New Subscription", Subscription.class)).thenReturn(sub);
        when(sub.getOwner().getKey()).thenReturn("test-owner");
        when(sub.getProduct().getId()).thenReturn("test-product-id");
        when(sub.getCertificate().getCert()).thenReturn("test-cert");
        when(sub.getCertificate().getKey()).thenReturn("test-key");
        when(config.getString(ConfigProperties.CA_CERT_UPSTREAM)).thenReturn("ca-cert");

        when(sub.getProvidedProducts()).thenReturn(
                Sets.newHashSet(createProductWithContent(
                "content1", "http://dummy.com/content")));

        // when
        this.eventAdapter.apply(event);

        // then
        Map<String, Object> expectedMap = new HashMap<String, Object>();
        expectedMap.put("id", 33L);
        expectedMap.put("owner", "test-owner");
        expectedMap.put("name", "test-product-id");
        expectedMap.put("entitlement_cert", "test-cert");
        expectedMap.put("cert_public_key", "test-key");
        expectedMap.put("ca_cert", "ca-cert");

        Map<String, String> content = new HashMap<String, String>();
        content.put("content_set_label", "content1");
        content.put("content_rel_url", "http://dummy.com/content");

        expectedMap.put("content_sets", Arrays.asList(new Map[] { content }));

        verify(mapper).writeValueAsString(expectedMap);
    }

    private Product createProductWithContent(String label, String url) {
        Product product = new Product(label, label);
        Content content = new Content();
        content.setLabel(label);
        content.setContentUrl(url);
        
        product.setContent(Sets.newHashSet(content));

        return product;
    }

    @Test
    public void subscriptionDeleted() throws IOException {
        // given
        Event event = new Event(Event.Type.DELETED, Event.Target.SUBSCRIPTION,
            principal, 1L, 1L, 33L, "Old Subscription", "New Subscription");

        Subscription sub = mock(Subscription.class, Mockito.RETURNS_DEEP_STUBS);
        
        when(mapper.readValue("New Subscription", Subscription.class)).thenReturn(sub);
        when(sub.getOwner().getKey()).thenReturn("test-owner");

        // when
        this.eventAdapter.apply(event);

        // then
        Map<String, Object> expectedMap = new HashMap<String, Object>();
        expectedMap.put("id", 33L);
        expectedMap.put("owner", "test-owner");

        verify(mapper).writeValueAsString(expectedMap);
    }
}

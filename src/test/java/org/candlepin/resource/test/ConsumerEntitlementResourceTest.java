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
package org.candlepin.resource.test;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.candlepin.controller.Entitler;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.resource.ConsumerEntitlementResource;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * ConsumerEntitlementResourceTest
 */
public class ConsumerEntitlementResourceTest {

    @Mock
    private EntitlementCurator mockedEntitlementCurator;

    private I18n i18n;

    @Before
    public void setUp() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void testProductNoPool() {
        try {
            Consumer c = mock(Consumer.class);
            Owner o = mock(Owner.class);
            SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
            Entitler e = mock(Entitler.class);
            ConsumerCurator cc = mock(ConsumerCurator.class);
            String[] prodIds = {"notthere"};

            when(c.getOwner()).thenReturn(o);
            when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
            when(cc.verifyAndLookupConsumer(eq("fakeConsumer"))).thenReturn(c);
            when(e.bindByProducts(eq(prodIds), eq(c), eq((Date) null)))
                .thenThrow(new RuntimeException());

            ConsumerEntitlementResource cr = new ConsumerEntitlementResource(
                mockedEntitlementCurator, cc, null, i18n, e, sa, null,
                null, null);
            cr.bind("fakeConsumer", null, prodIds, null, null, null, false, null);
        }
        catch (Throwable t) {
            fail("Runtime exception should be caught in ConsumerResource.bind");
        }
    }

    @Test
    public void futureHealing() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<ConsumerInstalledProduct>();
        products.add(cip);

        when(c.getOwner()).thenReturn(o);
        when(cip.getProductId()).thenReturn("product-foo");
        when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
        when(cc.verifyAndLookupConsumer(eq("fakeConsumer"))).thenReturn(c);

        ConsumerEntitlementResource cr = new ConsumerEntitlementResource(
            mockedEntitlementCurator, cc, null, i18n, e, sa, null,
            null, null);
        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);
        cr.bind("fakeConsumer", null, null, null, null, null, false, dtStr);
        verify(e).bindByProducts(eq((String []) null), eq(c), eq(dt));
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerEntitlementResource cer = new ConsumerEntitlementResource(
            mockedEntitlementCurator, consumerCurator, null, i18n,
            null, null, null, null, null);

        cer.bind("fake uuid", "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null);
    }


    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerEntitlementResource cer = new ConsumerEntitlementResource(
            mockedEntitlementCurator, consumerCurator, null, i18n,
            null, null, null, null, null);
        // I guess I'm not testing very much here
        when(consumerCurator.verifyAndLookupConsumer(eq("notarealuuid")))
            .thenThrow(new NotFoundException("not here"));

        cer.bind("notarealuuid", "fake pool uuid", null, null, null,
            null, false, null);
    }
}

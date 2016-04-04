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
package org.candlepin.controller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;




/**
 * PoolManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementCertificateGeneratorTest {

// init bits
        // when(entCertAdapterMock.generateEntitlementCerts(
        //     any(Consumer.class), anyMapOf(String.class, Entitlement.class),
        //     anyMapOf(String.class, Product.class))).thenReturn(entCerts);


    // @Test
    // public void testLazyRegenerate() {
    //     Entitlement e = new Entitlement();
    //     manager.regenerateCertificatesOf(e, false, true);
    //     assertTrue(e.getDirty());
    //     verifyZeroInteractions(entCertAdapterMock);
    // }

    // @Test
    // public void testLazyRegenerateForConsumer() {
    //     Entitlement e = new Entitlement();
    //     Consumer c = new Consumer();
    //     c.addEntitlement(e);
    //     manager.regenerateCertificatesOf(c, true);
    //     assertTrue(e.getDirty());
    //     verifyZeroInteractions(entCertAdapterMock);
    // }

    // @Test
    // public void testNonLazyRegenerate() throws Exception {
    //     Subscription s = TestUtil.createSubscription(getOwner(),
    //         product);
    //     s.setId("testSubId");
    //     pool.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
    //     Consumer con = TestUtil.createConsumer(o);
    //     Entitlement e = new Entitlement(pool, con, 1);
    //     e.setDirty(true);

    //     when(mockSubAdapter.getSubscription(pool.getSubscriptionId())).thenReturn(s);

    //     manager.regenerateCertificatesOf(e, false, false);
    //     assertFalse(e.getDirty());

    //     verify(entCertAdapterMock).generateEntitlementCerts(eq(con), entMapCaptor.capture(),
    //         productMapCaptor.capture());
    //     assertEquals(e, entMapCaptor.getValue().get(pool.getId()));
    //     assertEquals(product, productMapCaptor.getValue().get(pool.getId()));

    //     verify(mockEventSink, times(1)).queueEvent(any(Event.class));
    // }


}

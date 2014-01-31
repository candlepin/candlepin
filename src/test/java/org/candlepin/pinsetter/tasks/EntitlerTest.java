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
package org.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
/**
 * EntitlerTest
 */
public class EntitlerTest {
    private PoolManager pm;
    private EventFactory ef;
    private EventSink sink;
    private I18n i18n;
    private Entitler entitler;
    private Consumer consumer;
    private ConsumerCurator cc;

    private ValidationResult fakeOutResult(String msg) {
        ValidationResult result = new ValidationResult();
        ValidationError err = new ValidationError(msg);
        result.addError(err);
        return result;
    }

    @Before
    public void init() {
        pm = mock(PoolManager.class);
        ef = mock(EventFactory.class);
        sink = mock(EventSink.class);
        cc = mock(ConsumerCurator.class);
        consumer = mock(Consumer.class);
        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
        entitler = new Entitler(pm, cc, i18n, ef, sink);
    }

    @Test
    public void bindByPoolString() throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Entitlement ent = mock(Entitlement.class);

        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        when(pm.find(eq(poolid))).thenReturn(pool);
        when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenReturn(ent);

        List<Entitlement> ents = entitler.bindByPool(poolid, "abcd1234", 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByPool() throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Entitlement ent = mock(Entitlement.class);

        when(pm.find(eq(poolid))).thenReturn(pool);
        when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenReturn(ent);

        List<Entitlement> ents = entitler.bindByPool(poolid, consumer, 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByProductsString() throws EntitlementRefusedException {
        String[] pids = {"prod1", "prod2", "prod3"};
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        entitler.bindByProducts(pids, "abcd1234", null);
        verify(pm).entitleByProducts(eq(consumer), eq(pids), eq((Date) null));
    }

    @Test
    public void bindByProducts() throws EntitlementRefusedException {
        String[] pids = {"prod1", "prod2", "prod3"};
        entitler.bindByProducts(pids, consumer, null);
        verify(pm).entitleByProducts(eq(consumer), eq(pids), eq((Date) null));
    }

    @Test(expected = BadRequestException.class)
    public void nullPool() {
        String poolid = "foo";
        Consumer c = null; // keeps me from casting null
        when(pm.find(eq(poolid))).thenReturn(null);
        entitler.bindByPool(poolid, c, 10);
    }

    @Test(expected = ForbiddenException.class)
    public void someOtherErrorPool() {
        bindByPoolErrorTest("do.not.match");
    }

    @Test(expected = ForbiddenException.class)
    public void consumerTypeMismatchPool() {
        bindByPoolErrorTest("rulefailed.consumer.type.mismatch");
    }

    @Test(expected = ForbiddenException.class)
    public void alreadyHasProductPool() {
        bindByPoolErrorTest("rulefailed.consumer.already.has.product");
    }

    @Test(expected = ForbiddenException.class)
    public void noEntitlementsAvailable() {
        bindByPoolErrorTest("rulefailed.no.entitlements.available");
    }

    @Test
    public void consumerDoesntSupportInstanceBased() {
        String expected = "Unit does not support instance based " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.instance.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportCores() {
        String expected = "Unit does not support core " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.cores.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportRam() {
        String expected = "Unit does not support RAM " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.ram.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportDerived() {
        String expected = "Unit does not support derived products " +
            "data required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.derivedproduct.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private void bindByPoolErrorTest(String msg) {
        try {
            String poolid = "pool10";
            Pool pool = mock(Pool.class);
            EntitlementRefusedException ere = new EntitlementRefusedException(
                fakeOutResult(msg));

            when(pool.getId()).thenReturn(poolid);
            when(pm.find(eq(poolid))).thenReturn(pool);
            when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenThrow(ere);
            entitler.bindByPool(poolid, consumer, 1);
        }
        catch (EntitlementRefusedException e) {
            fail(msg + ": threw unexpected error");
        }
    }

    @Test(expected = ForbiddenException.class)
    public void alreadyHasProduct() {
        bindByProductErrorTest("rulefailed.consumer.already.has.product");
    }

    @Test(expected = ForbiddenException.class)
    public void noEntitlementsForProduct() {
        bindByProductErrorTest("rulefailed.no.entitlements.available");
    }

    @Test(expected = ForbiddenException.class)
    public void mismatchByProduct() {
        bindByProductErrorTest("rulefailed.consumer.type.mismatch");
    }

    @Test(expected = ForbiddenException.class)
    public void virtOnly() {
        bindByProductErrorTest("rulefailed.virt.only");
    }

    @Test(expected = ForbiddenException.class)
    public void allOtherErrors() {
        bindByProductErrorTest("generic.error");
    }

    private void bindByProductErrorTest(String msg) {
        try {
            String[] pids = {"prod1", "prod2", "prod3"};
            EntitlementRefusedException ere = new EntitlementRefusedException(
                fakeOutResult(msg));
            when(pm.entitleByProducts(eq(consumer), eq(pids),
                eq((Date) null))).thenThrow(ere);
            entitler.bindByProducts(pids, consumer, null);
        }
        catch (EntitlementRefusedException e) {
            fail(msg + ": threw unexpected error");
        }
    }

    @Test
    public void events() {
        List<Entitlement> ents = new ArrayList<Entitlement>();
        ents.add(mock(Entitlement.class));
        ents.add(mock(Entitlement.class));

        Event evt1 = mock(Event.class);
        Event evt2 = mock(Event.class);
        when(ef.entitlementCreated(any(Entitlement.class)))
            .thenReturn(evt1)
            .thenReturn(evt2);
        entitler.sendEvents(ents);

        verify(sink).sendEvent(eq(evt1));
        verify(sink).sendEvent(eq(evt2));
    }

    @Test
    public void noEventsWhenEntitlementsNull() {
        entitler.sendEvents(null);
        verify(sink, never()).sendEvent(any(Event.class));
    }

    @Test
    public void noEventsWhenListEmpty() {
        List<Entitlement> ents = new ArrayList<Entitlement>();
        entitler.sendEvents(ents);
        verify(sink, never()).sendEvent(any(Event.class));
    }
}

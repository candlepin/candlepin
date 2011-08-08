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
package org.fedoraproject.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;

/**
 * ChainedListenerTest
 * @version $Rev$
 */
public class ChainedListenerTest {
    private ChainedListener cl;
    private Trigger trigger;
    private TriggerListener listener;
    private JobExecutionContext ctx;


    @Before
    public void setUp() {
        cl = new ChainedListener();
        trigger = mock(Trigger.class);
        listener = mock(TriggerListener.class);
        ctx = mock(JobExecutionContext.class);
        cl.addListener(listener);
    }

    @Test
    public void ctor() {
        ChainedListener clist = new ChainedListener();
        assertNotNull(clist);
        assertEquals(ChainedListener.LISTENER_NAME, clist.getName());
    }

    @Test
    public void testTriggerComplete() {
        cl.triggerComplete(trigger, ctx, 0);
        verify(listener).triggerComplete(eq(trigger), eq(ctx), eq(0));
    }

    @Test
    public void testTriggerFired() {
        cl.triggerFired(trigger, ctx);
        verify(listener).triggerFired(eq(trigger), eq(ctx));
    }

    @Test
    public void testTriggerMisfired() {
        cl.triggerMisfired(trigger);
        verify(listener).triggerMisfired(eq(trigger));
    }

    @Test
    public void vetoFalse() {
        Trigger trigger = mock(Trigger.class);
        TriggerListener listener = mock(TriggerListener.class);
        when(listener.vetoJobExecution(eq(trigger), eq(ctx))).thenReturn(false);
        cl.addListener(listener);

        boolean rc = cl.vetoJobExecution(trigger, ctx);
        assertFalse(rc);
        verify(listener).vetoJobExecution(eq(trigger), eq(ctx));
    }

    @Test
    public void vetoTrue() {
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        Trigger trigger = mock(Trigger.class);
        TriggerListener listener = mock(TriggerListener.class);
        when(listener.vetoJobExecution(eq(trigger), eq(ctx))).thenReturn(true);
        cl.addListener(listener);

        boolean rc = cl.vetoJobExecution(trigger, ctx);
        assertTrue(rc);
        verify(listener).vetoJobExecution(eq(trigger), eq(ctx));
    }

    @Test
    public void addListener() {
        ChainedListener clist = new ChainedListener();
        TriggerListener list1 = mock(TriggerListener.class);
        TriggerListener list2 = mock(TriggerListener.class);
        clist.addListener(list1);
        clist.addListener(list2);

        // now call one of the methods that loops through the listener list
        // to make sure we call it for all the ones given.

        clist.triggerMisfired(trigger);

        verify(list1).triggerMisfired(eq(trigger));
        verify(list2).triggerMisfired(eq(trigger));
    }
}

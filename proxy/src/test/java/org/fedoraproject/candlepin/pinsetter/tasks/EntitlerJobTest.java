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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.ArrayList;
import java.util.List;

/**
 * EntitlerJobTest
 */
public class EntitlerJobTest {

    private Consumer c;
    private Entitler e;

    @Before
    public void init() {
        c = mock(Consumer.class);
        e = mock(Entitler.class);
    }

    @Test
    public void bindByProductsSetup() {
        String[] pids = {"pid1", "pid2", "pid3"};

        JobDetail detail = EntitlerJob.bindByProducts(pids, c, 1, e);
        assertNotNull(detail);
        String[] resultpids = (String[]) detail.getJobDataMap().get("product_ids");
        assertEquals("pid2", resultpids[1]);
        assertEquals(c, (Consumer) detail.getJobDataMap().get("consumer"));
        assertEquals(e, (Entitler) detail.getJobDataMap().get("entitler"));
        assertTrue(detail.getName().startsWith("bind_by_products_"));
    }

    @Test
    public void bindByPoolSetup() {
        String pool = "pool10";

        JobDetail detail = EntitlerJob.bindByPool(pool, c, 1, e);
        assertNotNull(detail);
        String resultpool = (String) detail.getJobDataMap().get("pool_id");
        assertEquals("pool10", resultpool);
        assertEquals(c, (Consumer) detail.getJobDataMap().get("consumer"));
        assertEquals(e, (Entitler) detail.getJobDataMap().get("entitler"));
        assertTrue(detail.getName().startsWith("bind_by_pool_"));
    }

    @Test
    public void bindByPoolExec() throws JobExecutionException {
        String pool = "pool10";

        JobDetail detail = EntitlerJob.bindByPool(pool, c, 1, e);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        List<Entitlement> ents = new ArrayList<Entitlement>();
        when(e.bindByPool(eq(pool), eq(c), eq(1))).thenReturn(ents);

        EntitlerJob job = new EntitlerJob();
        job.execute(ctx);
        verify(e).bindByPool(eq(pool), eq(c), eq(1));
        verify(e).sendEvents(eq(ents));
    }

    @Test
    public void bindByProductsExec() throws JobExecutionException {
        String[] pids = {"pid1", "pid2", "pid3"};

        JobDetail detail = EntitlerJob.bindByProducts(pids, c, 1, e);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        List<Entitlement> ents = new ArrayList<Entitlement>();
        when(e.bindByProducts(eq(pids), eq(c), eq(1))).thenReturn(ents);

        EntitlerJob job = new EntitlerJob();
        job.execute(ctx);
        verify(e).bindByProducts(eq(pids), eq(c), eq(1));
        verify(e).sendEvents(eq(ents));
    }
}

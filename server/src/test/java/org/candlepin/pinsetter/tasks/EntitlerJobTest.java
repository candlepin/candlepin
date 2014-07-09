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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.controller.Entitler;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Entitlement;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * EntitlerJobTest
 */
public class EntitlerJobTest {

    private String consumerUuid;
    private Entitler e;

    @Before
    public void init() {
        consumerUuid = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";
        e = mock(Entitler.class);
    }

    @Test
    public void bindByPoolSetup() {
        String pool = "pool10";

        JobDetail detail = EntitlerJob.bindByPool(pool, consumerUuid, 1);
        assertNotNull(detail);
        String resultpool = (String) detail.getJobDataMap().get("pool_id");
        assertEquals("pool10", resultpool);
        assertEquals(consumerUuid, detail.getJobDataMap().get(JobStatus.TARGET_ID));
        assertTrue(detail.getKey().getName().startsWith("bind_by_pool_"));
    }

    @Test
    public void bindByPoolExec() throws JobExecutionException {
        String pool = "pool10";

        JobDetail detail = EntitlerJob.bindByPool(pool, consumerUuid, 1);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        List<Entitlement> ents = new ArrayList<Entitlement>();
        when(e.bindByPool(eq(pool), eq(consumerUuid), eq(1))).thenReturn(ents);

        EntitlerJob job = new EntitlerJob(e, null);
        job.execute(ctx);
        verify(e).bindByPool(eq(pool), eq(consumerUuid), eq(1));
        verify(e).sendEvents(eq(ents));
    }

    /**
     * At first glance this seems like a stupid test of Quartz functionality,
     * but its intent is to ensure that what we put into the JobDataMap can
     * be serialized to the database for Quartz clustering. If this test fails
     * 9/10 times one of the objects added does not implement the Serializable
     * interface.
     * @throws IOException
     */
    @Test
    public void serializeJobDataMapForPool() throws IOException {
        JobDetail detail = EntitlerJob.bindByPool("pool10", consumerUuid, 1);
        serialize(detail.getJobDataMap());
    }

    @Test
    public void recoveryIsFalse() {
        JobDetail detail = EntitlerJob.bindByPool("pool10", consumerUuid, 1);
        assertFalse(detail.requestsRecovery());
        assertFalse(detail.isDurable());
    }

    private void serialize(Object obj) throws IOException {
        ObjectOutput out = new ObjectOutputStream(new FileOutputStream("obj.ser"));
        out.writeObject(obj);
        out.close();
    }

    @Test(expected = JobExecutionException.class)
    public void handleException() throws JobExecutionException {
        String pool = "pool10";
        JobDetail detail = EntitlerJob.bindByPool(pool, consumerUuid, 1);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(e.bindByPool(eq(pool), eq(consumerUuid), eq(1))).thenThrow(
            new ForbiddenException("job should fail"));

        EntitlerJob job = new EntitlerJob(e, null);
        job.execute(ctx);
    }

    @After
    public void cleanup() {
        new File("obj.ser").delete();
    }
}

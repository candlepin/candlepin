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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.candlepin.controller.Entitler;
import org.candlepin.model.Entitlement;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * EntitleByProductsJobTest
 */
public class EntitleByProductsJobTest {

    private String consumerUuid;
    private Entitler e;

    @Before
    public void init() {
        consumerUuid = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";
        e = mock(Entitler.class);
    }

    @Test
    public void bindByProductsSetup() {
        String[] pids = {"pid1", "pid2", "pid3"};

        JobDetail detail = EntitleByProductsJob.bindByProducts(pids, consumerUuid, null);
        assertNotNull(detail);
        String[] resultpids = (String[]) detail.getJobDataMap().get("product_ids");
        assertEquals("pid2", resultpids[1]);
        assertEquals(consumerUuid, detail.getJobDataMap().get(JobStatus.TARGET_ID));
        assertTrue(detail.getKey().getName().startsWith("bind_by_products_"));
    }

    @Test
    public void bindByProductsExec() throws JobExecutionException {
        String[] pids = {"pid1", "pid2", "pid3"};

        JobDetail detail = EntitleByProductsJob.bindByProducts(pids, consumerUuid, null);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());

        List<Entitlement> ents = new ArrayList<Entitlement>();
        when(e.bindByProducts(eq(pids), eq(consumerUuid),
            eq((Date) null))).thenReturn(ents);

        EntitleByProductsJob job = new EntitleByProductsJob(e, null);
        job.execute(ctx);
        verify(e).bindByProducts(eq(pids), eq(consumerUuid), eq((Date) null));
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
    public void serializeJobDataMapForProducts() throws IOException {
        String[] pids = {"pid1", "pid2", "pid3"};
        JobDetail detail = EntitleByProductsJob.bindByProducts(pids, consumerUuid, null);
        serialize(detail.getJobDataMap());
    }

    private void serialize(Object obj) throws IOException {
        ObjectOutput out = new ObjectOutputStream(new FileOutputStream("obj.ser"));
        out.writeObject(obj);
        out.close();
    }
}

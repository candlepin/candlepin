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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.policy.EntitlementRefusedException;

import org.hibernate.mapping.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * EntitlerJobTest
 */
public class EntitlerJobTest {

    private String consumerUuid;
    private Consumer consumer;
    private Entitler e;

    @Before
    public void init() {
        consumerUuid = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";
        consumer = new Consumer("Test Consumer", "test-consumer", new Owner("test-owner"),
            new ConsumerType("system"));
        consumer.setUuid(consumerUuid);
        e = mock(Entitler.class);
    }

    @Test
    public void bindByPoolSetup() {
        String pool = "pool10";

        PoolIdAndQuantity[] pQs = new PoolIdAndQuantity[1];
        pQs[0] = new PoolIdAndQuantity(pool, 1);
        JobDetail detail = EntitlerJob.bindByPoolAndQuantities(consumer, pQs);
        assertNotNull(detail);
        PoolIdAndQuantity[] resultPools = (PoolIdAndQuantity[]) detail.getJobDataMap().get(
                "pool_and_quanities");
        assertEquals("pool10", resultPools[0].getPoolId());
        assertEquals(1, resultPools[0].getQuantity().intValue());
        assertEquals(consumerUuid, detail.getJobDataMap().get(JobStatus.TARGET_ID));
        assertTrue(detail.getKey().getName().startsWith("bind_by_pool_"));
    }

    @Test
    public void bindByPoolExec() throws JobExecutionException, EntitlementRefusedException {
        String pool = "pool10";

        PoolIdAndQuantity[] pQs = new PoolIdAndQuantity[1];
        pQs[0] = new PoolIdAndQuantity(pool, 1);
        JobDetail detail = EntitlerJob.bindByPoolAndQuantities(consumer, pQs);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        List<Entitlement> ents = new ArrayList<Entitlement>();

        Class<HashMap<String, Integer>> className = (Class<HashMap<String, Integer>>) (Class) Map.class;
        ArgumentCaptor<HashMap<String, Integer>> pqMapCaptor = ArgumentCaptor.forClass(className);
        when(e.bindByPoolQuantities(eq(consumerUuid), pqMapCaptor.capture())).thenReturn(ents);
        HashMap<String, Integer> pqMap = pqMapCaptor.getValue();

        assertEquals(1, pqMap.get(pool).intValue());

        EntitlerJob job = new EntitlerJob(e, null, null, null);
        job.execute(ctx);
        verify(e).bindByPoolQuantities(eq(consumerUuid), eq(pqMap));
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
        PoolIdAndQuantity[] pQs = new PoolIdAndQuantity[1];
        pQs[0] = new PoolIdAndQuantity("pool10", 1);
        JobDetail detail = EntitlerJob.bindByPoolAndQuantities(consumer, pQs);
        serialize(detail.getJobDataMap());
    }

    @Test
    public void recoveryIsFalse() {
        PoolIdAndQuantity[] pQs = new PoolIdAndQuantity[1];
        pQs[0] = new PoolIdAndQuantity("pool10", 1);
        JobDetail detail = EntitlerJob.bindByPoolAndQuantities(consumer, pQs);
        assertFalse(detail.requestsRecovery());
        assertTrue(detail.isDurable());
    }

    private void serialize(Object obj) throws IOException {
        ObjectOutput out = new ObjectOutputStream(new FileOutputStream("obj.ser"));
        out.writeObject(obj);
        out.close();
    }

    @Test(expected = JobExecutionException.class)
    public void handleException() throws JobExecutionException, EntitlementRefusedException {
        PoolIdAndQuantity[] pQs = new PoolIdAndQuantity[1];
        pQs[0] = new PoolIdAndQuantity("pool10", 1);
        JobDetail detail = EntitlerJob.bindByPoolAndQuantities(consumer, pQs);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        Class<HashMap<String, Integer>> className = (Class<HashMap<String, Integer>>) (Class) Map.class;
        ArgumentCaptor<HashMap<String, Integer>> pqMapCaptor = ArgumentCaptor.forClass(className);
        when(e.bindByPoolQuantities(eq(consumerUuid), pqMapCaptor.capture())).thenThrow(
                new ForbiddenException("job should fail"));

        EntitlerJob job = new EntitlerJob(e, null, null, null);
        job.execute(ctx);
    }

    @After
    public void cleanup() {
        new File("obj.ser").delete();
    }
}

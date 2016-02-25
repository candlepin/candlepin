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

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.compliance.ComplianceRules;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Date;

import static org.mockito.Mockito.*;
/**
 * CancelJobJobTest
 */
public class ConsumerComplianceJobTest {
    private ConsumerComplianceJob job;
    private Consumer consumer;
    private String consumerUuid;

    @Mock private ConsumerCurator curator;
    @Mock private ComplianceRules rules;
    @Mock private JobExecutionContext ctx;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        job = new ConsumerComplianceJob(curator, rules);
        consumerUuid = "49bd6a8f-e9f8-40cc-b8d7-86cafd687a0e";
        consumer = new Consumer("Test Consumer", "test-consumer", new Owner("test-owner"),
            new ConsumerType("system"));
        consumer.setUuid(consumerUuid);
    }

    @Test
    public void scheduleSimpleStatusCheck() throws JobExecutionException {
        JobDetail detail = job.scheduleStatusCheck(consumer, new Date(), true, false);
        assertNotNull(detail);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(curator.verifyAndLookupConsumer(consumerUuid)).thenReturn(consumer);
        job.execute(ctx);
        verify(curator).lockAndLoad(eq(consumer));
        verify(rules).getStatus(eq(consumer), any(Date.class), eq(true), eq(false));
    }

    @Test
    public void scheduleWithForceUpdateCheck() throws JobExecutionException {
        JobDetail detail = job.scheduleWithForceUpdate(consumer);
        assertNotNull(detail);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(curator.verifyAndLookupConsumer(consumerUuid)).thenReturn(consumer);
        job.execute(ctx);
        verify(curator).lockAndLoad(eq(consumer));
        verify(rules).getStatus(eq(consumer), any(Date.class), eq(false), eq(false));
        verify(curator).update(consumer);
    }

    @Test(expected = JobExecutionException.class)
    public void exceptionOnSchedule() throws JobExecutionException {
        JobDetail detail = job.scheduleWithForceUpdate(consumer);
        assertNotNull(detail);
        when(ctx.getMergedJobDataMap()).thenReturn(detail.getJobDataMap());
        when(curator.verifyAndLookupConsumer(consumerUuid)).thenReturn(consumer);
        when(rules.getStatus(eq(consumer), any(Date.class), eq(false), eq(false))).thenThrow(
                new RuleExecutionException("random exception"));
        job.execute(ctx);
    }

}

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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.ConsumerComplianceJob;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;



/**
 * ConsumerManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerManagerTest {

    @Mock private JobCurator jobCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ComplianceRules complianceRules;

    private ConsumerManager consumerManager;

    @Before
    public void init() throws Exception {

        this.consumerManager = new ConsumerManager();
        Field f = ConsumerManager.class.getDeclaredField("jobCurator");
        f.setAccessible(true);
        f.set(consumerManager, jobCurator);

        f = ConsumerManager.class.getDeclaredField("consumerCurator");
        f.setAccessible(true);
        f.set(consumerManager, consumerCurator);

        f = ConsumerManager.class.getDeclaredField("complianceRules");
        f.setAccessible(true);
        f.set(consumerManager, complianceRules);

    }

    @Test
    public void testComputeComplianceIfAsyncScheduled() {
        Consumer c1 = TestUtil.createConsumer();
        Consumer c2 = TestUtil.createConsumer();
        Consumer c3 = TestUtil.createConsumer();
        List<Consumer> consumers = Arrays.asList(c1, c2, c3);

        JobStatus j2 = mock(JobStatus.class);
        when(j2.getId()).thenReturn("j2");
        when(j2.getTargetId()).thenReturn(c2.getUuid());
        JobStatus j3 = mock(JobStatus.class);
        when(j3.getId()).thenReturn("j3");
        when(j3.getTargetId()).thenReturn(c3.getUuid());
        List<JobStatus> jobs = Arrays.asList(j2, j3);

        Class<List<String>> listClass = (Class<List<String>>) (Class) ArrayList.class;
        ArgumentCaptor<List<String>> argConsumers = ArgumentCaptor.forClass(listClass);
        when(this.jobCurator.getUnfinishedJobsByTargetIds(
            eq(ConsumerComplianceJob.class), argConsumers.capture())).thenReturn(jobs);

        consumerManager.computeComplianceIfAsyncScheduled(consumers);
        assertEquals(3, argConsumers.getValue().size());

        verify(this.complianceRules, times(1)).getStatus(eq(c2), isNull(Date.class), eq(false), eq(false));
        verify(this.complianceRules, times(1)).getStatus(eq(c3), isNull(Date.class), eq(false), eq(false));

        List<String> jobIds = Arrays.asList("j2", "j3");
        verify(this.jobCurator, times(1)).cancelIfNotRunning(jobIds);

    }
}

/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.audit.EventSink;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.EmptyCandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.EntityManager;


public class HealEntireOrgJobTest {

    private Entitler entitler;
    private EventSink eventSink;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @BeforeEach
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK);

        this.entitler = mock(Entitler.class);
        this.eventSink = mock(EventSink.class);
        this.consumerCurator = mock(ConsumerCurator.class);
        this.ownerCurator = mock(OwnerCurator.class);

        EntityManager entityManager = mock(EntityManager.class);
        TestUtil.mockTransactionalFunctionality(entityManager, this.consumerCurator);
    }

    private HealEntireOrgJob createJob() {
        return new HealEntireOrgJob(entitler, eventSink, consumerCurator, ownerCurator, i18n);
    }

    @Test
    public void testHealEntireOrgJob() throws JobExecutionException {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        owner.setContentAccessMode("entitlement");
        doReturn(owner).when(ownerCurator).getByKey(owner.getKey());

        Consumer consumer1 = TestUtil.createConsumer(owner);
        Consumer consumer2 = TestUtil.createConsumer(owner);
        Map<String, Consumer> consumers = new HashMap<String, Consumer>() {
            {
                put(consumer1.getUuid(), consumer1);
                put(consumer2.getUuid(), consumer2);
            }
        };

        doReturn(new EmptyCandlepinQuery<String>() {
            @Override
            public List<String> list() {
                return Arrays.asList(consumer1.getUuid(), consumer2.getUuid());
            }
        }).when(ownerCurator).getConsumerUuids(owner);

        doAnswer(new Answer<Consumer>() {
            @Override
            public Consumer answer(InvocationOnMock invocation) throws Throwable {
                return consumers.get(invocation.getArguments()[0].toString());
            }
        }).when(consumerCurator).getConsumer(anyString());

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = this.createJob();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        healEntireOrgJob.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        StringBuilder expectedResult = new StringBuilder();
        expectedResult.append("Successfully healed consumer with UUID: ").append(consumer1.getUuid())
            .append("\n");
        expectedResult.append("Successfully healed consumer with UUID: ").append(consumer2.getUuid())
            .append("\n");

        assertEquals(expectedResult.toString(), result.toString());
    }

    @Test
    public void testGetConsumerException() throws JobExecutionException {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        owner.setContentAccessMode("entitlement");
        // owner.setAutobindDisabled(false);
        doReturn(owner).when(ownerCurator).getByKey(owner.getKey());

        Consumer consumer1 = TestUtil.createConsumer(owner);
        Consumer consumer2 = TestUtil.createConsumer(owner);

        doReturn(new EmptyCandlepinQuery<String>() {
            @Override
            public List<String> list() {
                return Arrays.asList(consumer1.getUuid(), consumer2.getUuid());
            }
        }).when(ownerCurator).getConsumerUuids(owner);

        doAnswer(new Answer<Consumer>() {

            @Override
            public Consumer answer(InvocationOnMock invocation) throws Throwable {
                String uuid = invocation.getArguments()[0].toString();

                if (consumer2.getUuid().equals(uuid)) {
                    return consumer2;
                }

                throw new Exception("Consumer not found");
            }
        }).when(consumerCurator).getConsumer(anyString());

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = this.createJob();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        healEntireOrgJob.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        final StringBuilder expectedResult = new StringBuilder();
        expectedResult.append("Healing failed for consumer with UUID: ").append(consumer1.getUuid())
            .append("\n");
        expectedResult.append("Successfully healed consumer with UUID: ").append(consumer2.getUuid())
            .append("\n");

        assertEquals(expectedResult.toString(), result.toString());
    }

    @Test
    public void testAutoBindDisabledOwner() {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        owner.setAutobindDisabled(true);
        doReturn(owner).when(ownerCurator).getByKey(owner.getKey());

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = this.createJob();
        assertThrows(JobExecutionException.class, () -> healEntireOrgJob.execute(context));
    }

    @Test
    public void testContentAccessEnabledOwner() {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        owner.setContentAccessMode("org_environment");
        doReturn(owner).when(ownerCurator).getByKey(owner.getKey());

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = this.createJob();
        assertThrows(JobExecutionException.class, () -> healEntireOrgJob.execute(context));
    }

    @Test
    public void testJobConfigSetOwnerAndEntitleDate() {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        assertDoesNotThrow(config::validate);

        assertEquals(owner, config.getContextOwner());
        JobArguments jobArguments = config.getJobArguments();

        assertTrue(jobArguments.containsKey(HealEntireOrgJob.OWNER_KEY));
        assertEquals(owner.getKey(), jobArguments.getAsString(HealEntireOrgJob.OWNER_KEY));

        assertTrue(jobArguments.containsKey(HealEntireOrgJob.ENTITLE_DATE_KEY));
        assertEquals(entitleDate, jobArguments.getAs(HealEntireOrgJob.ENTITLE_DATE_KEY, Date.class));
    }

    private Owner createTestOwner(String key, String logLevel) {
        Owner owner = TestUtil.createOwner();

        owner.setId(TestUtil.randomString());
        owner.setKey(key);
        owner.setLogLevel(logLevel);

        return owner;
    }

}

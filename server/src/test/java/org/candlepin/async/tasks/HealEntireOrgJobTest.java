/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.EmptyCandlepinQuery;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HealEntireOrgJobTest extends BaseJobTest {

    private OwnerCurator ownerCurator;
    private Entitler entitler;
    private ConsumerCurator consumerCurator;
    private I18n i18n;

    @BeforeEach
    public void init() {
        super.inject();
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK);

        ownerCurator = mock(OwnerCurator.class);
        consumerCurator = mock(ConsumerCurator.class);
        entitler = mock(Entitler.class);
    }

    @Test
    public void testHealEntireOrgJob() throws JobExecutionException {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
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

        when(consumerCurator.getConsumer(anyString())).then(new Answer<Consumer>() {

            @Override
            public Consumer answer(InvocationOnMock invocation) throws Throwable {
                return consumers.get(invocation.getArguments()[0].toString());
            }
        });

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = new HealEntireOrgJob(entitler, consumerCurator, ownerCurator,
            i18n);
        Object result = healEntireOrgJob.execute(context);
        final StringBuilder expectedResult = new StringBuilder();
        expectedResult.append("Successfully healed consumer with UUID: ").append(consumer1.getUuid())
            .append("\n");
        expectedResult.append("Successfully healed consumer with UUID: ").append(consumer2.getUuid())
            .append("\n");

        assertEquals(expectedResult.toString(), result.toString());
    }

    @Test
    public void testGetConsumerException() throws JobExecutionException {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        doReturn(owner).when(ownerCurator).getByKey(owner.getKey());

        Consumer consumer1 = TestUtil.createConsumer(owner);
        Consumer consumer2 = TestUtil.createConsumer(owner);

        doReturn(new EmptyCandlepinQuery<String>() {

            @Override
            public List<String> list() {
                return Arrays.asList(consumer1.getUuid(), consumer2.getUuid());
            }
        }).when(ownerCurator).getConsumerUuids(owner);
        when(consumerCurator.getConsumer(anyString())).then(new Answer<Consumer>() {

            @Override
            public Consumer answer(InvocationOnMock invocation) throws Throwable {
                String uuid = invocation.getArguments()[0].toString();
                if (consumer1.getUuid().equals(uuid)) {
                    throw new Exception("Consumer not found");
                }
                return consumer2;
            }
        });

        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobArguments()).thenReturn(config.getJobArguments());

        HealEntireOrgJob healEntireOrgJob = new HealEntireOrgJob(entitler, consumerCurator, ownerCurator,
            i18n);
        Object result = healEntireOrgJob.execute(context);
        final StringBuilder expectedResult = new StringBuilder();
        expectedResult.append("Healing failed for UUID: ").append(consumer1.getUuid()).append("\n");
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

        HealEntireOrgJob healEntireOrgJob = new HealEntireOrgJob(entitler, consumerCurator, ownerCurator,
            i18n);
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

        HealEntireOrgJob healEntireOrgJob = new HealEntireOrgJob(entitler, consumerCurator, ownerCurator,
            i18n);
        assertThrows(JobExecutionException.class, () -> healEntireOrgJob.execute(context));

    }

    @Test
    public void testJobConfigSetOwnerAndEntitleDate() {
        Owner owner = this.createTestOwner(HealEntireOrgJob.OWNER_KEY, "log_level");
        Date entitleDate = new Date();
        JobConfig config = HealEntireOrgJob.createJobConfig().setOwner(owner).setEntitleDate(entitleDate);

        assertDoesNotThrow(config::validate);

        Map<String, String> metadata = config.getJobMetadata();

        assertTrue(metadata.containsKey(LoggingFilter.OWNER_KEY));
        assertEquals(owner.getKey(), metadata.get(LoggingFilter.OWNER_KEY));
        assertEquals(owner.getLogLevel(), config.getLogLevel());
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

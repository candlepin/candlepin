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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;


public class InactiveConsumerCleanerJobTest extends DatabaseTestFixture {

    private InactiveConsumerCleanerJob inactiveConsumerCleanerJob;
    private Owner owner;
    private ConsumerType consumerType;

    @BeforeEach
    public void beforeEach() {
        owner = this.createOwner(TestUtil.randomString(), TestUtil.randomString());
        consumerType = this.createConsumerType(false);
        inactiveConsumerCleanerJob = new InactiveConsumerCleanerJob(this.config,
            this.consumerCurator,
            this.deletedConsumerCurator,
            this.identityCertificateCurator,
            this.caCertCurator,
            this.certSerialCurator);
    }

    @Test
    public void testExecutionWithInactiveCheckedInTime() throws JobExecutionException {
        Consumer inactiveConsumer =
            createConsumer(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10);
        Consumer activeConsumer =
            createConsumer(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10);

        JobExecutionContext context = mock(JobExecutionContext.class);
        inactiveConsumerCleanerJob.execute(context);

        consumerCurator.flush();
        consumerCurator.clear();

        Collection<Consumer> activeConsumers = this.consumerCurator.getConsumers(
            Arrays.asList(inactiveConsumer.getId(),
            activeConsumer.getId()));

        assertEquals(1, activeConsumers.size());
        assertEquals(activeConsumer, activeConsumers.iterator().next());

        DeletedConsumer deletedConsumer = this.deletedConsumerCurator
            .findByConsumerUuid(inactiveConsumer.getUuid());
        assertNotNull(deletedConsumer);
        assertEquals(inactiveConsumer.getUuid(), deletedConsumer.getConsumerUuid());
        assertEquals(inactiveConsumer.getName(), deletedConsumer.getConsumerName());

        assertNull(this.deletedConsumerCurator.findByConsumer(activeConsumer));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testExecutionWithInvalidCheckedInRetentionConfig(int rententionDays)
        throws JobExecutionException {
        setRetentionDaysConfiguration(InactiveConsumerCleanerJob.CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS,
            rententionDays);

        JobExecutionContext context = mock(JobExecutionContext.class);
        assertThrows(JobExecutionException.class, () -> inactiveConsumerCleanerJob.execute(context));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testExecutionWithInvalidLastUpdatedRetentionConfig(int rententionDays)
        throws JobExecutionException {
        setRetentionDaysConfiguration(InactiveConsumerCleanerJob.CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS,
            rententionDays);

        JobExecutionContext context = mock(JobExecutionContext.class);
        assertThrows(JobExecutionException.class, () -> inactiveConsumerCleanerJob.execute(context));
    }

    private Consumer createConsumer(Integer lastCheckedInDaysAgo) {
        Consumer newConsumer = new Consumer();
        newConsumer.setOwner(owner);
        newConsumer.setType(consumerType);
        newConsumer.setName(TestUtil.randomString());

        if (lastCheckedInDaysAgo != null) {
            newConsumer.setLastCheckin(Util.addDaysToDt(lastCheckedInDaysAgo * -1));
        }

        return this.consumerCurator.create(newConsumer);
    }

    private void setRetentionDaysConfiguration(String configurationName, int retentionDays) {
        String configuration = ConfigProperties.jobConfig(
            InactiveConsumerCleanerJob.JOB_KEY,
            configurationName);
        this.config.setProperty(configuration, String.valueOf(retentionDays));
    }
}

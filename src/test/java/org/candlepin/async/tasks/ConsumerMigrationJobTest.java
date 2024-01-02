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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ConsumerMigration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsumerMigrationJobTest {

    private static final String ORIGIN = "orig_owner_key";
    private static final String DESTINATION = "dest_owner_key";

    @Mock
    private ConsumerMigration consumerMigration;

    @Test
    void allArgsOk() {
        final JobConfig config = ConsumerMigrationJob.createConfig()
            .setOriginOwner(ORIGIN)
            .setDestinationOwner(DESTINATION);

        assertDoesNotThrow(config::validate);
    }

    @Test
    void originMustBePresent() {
        final JobConfig config = ConsumerMigrationJob.createConfig()
            .setDestinationOwner(DESTINATION);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void originCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> ConsumerMigrationJob.createConfig().setOriginOwner(""));
    }

    @Test
    void destinationMustBePresent() {
        final JobConfig config = ConsumerMigrationJob.createConfig()
            .setOriginOwner(ORIGIN);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    void destinationCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
            () -> ConsumerMigrationJob.createConfig().setDestinationOwner(""));
    }

    @Test
    void shouldExecuteMigration() throws JobExecutionException {
        final JobConfig config = ConsumerMigrationJob.createConfig()
            .setOriginOwner(ORIGIN)
            .setDestinationOwner(DESTINATION);
        ConsumerMigrationJob job = new ConsumerMigrationJob(this.consumerMigration);

        job.execute(createJobContext(config));

        verify(this.consumerMigration, times(1)).migrate(ORIGIN, DESTINATION);
    }

    private JobExecutionContext createJobContext(final JobConfig config) {
        final JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getJobArguments()).thenReturn(config.getJobArguments());
        return ctx;
    }

}

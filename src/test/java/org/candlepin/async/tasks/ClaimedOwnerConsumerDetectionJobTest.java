/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.model.ClaimedOwner;
import org.candlepin.model.OwnerCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class ClaimedOwnerConsumerDetectionJobTest {

    private OwnerCurator ownerCurator;
    private JobManager jobManager;

    private ClaimedOwnerConsumerDetectionJob job;

    @BeforeEach
    public void setUp() {
        this.ownerCurator = mock(OwnerCurator.class);
        this.jobManager = mock(JobManager.class);

        job = new ClaimedOwnerConsumerDetectionJob(this.ownerCurator, this.jobManager);
    }

    @Test
    void nothingToDo() throws JobExecutionException {
        job.execute(null);

        verifyNoInteractions(this.jobManager);
    }

    @Test
    void shouldMigrateFoundOwners() throws JobException {
        when(this.ownerCurator.findClaimedUnMigratedOwners()).thenReturn(List.of(
            new ClaimedOwner("anon_owner_1", "dest_owner"),
            new ClaimedOwner("anon_owner_2", "dest_owner"),
            new ClaimedOwner("anon_owner_3", "dest_owner")
        ));

        job.execute(null);

        verify(this.jobManager, times(3)).queueJob(any(JobConfig.class));
    }

}

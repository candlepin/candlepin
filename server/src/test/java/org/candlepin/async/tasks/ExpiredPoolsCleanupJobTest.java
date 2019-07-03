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

import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.PoolManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the ExpiredPoolsCleanupJob class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExpiredPoolsCleanupJobTest {

    private PoolManager poolManager;

    @BeforeEach
    public void init() {
        this.poolManager = mock(PoolManager.class);
    }

    private ExpiredPoolsCleanupJob createJobInstance() {
        return new ExpiredPoolsCleanupJob(this.poolManager);
    }

    @Test
    public void execute() throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);
        ExpiredPoolsCleanupJob job = this.createJobInstance();
        job.execute(context);

        verify(this.poolManager).cleanupExpiredPools();
    }

}

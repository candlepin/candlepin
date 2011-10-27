/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.wideplay.warp.persist.WorkManager;

import org.junit.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * TransactionalPinsetterJobTest
 */
public class TransactionalPinsetterJobTest {

    @Test
    public void execute() throws Exception {
        WorkManager manager = mock(WorkManager.class);
        Job wrapped = mock(Job.class);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        TransactionalPinsetterJob tpj = new TransactionalPinsetterJob(
            wrapped, manager);

        tpj.execute(ctx);

        verify(manager).beginWork();
        verify(wrapped).execute(eq(ctx));
        verify(manager).endWork();
        verifyNoMoreInteractions(wrapped);
        verifyNoMoreInteractions(manager);
    }

    @Test
    public void sameJob() {
        Job wrapped = mock(Job.class);
        TransactionalPinsetterJob tpj = new TransactionalPinsetterJob(wrapped, null);
        assertEquals(wrapped, tpj.getWrappedJob());
    }
}

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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.candlepin.model.JobCurator;

import org.junit.Test;

import java.util.Date;


/**
 * JobCleanerTest
 */
public class JobCleanerTest {

    @Test
    public void execute() throws Exception {
        JobCurator curator = mock(JobCurator.class);
        JobCleaner cleaner = new JobCleaner(curator);
        cleaner.execute(null);
        verify(curator).cleanUpOldCompletedJobs(any(Date.class));
        verify(curator).cleanupAllOldJobs(any(Date.class));
    }
}

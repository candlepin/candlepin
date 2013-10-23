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
package org.candlepin.pinsetter.core;

import org.candlepin.pinsetter.tasks.KingpinJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

/**
 * TestJob
 * @version $Rev$
 */
public class TestJob extends KingpinJob {

    private boolean ran = false;

    /**
     * @param unitOfWork
     */
    @Inject
    public TestJob(UnitOfWork unitOfWork) {
        super(unitOfWork);
    }

    @Override
    public void toExecute(JobExecutionContext arg0In)
        throws JobExecutionException {
        ran = true;
    }

    public boolean verify() {
        return ran;
    }
}

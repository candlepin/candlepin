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
package org.candlepin.async;

import org.candlepin.model.AsyncJobStatus;



/**
 * The JobConstraint interface provides a standard API for all job queuing constraints.
 */
@FunctionalInterface
public interface JobConstraint {

    /**
     * Tests this constraint using the given inbound job against the given existing job. If the
     * inbound job conflicts with the existing job, this method returns true.
     *
     * @param inbound
     *  the inbound job status to test
     *
     * @param existing
     *  the existing job status to test
     *
     * @throws IllegalArgumentException
     *  if either the inbound or existing job is null
     *
     * @return
     *  true if the queuing of the inbound job is constrained by the existing job; false otherwise
     */
    boolean test(AsyncJobStatus inbound, AsyncJobStatus existing);

}

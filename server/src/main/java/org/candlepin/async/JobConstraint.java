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

import java.util.Collection;



/**
 * The JobConstraint interface provides a standard API for all job queuing constraints.
 */
@FunctionalInterface
public interface JobConstraint {

    /**
     * Tests this constraint using the given inbound job against the provided existing, non-terminal
     * jobs. If the inbound job is constrained by any of the existing jobs, this method should
     * return a collection of those constraining jobs. Otherwise, this method should return null or
     * and empty collection.
     *
     * @param inbound
     *  the inbound job status to test
     *
     * @param existing
     *  the collection of existing, non-terminal job statuses to test against
     *
     * @throws IllegalArgumentException
     *  if either the inbound job is null, or the existing jobs is null or contains null elements
     *
     * @return
     *  a collection of colliding jobs, or null to indicate no job collisions
     */
    Collection<AsyncJobStatus> test(AsyncJobStatus inbound, Collection<AsyncJobStatus> existing);

}

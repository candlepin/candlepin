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
package org.candlepin.async;

import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;

import java.util.Collection;



/**
 * The JobConstraint interface provides a standard API for all job queuing constraints.
 */
@FunctionalInterface
public interface JobConstraint {

    /**
     * Tests this constraint using the given inbound job. If the inbound job is constrained this
     * method should return a collection containing the IDs of the constraining jobs. Otherwise,
     * this method should return null or an empty collection.
     *
     * @param jobCurator
     *  an instance of the current jobCurator to use for performing database operations
     *
     * @param inbound
     *  the inbound job status to test
     *
     * @throws IllegalArgumentException
     *  if the inbound job is null
     *
     * @return
     *  a collection of constraining job IDs, or null to indicate no job collisions
     */
    Collection<String> test(AsyncJobStatusCurator jobCurator, AsyncJobStatus inbound);

}

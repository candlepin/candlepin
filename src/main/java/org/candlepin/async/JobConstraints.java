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

import org.candlepin.async.impl.ThrottledByJobKeyConstraint;
import org.candlepin.async.impl.UniqueByArgConstraint;

import java.util.List;



/**
 * The JobConstraints class provides a collection of factory methods for quickly building job
 * constraints.
 */
public class JobConstraints {

    private JobConstraints() {
        throw new UnsupportedOperationException("This class should not be constructed");
    }

    /**
     * Creates a new unique-by-argument constraint, using the specified parameters as the target of
     * the constraint.
     *
     * @param params
     *  The parameter, or parameters, to use as the target of the new constraint
     *
     * @return
     *  a new unique-by-argument constraint
     */
    public static JobConstraint uniqueByArguments(String... params) {
        return new UniqueByArgConstraint(params);
    }

    /**
     * Creates a new unique-by-argument constraint, using the specified parameters as the target of
     * the constraint.
     *
     * @param params
     *  The parameters to use as the target of the new constraint
     *
     * @return
     *  a new unique-by-argument constraint
     */
    public static JobConstraint uniqueByArguments(List<String> params) {
        return new UniqueByArgConstraint(params);
    }

    /**
     * Creates a new throttling constraint, using the specified job key as the target of
     * the constraint and limit for the throttling.
     *
     * @param key
     *  The job key by which you want to throttle jobs
     * @param limit
     *  The maximum number of jobs running with the given key
     *
     * @return
     *  a new throttling constraint
     */
    public static JobConstraint throttledByJobKey(String key, int limit) {
        return new ThrottledByJobKeyConstraint(key, limit);
    }
}

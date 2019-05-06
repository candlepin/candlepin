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

import org.candlepin.async.impl.UniqueByArgConstraint;



/**
 * The JobConstraints class provides a collection of factory methods for quickly building job
 * constraints.
 */
public class JobConstraints {

    private JobConstraints() {
        throw new UnsupportedOperationException("This class should not be constructed");
    }

    /**
     * Creates a new unique-by-argument constraint, using the specified parameter as the target of
     * the constraint.
     *
     * @param param
     *  The parameter to use as the target of the new constraint
     *
     * @return
     *  a new unique-by-argument constraint
     */
    public static JobConstraint uniqueByArgument(String param) {
        return new UniqueByArgConstraint(param);
    }

}

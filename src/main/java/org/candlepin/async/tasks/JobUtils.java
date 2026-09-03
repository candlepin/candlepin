/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;

/**
 * Utility class providing common helper methods for async jobs.
 */
public class JobUtils {

    private JobUtils() {
        // default ctor
    }

    /**
     * Checks whether the given exception's cause chain contains a transient constraint violation
     * or deadlock, indicating the job failed only because it collided with another concurrent
     * operation, rather than a genuine problem.
     *
     * @param exception
     *  the exception to inspect
     *
     * @return
     *  true if the exception was caused by a transient constraint violation or deadlock; false
     *  otherwise
     */
    public static boolean isCausedByTransientRace(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException || cause instanceof LockAcquisitionException) {
                return true;
            }
        }

        return false;
    }
}

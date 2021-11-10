/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
package org.candlepin.controller.util;

import org.hibernate.exception.ConstraintViolationException;

import java.util.function.Supplier;



/**
 * Wrapper class providing retry logic to handle ConstraintViolationExceptions occuring as a result
 * of parallel requests attempting to create the same versioned entities at the same time. In many
 * cases, only a single retry is needed; but for operations which create many entities (such as
 * refresh), several retries may be necessary to avoid complete failure.
 */
public class EntityVersioningRetryWrapper {

    /** The name of the constraint that will trigger a retry when violated */
    public static final String CONSTRAINT_STRING = "entity_version";

    private int maxRetries;

    /**
     * Creates a new retry wrapper with the default max retry value of 2.
     */
    public EntityVersioningRetryWrapper() {
        this.maxRetries = 2;
    }

    /**
     * Sets the number of times to retry execution of this wrapper if a failure occurs. If a
     * negative value is provided, this method throws an exception.
     *
     * @param attempts
     *  the maximum number of times to retry execution of this wrapper upon failure
     *
     * @throws IllegalArgumentException
     *  if attempts is negative
     *
     * @return
     *  this retry wrapper
     */
    public EntityVersioningRetryWrapper retries(int attempts) {
        if (attempts < 0) {
            throw new IllegalArgumentException("max attempts is less than zero");
        }

        this.maxRetries = attempts;
        return this;
    }

    /**
     * Executes this retry wrapper with the provided action. The action will always be executed at
     * least once, and will retry the action if a specific constraint violation exception occurs.
     * <p></p>
     * If the action fails with a versioning-related constraint violation, it will be retried
     * up to the maximum number of retries specified with the <tt>retries</tt> method. Any other
     * exceptions are immediately rethrown without any attempts to process them or retry the action.
     * <p></p>
     * If the action fails with a versioning-related constraint violation, but is at the retry
     * limit, this method throws the constraint violation exception.
     *
     * @param action
     *  the action to retry until successful completion
     *
     * @return
     *  the result of the provided action
     */
    public <O> O execute(Supplier<O> action) {
        // Retry this operation if we hit a constraint violation on the entity version constraint
        int retries = 0;

        while (true) {
            try {
                return action.get();
            }
            catch (Exception e) {
                if (retries++ < this.maxRetries && isEntityVersioningConstraintViolation(e)) {
                    continue;
                }

                throw e;
            }
        }
    }

    /**
     * Checks if the given exception is one originating from a constraint violation related to
     * entity versioning.
     *
     * @param exception
     *  the exception to check
     *
     * @return
     *  true if the exception originates from an entity versioning constraint violation; false
     *  otherwise
     */
    public static boolean isEntityVersioningConstraintViolation(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                ConstraintViolationException cve = (ConstraintViolationException) cause;
                String cname = cve.getConstraintName();

                if (cname != null && cname.contains(CONSTRAINT_STRING)) {
                    return true;
                }

                break;
            }
        }

        return false;
    }

}

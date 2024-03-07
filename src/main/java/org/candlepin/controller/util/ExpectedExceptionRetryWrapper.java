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
package org.candlepin.controller.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;



/**
 * Wrapper class providing retry logic to handle known exceptions occuring during a given operation.
 */
public class ExpectedExceptionRetryWrapper {
    private static final Logger log = LoggerFactory.getLogger(ExpectedExceptionRetryWrapper.class);

    private final Set<Class<? extends Exception>> exceptions;
    private int maxRetries;

    /**
     * Creates a new retry wrapper with the default max retry value of 2 and no expected exceptions
     */
    public ExpectedExceptionRetryWrapper() {
        this.exceptions = new HashSet<>();
        this.maxRetries = 2;
    }

    /**
     * Checks if the given exception is an expected exception, or is caused by an expected
     * exception.
     *
     * @param exception
     *  the exception to check
     *
     * @return
     *  true if the exception originates from an entity versioning constraint violation; false
     *  otherwise
     */
    private boolean isExpectedException(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            for (Class<? extends Exception> expected : this.exceptions) {
                if (expected.isInstance(cause)) {
                    return true;
                }
            }
        }

        return false;
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
    public ExpectedExceptionRetryWrapper retries(int attempts) {
        if (attempts < 0) {
            throw new IllegalArgumentException("max attempts is less than zero");
        }

        this.maxRetries = attempts;
        return this;
    }

    /**
     * Adds an expected exception to this wrapper. If the action executed within the context of this
     * wrapper throws an exception matching or caused by the given exception class, the action will
     * be retried.
     *
     * @param exception
     *  a class of exception to expect and retry
     *
     * @return
     *  this retry wrapper
     */
    public ExpectedExceptionRetryWrapper addException(Class<? extends Exception> exception) {
        if (exception == null) {
            throw new IllegalArgumentException("exception class is null");
        }

        this.exceptions.add(exception);
        return this;
    }

    /**
     * Executes this retry wrapper with the provided action. The action will always be executed at
     * least once, and will retry the action if a constraint violation exception occurs.
     * <p></p>
     * If the action fails with a constraint violation, but is at the retry limit, this method
     * throws the constraint violation exception.
     *
     * @param action
     *  the action to retry until successful completion
     *
     * @return
     *  the result of the provided action
     */
    public <O> O execute(Supplier<O> action) {
        int retries = 0;

        while (true) {
            try {
                return action.get();
            }
            catch (Exception e) {
                if (retries++ < this.maxRetries && this.isExpectedException(e)) {
                    log.warn("An expected exception occurred while attempting transactional operation; " +
                        "retrying...", e);
                    continue;
                }

                throw e;
            }
        }
    }

}

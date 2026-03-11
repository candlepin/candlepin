/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.util.function;



/**
 * The CheckedRunnable is a functional interface for a block or function that may throw a checked exception,
 * which is to be passed through to the caller.
 *
 * @param <E>
 *  the type of checked exception, or class of exceptions, this runnable may throw
 */
@FunctionalInterface
public interface CheckedRunnable<E extends Exception> {

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrowException(Exception exception) throws E {
        throw (E) exception;
    }

    /**
     * Wraps a checked runnable for use in unchecked contexts without losing the error handling of the
     * typed checked exception; effectively using type erasure to turn a given checked exception into a
     * runtime exception. Other exceptions, checked or otherwise, are passed through as normal.
     *
     * @param runnable
     *  the CheckedRunnable to wrap with exception rethrow logic; cannot be null
     *
     * @throws E
     *  if an exception of the given type occurs during execution of the target runnable
     *
     * @return
     *  a runnable wrapping the checked runnable with exception rethrowing logic
     */
    static <E extends Exception> Runnable rethrow(CheckedRunnable<E> runnable) throws E {
        return () -> {
            try {
                runnable.run();
            }
            catch (Exception exception) {
                rethrowException(exception);
            }
        };
    }

    /**
     * Executes the action represented by this runnable.
     */
    void run() throws E;
}

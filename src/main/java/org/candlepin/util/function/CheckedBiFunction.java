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
package org.candlepin.util.function;

import java.util.function.BiFunction;

/**
 * The CheckedBiFunction is a functional interface for a function that may throw a checked exception, which
 * is to be passed through to the caller.
 *
 * @param <T>
 *  the type of the first argument to the function
 *
 * @param <U>
 *  the type of the second argument to the function
 *
 * @param <R>
 *  the type of the output of the function
 *
 * @param <E>
 *  the type of checked exception, or class of exceptions, the function may throw
 */
@FunctionalInterface
public interface CheckedBiFunction<T, U, R, E extends Exception> {

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrowException(Exception exception) throws E {
        throw (E) exception;
    }

    /**
     * Wraps a checked bifunction for use in unchecked contexts without losing the error handling of the
     * typed checked exception; effectively using type erasure to turn a given checked exception into a
     * runtime exception. Other exceptions, checked or otherwise, are passed through as normal.
     *
     * @param function
     *  the CheckedBiFunction to wrap with exception rethrow logic; cannot be null
     *
     * @throws E
     *  if an exception of the given type occurs during execution of the target function
     *
     * @return
     *  a BiFunction wrapping the CheckedBiFunction with exception rethrowing logic
     */
    static <T, U, R, E extends Exception> BiFunction<T, U, R> rethrow(CheckedBiFunction<T, U, R, E> function)
        throws E {

        return (arg1, arg2) -> {
            try {
                return function.apply(arg1, arg2);
            }
            catch (Exception exception) {
                rethrowException(exception);
            }

            // This is safe -- we never actually get here
            return null;
        };
    }

    /**
     * Applies this function to the given arguments.
     *
     * @param arg1
     *  the first function argument
     *
     * @param arg2
     *  the second function argument
     *
     * @return
     *  the function result
     */
    R apply(T arg1, U arg2) throws E;
}

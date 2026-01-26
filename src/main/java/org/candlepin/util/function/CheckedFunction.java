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

import java.util.function.Function;



/**
 * The CheckedFunction is a functional interface for a function that may throw a checked exception, which
 * is to be passed through to the caller.
 *
 * @param <T>
 *  the type of the input to the function
 *
 * @param <R>
 *  the type of the output of the function
 *
 * @param <E>
 *  the type of checked exception, or class of exceptions, the function may throw
 */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Exception> {

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrowException(Exception exception) throws E {
        throw (E) exception;
    }

    /**
     * Wraps a checked function for use in unchecked contexts without losing the error handling of the
     * typed checked exception; effectively using type erasure to turn a given checked exception into a
     * runtime exception. Other exceptions, checked or otherwise, are passed through as normal.
     *
     * @param function
     *  the CheckedFunction to wrap with exception rethrow logic; cannot be null
     *
     * @throws E
     *  if an exception of the given type occurs during execution of the target function
     *
     * @return
     *  a function wrapping the checked function with exception rethrowing logic
     */
    static <T, R, E extends Exception> Function<T, R> rethrow(CheckedFunction<T, R, E> function) throws E {
        return input -> {
            try {
                return function.apply(input);
            }
            catch (Exception exception) {
                rethrowException(exception);
            }

            // This is safe -- we never actually get here
            return null;
        };
    }

    /**
     * Applies this function to the given argument.
     *
     * @param value
     *  the function argument
     *
     * @return
     *  the function result
     */
    R apply(T value) throws E;
}

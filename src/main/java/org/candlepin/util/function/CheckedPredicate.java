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

import java.util.function.Predicate;



/**
 * The CheckedPredicate is a functional interface for a predicate that may throw a checked exception, which
 * is to be passed through to the caller.
 *
 * @param <T>
 *  the type of the input to the predicate
 *
 * @param <E>
 *  the type of checked exception, or class of exceptions, this runnable may throw
 */
@FunctionalInterface
public interface CheckedPredicate<T, E extends Exception> {

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrowException(Exception exception) throws E {
        throw (E) exception;
    }

    /**
     * Wraps a checked predicate for use in unchecked contexts without losing the error handling of the
     * typed checked exception; effectively using type erasure to turn a given checked exception into a
     * runtime exception. Other exceptions, checked or otherwise, are passed through as normal.
     *
     * @param predicate
     *  the CheckedPredicate to wrap with exception rethrow logic. Cannot be null
     *
     * @throws E
     *  if an exception of the given type occurs during execution of the predicate
     *
     * @return
     *  a predicate wrapping the checked predicate with exception rethrowing logic
     */
    static <T, E extends Exception> Predicate<T> rethrow(CheckedPredicate<T, E> predicate) throws E {
        return (input) -> {
            try {
                return predicate.test(input);
            }
            catch (Exception exception) {
                rethrowException(exception);
            }

            return false;
        };
    }

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param value
     *  the input argument
     *
     * @return
     *  true if the input argument matches the predicate, false otherwise
     */
    boolean test(T value) throws E;
}

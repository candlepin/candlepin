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

import java.util.function.Supplier;



/**
 * The CheckedSupplier is a functional interface for a block or function that may throw a checked exception,
 * which is to be passed through to the caller.
 *
 * @param <T>
 *  the type of value returned by this supplier
 *
 * @param <E>
 *  the type of checked exception, or class of exceptions, this supplier may throw
 */
@FunctionalInterface
public interface CheckedSupplier<T, E extends Exception> {

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void rethrowException(Exception exception) throws E {
        throw (E) exception;
    }

    /**
     * Wraps a checked supplier for use in unchecked contexts without losing the error handling of the
     * typed checked exception; effectively using type erasure to turn a given checked exception into a
     * runtime exception. Other exceptions, checked or otherwise, are passed through as normal.
     *
     * @param supplier
     *  the CheckedSupplier to wrap with exception rethrow logic; cannot be null
     *
     * @throws E
     *  if an exception of the given type occurs during execution of the target supplier
     *
     * @return
     *  a supplier wrapping the checked supplier with exception rethrowing logic
     */
    static <T, E extends Exception> Supplier<T> rethrow(CheckedSupplier<T, E> supplier) throws E {
        return () -> {
            try {
                return supplier.get();
            }
            catch (Exception exception) {
                rethrowException(exception);
            }

            // This is safe -- we never actually get here
            return null;
        };
    }

    /**
     * Fetches a value or result from this supplier. The output of this method may change from invocation to
     * invocation.
     *
     * @return
     *  the value or result of this checked supplier
     */
    T get() throws E;
}

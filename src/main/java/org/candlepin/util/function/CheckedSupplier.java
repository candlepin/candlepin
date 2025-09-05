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

    /**
     * Fetches a value or result from this supplier. The output of this method may change from invocation to
     * invocation.
     *
     * @return
     *  the value or result of this checked supplier
     */
    T get() throws E;
}

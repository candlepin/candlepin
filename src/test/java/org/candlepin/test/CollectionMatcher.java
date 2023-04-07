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
package org.candlepin.test;

import org.mockito.ArgumentMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * An arbitrary collection matcher that allows verifying if two collections are equal strictly by
 * their unordered contents. This permits matching across ordered and restricted collections like
 * sets and lists.
 *
 * Two collections will be considered equal if they contain exactly the same elements, without
 * respect to order. The elements of the collections must properly implement equality checking, or
 * the result of this matcher will be undefined. Null collections are permitted, and will only match
 * another null collection, regardless of the declared type.
 */
public class CollectionMatcher<T> implements ArgumentMatcher<Collection<T>> {

    private final Collection<T> expected;

    public CollectionMatcher(Collection<T> expected) {
        this.expected = expected;
    }

    public boolean matches(Collection<T> input) {
        if (input == this.expected || input == null) {
            return input == expected;
        }

        if (this.expected == null || this.expected.size() != input.size()) {
            return false;
        }

        // Move the expected value to a mutable container so we can do a comparison via collection
        // subtraction without attempting to modify it in any way. This isn't the most efficient way
        // of doing it, but it's easy to maintain and good enough for testing.
        List<T> container = new ArrayList<>(this.expected);

        // Impl note: we can't use removeAll here, as that will remove duplicates for us, which we
        // absolutely do not want. We need to make sure there's a 1:1 removal of items. If any
        // removal fails, we can immediately return false.
        for (T element : input) {
            if (!container.remove(element)) {
                return false;
            }
        }

        return container.isEmpty();
    }

}

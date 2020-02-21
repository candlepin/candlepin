/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.visitors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collection;



@FunctionalInterface
public interface CollectionMergeValidator<T, E> extends MergeValidator<T> {
    @Override
    default void validate(T original, T updated, T merged) {
        if (this.getValue(merged) != null) {
            Collection<E> actual = this.getValue(merged);
            Collection<E> expected = null;

            if (updated != null && this.getValue(updated) != null) {
                expected = this.getValue(updated);
            }
            else {
                assertNotNull(original);
                expected = this.getValue(original);
            }

            assertNotNull(expected);
            assertEquals(expected.size(), actual.size());

            for (E item : expected) {
                assertThat(actual, hasItem(item));
            }
        }
        else {
            if (original != null) {
                assertNull(this.getValue(original));
            }

            if (updated != null) {
                assertNull(this.getValue(updated));
            }
        }
    }

    @Override
    Collection<E> getValue(T obj);
}

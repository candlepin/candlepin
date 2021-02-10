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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;



@FunctionalInterface
public interface MergeValidator<T> {
    default void validate(T original, T updated, T merged) {
        if (this.getValue(merged) != null) {
            if (updated != null && this.getValue(updated) != null) {
                assertEquals(this.getValue(updated), this.getValue(merged));
            }
            else {
                assertNotNull(original);
                assertEquals(this.getValue(original), this.getValue(merged));
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

    Object getValue(T obj);
}

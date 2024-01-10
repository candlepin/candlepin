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
package org.candlepin.cache;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

/*
 * A key composed of multiple elements. Duplicate elements are removed to form a distinct set including null
 * values. Comparisons of the MultiKey does not consider ordering of elements.
 */
public class MultiKey {

    private HashSet<String> elements;

    public MultiKey(Collection<String> elements) {
        Objects.requireNonNull(elements);

        this.elements = new HashSet<>(elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MultiKey multiKey = (MultiKey) o;
        return this.elements.equals(multiKey.elements);
    }

    @Override
    public String toString() {
        return "MultiKey [elements=" + elements + "]";
    }

}


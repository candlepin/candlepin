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
package org.candlepin.paging;

import java.util.Comparator;



/**
 * A functional interface for defining a mapping function that converts a field name to a comparator
 * for the purposes of sorting collections or streams for paged output.
 *
 * @param <T>
 *  the class type for which this factory's mapping logic applies
 */
@FunctionalInterface
public interface FieldComparatorFactory<T> {

    /**
     * Fetches a comparator which orders elements of this comparator factory's type by using the
     * value returned by accessor best matching the given field name. The comparator this method
     * returns should always order elements in ascending order. If the type supported by this
     * comparator factory has no mapping for the given field name, this method should return null.
     *
     * @param fieldName
     *  the name of the field for which to build a comparator
     *
     * @return
     *  a comparator for this factory's type which compares instances using the appropriate field
     *  for the given field name; or null if the field name does not have a mapping for the type
     */
    Comparator<T> getComparator(String fieldName);

    /**
     * Gets the default comparator for this type. The default comparator should only be used in
     * cases where paging is requested, but no sort-by field has been provided.
     * <p>
     * The default implementation of this method calls into <tt>getComparator</tt> using the value
     * defined at <tt>PageRequest.DEFAULT_SORT_FIELD</tt> as the field name. Implementors are
     * encouraged to provide a more accurate default comparator.
     *
     * @return
     *  a comparator to use for sorting objects of this factory's type when no explicit sort-by
     *  field has been provided; or null if no default sorting is supported
     */
    default Comparator<T> getDefaultComparator() {
        // This is actually a very bad default, but it's here for backwards compatibility purposes.
        // Existing solutions use this field whenever the sort-by field is absent, so to be drop-in
        // compliant, we do the same.
        return this.getComparator(PageRequest.DEFAULT_SORT_FIELD);
    }

}

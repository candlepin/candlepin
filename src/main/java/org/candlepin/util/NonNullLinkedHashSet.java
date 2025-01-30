/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.util;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * An extension of {@link LinkedHashSet} where null values cannot be added.
 *
 * @param <E>
 *  the object type populating the set
 */
public class NonNullLinkedHashSet<E> extends LinkedHashSet<E> {

    /**
     * {@inheritDoc}
     *
     * @param object
     *  non-null element to be added to the set
     *
     * @throws IllegalArgumentException
     *  if the provided element is null
     */
    @Override
    public boolean add(E object) {
        if (object == null) {
            throw new IllegalArgumentException("object cannot be null");
        }

        return super.add(object);
    }

    /**
     * {@inheritDoc}
     *
     * @param collection
     *  collection to add to the set
     *
     * @throws IllegalArgumentException
     *  if the provided collection contains null
     */
    @Override
    public boolean addAll(Collection<? extends E> collection) {
        if (collection == null) {
            return false;
        }

        if (collection.contains(null)) {
            throw new IllegalArgumentException("collection cannot contain null");
        }

        return super.addAll(collection);
    }

}


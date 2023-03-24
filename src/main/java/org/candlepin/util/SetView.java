/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import java.util.Set;



/**
 * The SetView is a pass-through collection which restricts adding elements to the backing set,
 * allowing only reading and removing elements.
 *
 * Unlike the CollectionView, the SetView class can only be used to provides views of sets.
 * However, this allows it to safely pass calls to .equals and .hashCode to its backing set.
 *
 * @param <E>
 *  The element type
 */
public class SetView<E> extends CollectionView<E> implements Set<E> {

    /**
     * Creates a new SetView instance backed by the provided set.
     *
     * @param set
     *  The set to use as the backing set
     *
     * @throws IllegalArgumentException
     *  if the provided set is null
     */
    public SetView(Set<E> set) {
        super(set);
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        return this.collection.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return this.collection.hashCode();
    }

}

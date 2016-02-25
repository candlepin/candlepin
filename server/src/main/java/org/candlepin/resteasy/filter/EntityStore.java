/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.resteasy.filter;

import org.candlepin.model.Owner;
import org.candlepin.model.Persisted;

import java.util.Collection;
import java.util.List;

/**
 * Classes implementing EntityStore are used to look up the Owner for a particular
 * entity or to look up the entity for a given String.
 *
 * @param <E> a type that implements the Persisted interface.
 */
interface EntityStore<E extends Persisted> {
    E lookup(String key);
    E lookup(String key, Owner owner);
    List<E> lookup(Collection<String> keys);
}

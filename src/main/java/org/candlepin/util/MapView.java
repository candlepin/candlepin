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
package org.candlepin.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;



/**
 * The MapView is a pass-through map which restricts adding elements to the backing map, allowing
 * only reading and removing elements.
 *
 * @param <K>
 *  The type of keys to be used by this map
 *
 * @param <V>
 *  The type of values to be stored by this map
 */
public class MapView<K, V> implements Map<K, V> {

    protected final Map<K, V> map;

    /**
     * Creates a new MapView instance backed by the provided map.
     *
     * @param map
     *  The map to use as the backing map
     *
     * @throws IllegalArgumentException
     *  if the provided map is null
     */
    public MapView(Map<K, V> map) {
        if (map == null) {
            throw new IllegalArgumentException("map is null");
        }

        this.map = map;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        this.map.clear();
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
        return this.map.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public Set<Map.Entry<K, V>> entrySet() {
        // Impl note:
        // We're safe here since the set returned by entrySet is defined as follows:
        // "The set supports element removal, which removes the corresponding mapping from the map,
        // via the Iterator.remove, Set.remove, removeAll, retainAll and clear operations. It does
        // not support the add or addAll operations."
        return this.map.entrySet();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        return this.map.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    public V get(Object key) {
        return this.map.get(key);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return this.map.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public Set<K> keySet() {
        // Impl note:
        // We're safe here since the set returned by keySet is defined as follows:
        // "The set supports element removal, which removes the corresponding mapping from the map,
        // via the Iterator.remove, Set.remove, removeAll, retainAll and clear operations. It does
        // not support the add or addAll operations."
        return this.map.keySet();
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param key
     * @param value
     *
     * @throws UnsupportedOperationException
     * @return n/a
     */
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param map
     *
     * @throws UnsupportedOperationException
     */
    public void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public V remove(Object key) {
        return this.map.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return this.map.size();
    }

    /**
     * {@inheritDoc}
     */
    public Collection<V> values() {
        // Impl note:
        // We're safe here since the collection returned by values is defined as follows:
        // "The collection supports element removal, which removes the corresponding mapping from
        // the map, via the Iterator.remove, Collection.remove, removeAll, retainAll and clear
        // operations. It does not support the add or addAll operations."
        return this.map.values();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append(" {");
        Iterator<Map.Entry<K, V>> iterator = this.map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();

            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());

            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append('}');

        return builder.toString();
    }

}

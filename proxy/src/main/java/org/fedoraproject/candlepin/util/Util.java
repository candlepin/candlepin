/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Genuinely random utilities.
 */
public class Util {

    private Util() {
        // default ctor
    }

    /**
     * Generates a random UUID.
     * 
     * @return a random UUID.
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static <T> List<T> subList(List<T> parentList, int start, int end) {
        List<T> l = new ArrayList<T>();
        for (int i = start; i < end; i++) {
            l.add(parentList.get(i));
        }
        return l;
    }

    public static <T> List<T> subList(List<T> parentList, int size) {
        return subList(parentList, 0, size - 1);
    }
    public static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    public static <K, V> Map<K, V> newMap() {
        return new HashMap<K, V>();
    }
    
    public static <T> Set<T> newSet() {
        return new HashSet<T>();
    }

}

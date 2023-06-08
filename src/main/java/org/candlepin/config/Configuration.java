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
package org.candlepin.config;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Inspired by Apache's Commons Configuration library.
 * <p>
 * This class should only be used to hold <b>immutable objects</b>.  If you place
 * a mutable object in the configuration and using the subset() method, both configurations
 * will reference the same object and any changes to that object will be reflected
 * in both!
 */
public interface Configuration {

    Map<String, String> getValuesByPrefix(String prefix);
    Properties toProperties();
    Iterable<String> getKeys();

    /**
     * Return a property of type String <b>with all whitespace trimmed!</b>
     * @param key the key of the retrieved property
     * @return a String property with all whitespace trimmed by String.trim()
     */
    String getString(String key);
    boolean getBoolean(String key);
    int getInt(String key);
    long getLong(String key);
    List<String> getList(String key);
    Set<String> getSet(String key);
}

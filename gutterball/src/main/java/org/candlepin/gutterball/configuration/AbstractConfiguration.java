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
package org.candlepin.gutterball.configuration;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * AbstractConfiguration with basic methods to get typed values.
 *
 */
public abstract class AbstractConfiguration implements Configuration {
    protected String doesNotMapMessage(String key) {
        return String.format("\"%s\" doesn't map to an existing object", key);
    }

    @Override
    public Boolean getBoolean(String key) {
        Boolean b = getBoolean(key, null);
        if (b != null) {
            return b;
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toBoolean(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public Integer getInteger(String key) {
        Integer i = getInteger(key, null);
        if (i != null) {
            return i;
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toInteger(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key) {
        Long l = getLong(key, null);
        if (l != null) {
            return l;
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toLong(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public String getString(String key) {
        String s = getString(key, null);
        if (s != null) {
            return s;
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        return getString(key, defaultValue, TrimMode.TRIM);

    }

    @Override
    public String getString(String key, String defaultValue, TrimMode trimMode) {
        if (containsKey(key)) {
            String val = getProperty(key).toString();
            return (trimMode.equals(TrimMode.TRIM)) ? val.trim() : val;
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public List<String> getList(String key) {
        List<String> list = getList(key, null);
        if (list != null) {
            return list;
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public List<String> getList(String key, List<String> defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toList(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }
}

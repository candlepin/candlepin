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
package org.candlepin.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * AbstractConfiguration with basic methods to get typed values.
 *
 */
public abstract class AbstractConfiguration implements Configuration {
    protected static final String ERROR_MESSAGE = "\"%s\" doesn't map to an existing object";
    protected static final String MISSING_MESSAGE = "\"%s\" not present in configuration";
    private static Logger log = LoggerFactory.getLogger(AbstractConfiguration.class);

    protected String doesNotMapMessage(String key) {
        return String.format(ERROR_MESSAGE, key);
    }

    protected String missingMessage(String key) {
        return String.format(MISSING_MESSAGE, key);
    }

    @Override
    public boolean getBoolean(String key) {
        if (containsKey(key)) {
            return PropertyConverter.toBoolean(getProperty(key));
        }
        else {
            log.warn(missingMessage(key));
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toBoolean(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public int getInt(String key) {
        if (containsKey(key)) {
            return PropertyConverter.toInteger(getProperty(key));
        }
        else {
            log.warn(missingMessage(key));
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public int getInt(String key, int defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toInteger(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key) {
        if (containsKey(key)) {
            return PropertyConverter.toLong(getProperty(key));
        }
        else {
            log.warn(missingMessage(key));
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
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
            log.warn(missingMessage(key));
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
        if (list == null) {
            log.warn(missingMessage(key));
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
        return list;
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

    @Override
    public Set<String> getSet(String key) {
        Set<String> set = getSet(key, null);
        if (set == null) {
            log.warn(missingMessage(key));
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
        return set;
    }

    @Override
    public Set<String> getSet(String key, Set<String> defaultValue) {
        if (containsKey(key)) {
            return PropertyConverter.toSet(getProperty(key));
        }
        else {
            return defaultValue;
        }
    }
}

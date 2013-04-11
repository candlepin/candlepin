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
package org.candlepin.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Defines the default Candlepin configuration
 */
public class Config {
    private static final String CONFIG_FILE_NAME = "/etc/candlepin/candlepin.conf";
    protected File configFile;
    protected TreeMap<String, String> configuration = null;

    /**
     * Creates a new <code>Config</code> instance using the default config file:
     * <code>/etc/candlepin/candlepin.conf</code>
     */
    public Config() {
        this(CONFIG_FILE_NAME);
    }

    /**
     * Creates a new <code>Config</code> instance using the file name to
     * override values defined in {@link ConfigProperties}.
     *
     * @param configFileName the file that contains the configuration values
     */
    public Config(String configFileName) {
        this.configFile = new File(configFileName);

        // start with the default values
        this.configuration = new TreeMap<String, String>(
            ConfigProperties.DEFAULT_PROPERTIES);

        // override with user-specified values
        this.configuration.putAll(loadProperties());
        this.configuration = this.trimSpaces(configuration);
    }

    public Config(Map<String, String> properties) {
        this.configuration = new TreeMap<String, String>(
            ConfigProperties.DEFAULT_PROPERTIES);
        this.configuration.putAll(properties);
        this.configuration = this.trimSpaces(configuration);
    }

    /**
     * Remove all leading and trailing spaces from values in the tree.
     */
    protected TreeMap<String, String> trimSpaces(TreeMap<String, String> configs) {
        Iterator<Entry<String, String>> itor = configs.entrySet().iterator();
        while (itor.hasNext()) {
            Entry<String, String> entry = itor.next();
            configs.put(entry.getKey(), entry.getValue().trim());
        }
        return configs;
    }

    /**
     * Return configuration entry for the given prefix.
     *
     * @param prefix prefix for the entry sought.
     * @return configuration entry for the given prefix.
     */
    public Map<String, String> configurationWithPrefix(String prefix) {
        return configuration.subMap(prefix, prefix + Character.MAX_VALUE);
    }

    /**
     * Returns all of the entries with the given prefix.
     *
     * @param prefix part of the configuration key being sought.
     * @return all of the entries with the given prefix.
     */
    public Properties getNamespaceProperties(String prefix) {
        return this.getNamespaceProperties(prefix, null);
    }

    /**
     * Returns all of the entries with the given prefix, preloading the values
     * contained in the given defaults. The default list is also filtered by
     * prefix as well. For example, if you pass in a prefix of "a.c" and the
     * defaults map contains a key that beings with "b.d" it WILL not be added
     * to the returned Properties.
     *
     * @param prefix part of the configuration key being sought.
     * @param defaults default values you'd like to see defined.
     * @return all of the entries with the given prefix.
     */
    public Properties getNamespaceProperties(String prefix,
        Map<String, String> defaults) {

        Properties p = new Properties();

        if (defaults != null) {
            for (Entry<String, String> entry : defaults.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                    p.put(entry.getKey(), entry.getValue());
                }
            }
        }
        p.putAll(configurationWithPrefix(prefix));
        return p;
    }

    /**
     * Returns the JPA Configuration properties.
     *
     * @return the JPA Configuration properties.
     */
    public Properties jpaConfiguration(Config config) {
        return new JPAConfigParser(config).parseConfig(configuration);
    }

    /**
     * Returns the Database Basic Authentication Configuration properties
     *
     * @return the Database Basic Authentication Configuration properties
     */
    public Properties dbBasicAuthConfiguration(Config config) {
        return new DbBasicAuthConfigParser(config).parseConfig(configuration);
    }

    /**
     * to disable SSLAuthFilter add to candlepin.conf:
     * candlepin.auth.ssl.enabled=no
     *
     * @return if ssl authentication should be enabled
     */
    public boolean sslAuthEnabled() {
        return getBoolean(ConfigProperties.SSL_AUTHENTICATION);
    }

    public boolean indentJson() {
        return getBoolean(ConfigProperties.PRETTY_PRINT);
    }

    public boolean trustedAuthEnabled() {
        return getBoolean(ConfigProperties.TRUSTED_AUTHENTICATION);
    }

    public boolean oAuthEnabled() {
        return getBoolean(ConfigProperties.OAUTH_AUTHENTICATION);
    }

    public boolean basicAuthEnabled() {
        return getBoolean(ConfigProperties.BASIC_AUTHENTICATION);
    }

    public boolean failOnUnknownImportProperties() {
        return getBoolean(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES);
    }

    /**
     * Use to make decisions on behavior. Hosted vs. Standalone Default is
     * Standalone For hosted use candlepin.conf: candlepin.standalone = false
     *
     * @return if system is standalone.
     */
    public boolean standalone() {
        return getBoolean(ConfigProperties.STANDALONE);
    }

    /**
     * Returns true if candlpin should support per environment content filtering
     * Default is Enabled. For hosted use candlepin.conf:
     * candlepin.environment_content_filtering.
     * = false
     *
     * @return if system should filter content by environment.
     */
    public boolean environmentFilteringEnabled() {
        return getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);
    }


    protected Map<String, String> loadProperties() {
        try {
            return new ConfigurationFileLoader()
                .loadProperties(this.configFile);
        }
        catch (IOException e) {
            throw new RuntimeException(
                "Problem loading candlepin configuration file.", e);
        }
    }

    /**
     * Returns true if the configuration has an entry for the given key.
     *
     * @param key sought after key
     * @return true if the configuration has an entry for the given key.
     */
    public boolean containsKey(String key) {
        return configuration.containsKey(key);
    }

    /**
     * @param s configuration key
     * @return value associated with the given configuration key.
     */
    public String getString(String s) {
        return configuration.get(s);
    }

    /**
     * Get the configuration entry for the given string name. If the value is
     * null, then return the given defValue. defValue can be null as well.
     *
     * @param name name of property
     * @param defValue default value for property if it is null.
     * @return the value of the property with the given name, or defValue.
     */
    public String getString(String name, String defValue) {
        String ret = getString(name);
        if (ret == null) {
            ret = defValue;
        }
        return ret;
    }

    /**
     * get the config entry for string s
     *
     * @param s string to get the value of
     * @return the value as an array
     */
    public String[] getStringArray(String s) {
        if (s == null) {
            return null;
        }
        String value = getString(s);

        if (value == null) {
            return null;
        }

        return value.split(",");
    }

    /**
     * get the config entry for string s
     *
     * @param s string to get the value of
     * @return the value as an List
     */
    public List<String> getStringList(String s) {
        if (s == null) {
            return null;
        }
        String value = getString(s);

        if (value == null) {
            return null;
        }
        List<String> list = new ArrayList<String>();
        for (String entry : value.split(",")) {
            list.add(entry.trim());
        }
        return list;
    }

    public boolean getBoolean(String s) {
        if (s == null) {
            return false;
        }
        String value = getString(s);
        if (value == null) {
            throw new IllegalArgumentException("value of " + s + " is null");
        }

        value = value.toLowerCase();

        return value.equals("true") || value.equals("on") ||
            value.equals("1") || value.equals("yes");
    }

    public int getInt(String s) {
        return new Integer(getString(s));
    }

    public int getInt(String s, int defValue) {
        String ret = getString(s);
        if (ret == null) {
            return defValue;
        }

        return Integer.valueOf(ret);
    }
}

package org.candlepin.gutterball.config;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class JPAConfigurationParser extends ConfigurationParser {

    //FIXME: This class should be removed and the one currently in CP should
    //       be used when the configuration refactor is done.
    //       THERE IS CURRENTLY NO ENCRYPTION SUPPORT.

    /** JPA configuration prefix */
    public static final String JPA_CONFIG_PREFIX = "jpa.config";
    private Configuration config;

    public JPAConfigurationParser(Configuration config) {
        this.config = config;
    }

    public String getPrefix() {
        return JPA_CONFIG_PREFIX;
    }

    public Properties parseConfig() {
        return this.parseConfig(configToMap());
    }

    public Map<String, String> configToMap() {
        HashMap<String, String> all = new HashMap<String, String>();
        for (String key : config.getKeys()) {
            all.put(key, config.getString(key));
        }

        return all;
    }
}

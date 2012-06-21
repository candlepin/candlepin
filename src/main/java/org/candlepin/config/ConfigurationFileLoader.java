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
package org.candlepin.config;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.Logger;
/**
 * ConfigurationFileLoader
 */
public class ConfigurationFileLoader {

    private static Logger log = Logger.getLogger(ConfigurationFileLoader.class);

    /**
     * @param configurationFile config file to read
     * @return returns the configuration as a Map
     * @throws IOException thrown if there is a problem reading the file.
     */
    public Map<String, String> loadProperties(File configurationFile)
        throws IOException {

        if (configurationFile.canRead()) {
            BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(configurationFile));
            try {
                return loadConfiguration(bis);
            }
            finally {
                bis.close();
            }
        }
        return new HashMap<String, String>();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, String> loadConfiguration(InputStream input) throws IOException {
        Properties loaded = new Properties();
        loaded.load(input);
        HashMap result = new HashMap(loaded);
        // If we're reading a config file, and it doesn't specify a setting for
        // auto populating the db, set it to blank to prevent the "default"
        // persistence unit value from unintentionally clearing the database.
        if (!result.containsKey("jpa.config.hibernate.hbm2ddl.auto")) {
            result.put("jpa.config.hibernate.hbm2ddl.auto", "");
        }
        return result;
    }
}

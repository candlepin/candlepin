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
package org.candlepin.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * VersionUtil
 */
public class VersionUtil {
    private static Logger log = Logger.getLogger(VersionUtil.class);

    public static final String VERSION_HEADER = "X-Candlepin-Version";

    private VersionUtil() {
        // Quiet checkstyle
    }

    public static Map<String, String> getVersionMap() {
        Map<String, String> map = new HashMap<String, String>();

        map.put("version", "Unknown");
        map.put("release", "Unknown");

        InputStream in = VersionUtil.class.getClassLoader()
            .getResourceAsStream("candlepin_info.properties");

        Properties props = new Properties();

        try {
            props.load(in);
            if (props.containsKey("version")) {
                map.put("version", props.getProperty("version"));
            }
            if (props.containsKey("release")) {
                map.put("release", props.getProperty("release"));
            }
        }
        catch (IOException ioe) {
            log.error("Can not load candlepin_info.properties", ioe);
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ioe) {
                log.error("problem closing candlepin_info reader");
            }
        }

        return map;
    }
}

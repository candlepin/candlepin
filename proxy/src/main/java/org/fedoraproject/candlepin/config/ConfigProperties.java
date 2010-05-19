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

package org.fedoraproject.candlepin.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a map of default properties used to prepopulate the {@link Config}.
 * Also holds static keys for config lookup.
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String CA_KEY = "candlepin.ca_key";
    public static final String CA_CERT = "candlepin.ca_cert";
    public static final String CA_KEY_PASSWORD = "candlepin.ca_key_password";
    
    public static final String PRETTY_PRINT = "candlepin.pretty_print";

    public static final Map<String, String> DEFAULT_PROPERTIES = 
        new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;
            {
                this.put(CA_KEY, "/etc/candlepin/certs/candlepin-ca.key");
                this.put(CA_CERT, "/etc/candlepin/certs/candlepin-ca.crt");
                
                this.put(PRETTY_PRINT, "false");
            }
        };
}

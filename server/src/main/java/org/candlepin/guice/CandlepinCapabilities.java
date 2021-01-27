/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.guice;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;



/**
 * A class representing the capabilities of the Candlepin server.
 */
public class CandlepinCapabilities extends HashSet<String> {

    private static final int DEFAULT_CAPACITY = 20;

    private static final String[] DEFAULT_CAPABILITIES = { "cores", "ram", "instance_multiplier",
        "derived_product", "cert_v3", "guest_limit", "vcpu", "hypervisors_async", "storage_band",
        "remove_by_pool_id", "batch_bind", "org_level_content_access", "syspurpose", "hypervisors_heartbeat"
    };

    public static final String KEYCLOAK_AUTH_CAPABILITY = "keycloak_auth";

    public static final String CLOUD_REGISTRATION_CAPABILITY = "cloud_registration";

    private static CandlepinCapabilities capabilities;


    /**
     * Creates a new CandlepinCapabilities instance with the default capabilities.
     */
    public CandlepinCapabilities() {
        this(DEFAULT_CAPABILITIES);
    }

    /**
     * Creates a new CandlepinCapabilities instance with the specified capabilities. If the given
     * capabilities are null, no capabilities will be listed as available.
     *
     * @param capabilities
     *  An array of strings representing available Candlepin capabilities
     */
    public CandlepinCapabilities(String... capabilities) {
        super(DEFAULT_CAPACITY);

        if (capabilities != null) {
            this.addAll(Arrays.asList(capabilities));
        }
    }

    /**
     * Creates a new CandlepinCapabilities instance with the specified capabilities. If the given
     * capabilities are null, no capabilities will be listed as available.
     *
     * @param capabilities
     *  An collection of strings representing available Candlepin capabilities
     */
    public CandlepinCapabilities(Collection<String> capabilities) {
        super(DEFAULT_CAPACITY);

        if (capabilities != null) {
            this.addAll(capabilities);
        }
    }

    /**
     * Fetches the capabilities currently supported by the current Candlepin instance. The instance
     * returned is mutable, and changes made to it will be reflected by later calls to this method.
     *
     * @return
     *  A CandlepinCapabilities instance
     */
    public static CandlepinCapabilities getCapabilities() {
        if (capabilities == null) {
            capabilities = new CandlepinCapabilities();
        }

        return capabilities;
    }

    /**
     * Sets the capabilities of the current Candlepin instance. Later calls to getCapabilities
     * will return the provided instance. If the provided capabilities instance is null, the
     * capabilities will be reset to their defaults.
     *
     * @param caps
     *  A CandlepinCapabilities instance to set, or null to reset the capabilities
     */
    public static void setCapabilities(CandlepinCapabilities caps) {
        capabilities = caps;
    }

}

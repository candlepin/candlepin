/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

package org.candlepin.controller.util;

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content prefix implementation for entitlement certificates.
 */
public class EntitlementContentPrefix implements ContentPrefix {

    /**
     * A factory method constructs an instance and prepares prefixes for the given environments.
     *
     * @param owner an owner to be used in construction of content prefixes
     * @param environments environments for which to prepare content prefixes
     * @return content prefix instance
     */
    public static ContentPrefix from(Owner owner, List<Environment> environments) {
        EntitlementContentPrefix prefixes = new EntitlementContentPrefix(owner);
        for (Environment environment : environments) {
            prefixes.add(environment);
        }
        return prefixes;
    }

    private final Map<String, String> prefixes = new HashMap<>();
    private final String ownerPrefix;

    /**
     * A constructor
     *
     * @param owner an owner to be used in construction of content prefixes
     */
    private EntitlementContentPrefix(Owner owner) {
        this.ownerPrefix = owner.getContentPrefix();
    }

    /**
     * Returns a content prefix for the requested environment
     *
     * If the owner prefix contains an environment placeholder "$env" and an
     * environment is available the placeholder is expanded. Otherwise the owner
     * prefix is used as-is.
     *
     * E.G. With owner prefix /some/org/$env/ and an environment with name Env21
     * the prefix becomes /some/org/Env21/
     *
     * In case the owner prefix is missing an empty prefix "" is always returned.
     *
     * @param environmentId an id of environment for which to return a content prefix
     * @return content prefix
     */
    @Override
    public String get(String environmentId) {
        if (ownerPrefixIsMissing()) {
            return "";
        }
        return this.prefixes.getOrDefault(environmentId, ownerPrefix());
    }

    private void add(Environment env) {
        if (ownerPrefixIsMissing() || env == null || env.getId() == null) {
            return;
        }

        String contentPrefix = this.ownerPrefix.replaceAll("\\$env", env.getName());
        this.prefixes.put(env.getId(), this.cleanUpPrefix(contentPrefix));
    }

    private boolean ownerPrefixIsMissing() {
        return this.ownerPrefix == null || this.ownerPrefix.isBlank();
    }

    private String ownerPrefix() {
        return this.cleanUpPrefix(this.ownerPrefix);
    }

    // Encode the entire prefix in case any part of it is not
    // URL friendly. Any $ is put back in order to preserve
    // the ability to pass $env to the client
    private String cleanUpPrefix(String contentPrefix) {
        if (contentPrefix == null) {
            return "";
        }
        StringBuilder output = new StringBuilder("/");
        for (String part : contentPrefix.split("/")) {
            if (!part.equals("")) {
                output.append(Util.encodeUrl(part));
                output.append("/");
            }
        }
        return output.toString().replace("%24", "$");
    }

}

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
 * Content prefix implementation for simple content access certificates.
 */
public class ScaContentPrefix implements ContentPrefix {

    /**
     * A factory method constructs an instance and prepares prefixes for the given environments.
     *
     * @param owner an owner to be used in construction of content prefixes
     * @param standalone flag denoting whether we are in hosted or standalone deployment
     * @param environments environments for which to prepare content prefixes
     * @return content prefix instance
     */
    public static ContentPrefix from(Owner owner, boolean standalone, List<Environment> environments) {
        ScaContentPrefix prefixes = new ScaContentPrefix(owner, standalone);
        for (Environment environment : environments) {
            prefixes.add(environment);
        }
        return prefixes;
    }

    private final Map<String, String> prefixes = new HashMap<>();
    private final String ownerKey;
    private final boolean standalone;

    /**
     * A constructor
     *
     * @param owner an owner to be used in construction of content prefixes
     * @param standalone flag denoting whether we are in hosted or standalone deployment
     */
    private ScaContentPrefix(Owner owner, boolean standalone) {
        this.ownerKey = owner.getKey();
        this.standalone = standalone;
    }

    /**
     * Returns a content prefix for the requested environment.
     *
     * In hosted, SCA entitlement content paths should not have any prefix. The
     * returned prefix is always empty "".
     *
     * In satellite (standalone), the prefix is created by appending the owner key and
     * environment name if available. E.G. /some_org_key/an_env_name
     *
     * The environment name is omitted from prefix in case an invalid environment id was provided.
     *
     * @param environmentId an id of environment for which to return a content prefix
     * @return content prefix
     */
    @Override
    public String get(String environmentId) {
        return this.prefixes.getOrDefault(environmentId, ownerPrefix());
    }

    // Fix for BZ 1866525:
    // - In hosted, SCA entitlement content paths should not have any prefix
    // - In satellite (standalone), the prefix should use the owner key and environment name
    //   if available
    private void add(Environment environment) {
        if (!this.standalone || environment == null || environment.getId() == null) {
            return;
        }

        StringBuilder prefix = new StringBuilder();
        prefix.append(this.ownerPrefix());

        for (String chunk : environment.getName().split("/")) {
            if (!chunk.isEmpty()) {
                prefix.append("/");
                prefix.append(Util.encodeUrl(chunk));
            }
        }

        this.prefixes.put(environment.getId(), prefix.toString());
    }

    private String ownerPrefix() {
        if (this.standalone) {
            return "/" + Util.encodeUrl(this.ownerKey);
        }
        return "";
    }

}

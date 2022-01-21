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

import java.util.Collections;
import java.util.List;

/**
 * Content prefix implementation for simple content access certificate containers.
 */
public class ScaContainerContentPrefix implements ContentPrefix {

    /**
     * A factory method constructs an instance and prepares prefixes for the given environments.
     *
     * @param owner an owner to be used in construction of content prefixes
     * @param standalone flag denoting whether we are in hosted or standalone deployment
     * @param environments environments for which to prepare content prefixes
     * @return content prefix instance
     */
    public static ContentPrefix from(Owner owner, boolean standalone, List<Environment> environments) {
        if (standalone) {
            return new ScaContainerContentPrefix(owner, standalone, environments);
        }
        else {
            return new ScaContainerContentPrefix(owner, standalone, Collections.emptyList());
        }
    }

    private final ContentPrefix prefixes;
    private final String ownerKey;
    private final boolean standalone;

    private ScaContainerContentPrefix(Owner owner, boolean standalone, List<Environment> environments) {
        this.prefixes = ScaContentPrefix.from(owner, standalone, environments);
        this.ownerKey = owner.getKey();
        this.standalone = standalone;
    }

    /**
     * Returns a content prefix for the requested environment.
     *
     * In satellite (standalone), the prefix is created by appending the owner key and
     * environment name if available. E.G. /some_org_key/an_env_name
     *
     * In hosted, SCA entitlement content paths should use just the owner key
     * prefixed with 'sca'. E.G. /sca/some_org
     *
     * @param environmentId an id of environment for which to return a content prefix
     * @return content prefix
     */
    @Override
    public String get(String environmentId) {
        // Fix for BZ 1866525:
        // - In hosted, SCA entitlement content path needs to use a dummy value to prevent existing
        //   clients from breaking, while still being clear the path is present for SCA
        // - In satellite (standalone), the path should simply be the content prefix

        if (this.standalone) {
            return this.prefixes.get(environmentId);
        }
        return "/sca/" + Util.encodeUrl(ownerKey);
    }

}

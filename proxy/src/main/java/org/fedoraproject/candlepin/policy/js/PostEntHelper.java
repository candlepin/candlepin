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
package org.fedoraproject.candlepin.policy.js;

import org.fedoraproject.candlepin.model.Entitlement;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 * 
 * NOTE: Currently unused, keeping it around and wired up in case we do once rule
 * execution becomes better understood.
 */
public class PostEntHelper {

    /**
     * Separated from constructor because these objects are not something Guice
     * can inject. Must be called before the post helper is passed in to the
     * Javascript engine.
     *
     * @param ent Entitlement just created.
     */
    public void init(Entitlement ent) {
        //this.ent = ent;
    }

}

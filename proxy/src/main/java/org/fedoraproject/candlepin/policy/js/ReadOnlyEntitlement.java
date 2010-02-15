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
 * Represents a read-only entitlement.
 */
public class ReadOnlyEntitlement {
    private Entitlement ent;
  
    /**
     * ctor
     * @param e read-write Entitlement to be copied.
     */
    public ReadOnlyEntitlement(Entitlement e) {
        this.ent = e;
    }
   
    /**
     * Returns true if the entitlement is free and accessible.
     * @return true if the entitlement is free and accessible.
     */
    public Boolean getIsFree() {
        return ent.getIsFree();
    }
}

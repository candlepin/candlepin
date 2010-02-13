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
package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

import java.util.HashSet;
import java.util.Set;

public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    public EntitlementCurator() {
        super(Entitlement.class);
    }
    
    // TODO: handles addition of new entitlements only atm!
    @Transactional
    public Set<Entitlement> bulkUpdate(Set<Entitlement> entitlements) {
        Set<Entitlement> toReturn = new HashSet<Entitlement>();
        for(Entitlement toUpdate: entitlements) {
            Entitlement found = find(toUpdate.getId()); 
            if(found != null) {
                toReturn.add(found);
                continue;
            }
            toReturn.add(create(toUpdate));
        }
        return toReturn;
    }
}

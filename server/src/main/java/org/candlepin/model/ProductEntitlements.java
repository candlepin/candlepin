/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This data class is a container for Entitlements mapped by their provided
 * Products. The reason to have this class is to be able to easily answer query:
 * Given a product, which entitlements provide it?
 * @author fnguyen
 *
 */
public class ProductEntitlements {
    private Map<String, Set<Entitlement>> entsByProductIds = new HashMap<String, Set<Entitlement>>();

    public ProductEntitlements(Collection<Entitlement> entitlements) {
        for (Entitlement ent : entitlements) {
            addProductIdToMap(ent.getProductId(), ent);
            for (ProvidedProduct pp : ent.getPool().getProvidedProducts()) {
                addProductIdToMap(pp.getProductId(), ent);
            }
        }
    }

    private void addProductIdToMap(String pid, Entitlement e) {
        if (!entsByProductIds.containsKey(pid)) {
            entsByProductIds.put(pid, new HashSet<Entitlement>());
        }
        entsByProductIds.get(pid).add(e);
    }

    public boolean isEmpty() {
        return entsByProductIds.isEmpty();
    }

    public Collection<String> getAllProductIds() {
        return entsByProductIds.keySet();
    }

    public Collection<? extends Entitlement> getEntitlementsByProductId(String id) {
        return entsByProductIds.get(id);
    }
}

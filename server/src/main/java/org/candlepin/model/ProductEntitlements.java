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
import java.util.Map.Entry;
import java.util.Set;

/**
 * This data class is a container for Entitlements mapped by their provided
 * Products. The reason to have this class is to be able to easily answer query:
 * Given a product, which entitlements provide it?
 * @author fnguyen
 *
 */
public class ProductEntitlements {
    private Map<String, Set<Entitlement>> entsByProductIds = new HashMap<>();

    public ProductEntitlements(Collection<Entitlement> entitlements, ProductCurator productCurator) {
        for (Entitlement ent : entitlements) {
            addProductIdToMap(ent.getPool().getProductId(), ent);
            for (Product pp : ent.getPool().getProduct().getProvidedProducts()) {
                addProductIdToMap(pp.getId(), ent);
            }
        }
    }

    private void addProductIdToMap(String pid, Entitlement e) {
        if (!entsByProductIds.containsKey(pid)) {
            entsByProductIds.put(pid, new HashSet<>());
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Entitlement ids by products map: [");
        for (Entry<String, Set<Entitlement>> entry : entsByProductIds.entrySet()) {
            sb.append("    " + entry.getKey() + " -> " + entSetToString(entry.getValue()) + "\n");
        }

        sb.append("]");
        return sb.toString();
    }

    private String entSetToString(Set<Entitlement> value) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Entitlement ent : value) {
            if (!first) {
                sb.append(", ");
            }
            else {
                first = false;
            }

            sb.append(ent.getId());
        }
        sb.append("}");
        return sb.toString();
    }
}

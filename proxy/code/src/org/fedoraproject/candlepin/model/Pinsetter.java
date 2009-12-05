/**
 * Copyright (c) 2008 Red Hat, Inc.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Pinsetter
 * @version $Rev$
 */
public class Pinsetter {
    public static Pinsetter instance = new Pinsetter();
    private static List<EntitlementPool> pools = new ArrayList<EntitlementPool>();
    
    private Pinsetter() {
        // do nothing
    }
    
    public static Pinsetter get() {
        return instance;
    }
    
    /**
     * Add a new product to entitle.
     * @param owner Owner of the product/entitlement.
     * @param pname Product name
     * @param maxmem Maximum members available.
     * @param start start date of the product entitlement
     * @param end end date of the product entitlement.
     */
    public void addProduct(Owner owner, String pname, long maxmem,
            Date start, Date end) {
        System.out.println("Adding product " + pname);
        
        Product p = new Product(pname, pname);
        
        EntitlementPool ep = new EntitlementPool();
        ep.setOwner(owner);
        ep.setProduct(p);
        ep.setMaxMembers(maxmem);
        ep.setStartDate(start);
        ep.setEndDate(end);
        
        pools.add(ep);
        ObjectFactory.get().store(ep);
    }
}

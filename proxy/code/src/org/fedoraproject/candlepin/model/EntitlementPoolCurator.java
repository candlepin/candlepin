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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.hibernate.criterion.Restrictions;

import com.wideplay.warp.persist.Transactional;

public class EntitlementPoolCurator extends AbstractHibernateCurator<EntitlementPool> {

    protected EntitlementPoolCurator() {
        super(EntitlementPool.class);
    }

    public List<EntitlementPool> listByOwner(Owner o) {
        List<EntitlementPool> results = (List<EntitlementPool>) currentSession()
            .createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("owner", o)).list();
        if (results == null) {
            return new LinkedList<EntitlementPool>();
        }
        else {
            return results;
        }
    }
    
    public EntitlementPool lookupByOwnerAndProduct(Owner owner, Product product) {
        return (EntitlementPool) currentSession().createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("product", product))
            .uniqueResult();
    }
    
    /**
     * Create an entitlement.
     * 
     * @param entPool
     * @param consumer
     * @return
     */
    @Transactional
    public Entitlement createEntitlement(EntitlementPool entPool, Consumer consumer) {
        Entitlement e = new Entitlement(entPool, consumer.getOwner(), new Date());
        entPool.bumpCurrentMembers();
        consumer.addEntitlement(e);
        consumer.addConsumedProduct(entPool.getProduct());
        e.setOwner(consumer.getOwner());
        
        save(e);
        flush();
        
        return e;
    }
    
    @Transactional
    public EntitlementPool create(EntitlementPool entity) {
        
        // Make sure there isn't already a pool for this product. Ideally we'd catch
        // this with a database constraint but I don't see how to do that just yet.
        EntitlementPool existing = lookupByOwnerAndProduct(entity.getOwner(), 
                entity.getProduct());
        if (existing != null) {
            throw new RuntimeException("Already an entitlement pool for owner " +
                    entity.getOwner().getName() + " and product " + 
                    entity.getProduct().getLabel());
        }
        
        return super.create(entity);
    }


}

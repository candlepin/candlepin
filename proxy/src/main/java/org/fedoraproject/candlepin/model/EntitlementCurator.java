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

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import com.wideplay.warp.persist.Transactional;

/**
 * EntitlementCurator
 */
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static Logger log = Logger.getLogger(EntitlementCurator.class);
    
    /**
     * default ctor
     */
    public EntitlementCurator() {
        super(Entitlement.class);
    }
    
    // TODO: handles addition of new entitlements only atm!
    /**
     * @param entitlements entitlements to update
     * @return updated entitlements.
     */
    @Transactional
    public Set<Entitlement> bulkUpdate(Set<Entitlement> entitlements) {
        Set<Entitlement> toReturn = new HashSet<Entitlement>();
        for (Entitlement toUpdate : entitlements) {
            Entitlement found = find(toUpdate.getId()); 
            if (found != null) {
                toReturn.add(found);
                continue;
            }
            toReturn.add(create(toUpdate));
        }
        return toReturn;
    }

    public List<Entitlement> listByConsumer(Consumer consumer) {
        DetachedCriteria query = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer));
        return listByCriteria(query);
    }

    public List<Entitlement> listByOwner(Owner owner) {
        DetachedCriteria query = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("owner", owner));
        
        return listByCriteria(query);
    }

    @Transactional
    public List<Entitlement> listByConsumerAndProduct(Consumer consumer, String productId) {
        DetachedCriteria query = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer));
        
        List<Entitlement> results = listByCriteria(query);
        
        // TODO: Possible to do this via hibernate query? No luck on first attempt
        // with criteria query.
        List<Entitlement> filtered = new LinkedList<Entitlement>();
        for (Entitlement e : results) {
            if (e.getProductId().equals(productId)) {
                filtered.add(e);
            }
        }
        return filtered;
    }
    
    @Transactional
    public void delete(Entitlement entity) {
        Entitlement toDelete = find(entity.getId());
        log.debug("Deleting entitlement: " + toDelete);
        log.debug("certs.size = " + toDelete.getCertificates().size());
        
        for (EntitlementCertificate cert : toDelete.getCertificates()) {
            currentSession().delete(cert);
        }
        toDelete.getCertificates().clear();
        
        currentSession().delete(toDelete);
        flush();
    }
    
    @Transactional
    public Entitlement findByCertificateSerial(BigInteger serial) {
        return (Entitlement) currentSession().createCriteria(Entitlement.class)
            .createCriteria("certificates")
                .add(Restrictions.eq("serial", serial))
            .uniqueResult();
    }
}

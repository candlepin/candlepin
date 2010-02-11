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

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.hibernate.criterion.Restrictions;

import java.util.HashSet;
import java.util.Set;

public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    
    @Inject private ConsumerFactCurator consumerInfoCurator;
    @Inject private EntitlementCurator entitlementCurator;    

    protected ConsumerCurator() {
        super(Consumer.class);
    }
    
    public void addConsumedProduct(Consumer consumer, Product product) {
        ConsumerProduct cp = new ConsumerProduct() ;
        cp.setConsumer(consumer) ;
        cp.setProductOID(product.getOID()) ;
        consumer.addConsumedProduct(cp) ;
    }

    public Consumer lookupByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.like("name", name))
            .uniqueResult();
    }

    public Consumer lookupByUuid(String uuid) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.like("uuid", uuid))
            .uniqueResult();
    }
    
    @Transactional
    public Consumer update(Consumer updatedConsumer) {
        Consumer existingConsumer = find(updatedConsumer.getId());
        if (existingConsumer == null) {
            return create(updatedConsumer);
        }
        
        // TODO: Are any of these read-only?
        existingConsumer.setChildConsumers(bulkUpdate(updatedConsumer.getChildConsumers()));
        existingConsumer.setConsumedProducts(updatedConsumer.getConsumedProducts());
        existingConsumer.setEntitlements(entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements())); 
        existingConsumer.setFacts(consumerInfoCurator.update(updatedConsumer.getFacts()));
        existingConsumer.setName(updatedConsumer.getName());
        existingConsumer.setOwner(updatedConsumer.getOwner());
        existingConsumer.setParent(updatedConsumer.getParent());
        existingConsumer.setType(updatedConsumer.getType());
        existingConsumer.setUuid(updatedConsumer.getUuid());        
        save(existingConsumer);
        
        return existingConsumer;
    }
    
    @Transactional
    public Set<Consumer> bulkUpdate(Set<Consumer> consumers) {
        Set<Consumer> toReturn = new HashSet<Consumer>();        
        for(Consumer toUpdate: consumers) { 
            toReturn.add(update(toUpdate));
        }
        return toReturn;
    }
    
}

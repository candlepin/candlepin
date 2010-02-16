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

/**
 * ConsumerCurator
 */
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    
    @Inject private ConsumerFactCurator consumerInfoCurator;
    @Inject private EntitlementCurator entitlementCurator;    

    protected ConsumerCurator() {
        super(Consumer.class);
    }
    
    /**
     * Add consumed product by associating the product to the given consumer.
     * @param consumer consumer to update.
     * @param product Product to associate.
     */
    public void addConsumedProduct(Consumer consumer, Product product) {
        ConsumerProduct cp = new ConsumerProduct();
        cp.setConsumer(consumer);
        cp.setProductId(product.getId());
        consumer.addConsumedProduct(cp);
    }

    /**
     * Lookup consumer by its name
     * @param name consumer name to find
     * @return Consumer whose name matches the given name, null otherwise.
     */
    public Consumer lookupByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.like("name", name))
            .uniqueResult();
    }

    /**
     * Lookup the Consumer by its uuid.
     * @param uuid Consumer uuid sought.
     * @return Consumer whose uuid matches the given value, or null otherwise.
     */
    public Consumer lookupByUuid(String uuid) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.like("uuid", uuid))
            .uniqueResult();
    }
    
    /**
     * @param updatedConsumer updated Consumer values.
     * @return Updated consumers
     */
    @Transactional
    public Consumer update(Consumer updatedConsumer) {
        Consumer existingConsumer = find(updatedConsumer.getId());
        if (existingConsumer == null) {
            return create(updatedConsumer);
        }
        
        // TODO: Are any of these read-only?
        existingConsumer.setChildConsumers(bulkUpdate(updatedConsumer.getChildConsumers()));
        existingConsumer.setConsumedProducts(updatedConsumer.getConsumedProducts());
        existingConsumer.setEntitlements(
                entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements())); 
        existingConsumer.setFacts(consumerInfoCurator.update(updatedConsumer.getFacts()));
        existingConsumer.setName(updatedConsumer.getName());
        existingConsumer.setOwner(updatedConsumer.getOwner());
        existingConsumer.setParent(updatedConsumer.getParent());
        existingConsumer.setType(updatedConsumer.getType());
        existingConsumer.setUuid(updatedConsumer.getUuid());        
        save(existingConsumer);
        
        return existingConsumer;
    }
    
    /**
     * @param consumers consumers to update
     * @return updated consumers
     */
    @Transactional
    public Set<Consumer> bulkUpdate(Set<Consumer> consumers) {
        Set<Consumer> toReturn = new HashSet<Consumer>();        
        for (Consumer toUpdate : consumers) { 
            toReturn.add(update(toUpdate));
        }
        return toReturn;
    }
    
}

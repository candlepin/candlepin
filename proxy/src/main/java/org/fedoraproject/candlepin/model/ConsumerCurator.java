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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * ConsumerCurator
 */
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    
    @Inject private EntitlementCurator entitlementCurator;    
    //private static Logger log = Logger.getLogger(ConsumerCurator.class);
    
    protected ConsumerCurator() {
        super(Consumer.class);
    }

    @AllowRoles(roles = {Role.SUPER_ADMIN, Role.OWNER_ADMIN})
    @Transactional
    public Consumer create(Consumer entity) {
        return super.create(entity);
    }
    
    /**
     * Lookup consumer by its name
     * @param name consumer name to find
     * @return Consumer whose name matches the given name, null otherwise.
     */
    @Transactional
    public Consumer lookupByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

    /**
     * Lookup the Consumer by its uuid.
     * @param uuid Consumer uuid sought.
     * @return Consumer whose uuid matches the given value, or null otherwise.
     */
    @Transactional
    public Consumer lookupByUuid(String uuid) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("uuid", uuid))
            .uniqueResult();
    }
    
    @Transactional
    @EnforceAccessControl
    public List<Consumer> listByOwner(Owner owner) {
        return (List<Consumer>) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("owner", owner)).list();
    }

    
    
    /**
     * @param updatedConsumer updated Consumer values.
     * @return Updated consumers
     */
    @Transactional
    @EnforceAccessControl
    public Consumer update(Consumer updatedConsumer) {
        Consumer existingConsumer = find(updatedConsumer.getId());
        if (existingConsumer == null) {
            return create(updatedConsumer);
        }
        
        // TODO: Are any of these read-only?
        existingConsumer.setChildConsumers(bulkUpdate(updatedConsumer.getChildConsumers()));
        existingConsumer.setEntitlements(
                entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements())); 
        existingConsumer.setFacts(updatedConsumer.getFacts());
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

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * ConsumerCurator
 */
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private Config config;
    //private static Logger log = Logger.getLogger(ConsumerCurator.class);
    
    protected ConsumerCurator() {
        super(Consumer.class);
    }

    @AllowRoles(roles = {Role.SUPER_ADMIN, Role.OWNER_ADMIN})
    @Transactional
    @EnforceAccessControl
    public Consumer create(Consumer entity) {
        entity.ensureUUID();
        entity.setFacts(filterFacts(entity.getFacts()));
        return super.create(entity);
    }
    
    /**
     * Lookup consumer by its name
     * @param name consumer name to find
     * @return Consumer whose name matches the given name, null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param user User
     * @return Consumer for this user if one exists, null otherwise.
     */
    @AllowRoles(roles = {Role.SUPER_ADMIN, Role.OWNER_ADMIN})
    @Transactional
    @EnforceAccessControl
    public Consumer findByUser(User user) {
        ConsumerType person = consumerTypeCurator.lookupByLabel(
            ConsumerType.ConsumerTypeEnum.PERSON.getLabel());
        return (Consumer) currentSession().createCriteria(Consumer.class)
        .add(Restrictions.eq("username", user.getUsername()))
        .add(Restrictions.eq("type", person))
        .uniqueResult();
    }

    /**
     * Lookup the Consumer by its uuid.
     * @param uuid Consumer uuid sought.
     * @return Consumer whose uuid matches the given value, or null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByUuid(String uuid) {
        return getConsumer(uuid);
    }

    // NOTE:  This is a giant hack that is for use *only* by SSLAuth in order
    //        to bypass the authentication.  Do not call it!
    // TODO:  Come up with a better way to do this!
    public Consumer getConsumer(String uuid) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("uuid", uuid))
            .uniqueResult();
    }

    @AllowRoles(roles = {Role.SUPER_ADMIN, Role.OWNER_ADMIN})
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Consumer> listByOwner(Owner owner) {
        return (List<Consumer>) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("owner", owner)).list();
    }
    
    /**
     * Search for Consumers with fields matching those provided.
     * 
     * @param userName the username to match, or null to ignore 
     * @param type the type to match, or null to ignore
     * @return a list of matching Consumers
     */
    @AllowRoles(roles = {Role.SUPER_ADMIN, Role.OWNER_ADMIN})
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Consumer> listByUsernameAndType(String userName,
        ConsumerType type) {
        Criteria criteria = currentSession().createCriteria(Consumer.class);

        if (userName != null) {
            criteria.add(Restrictions.eq("username", userName));
        }
        if (type != null) {
            criteria.add(Restrictions.eq("type", type));
        }

        return (List<Consumer>) criteria.list();
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
        existingConsumer.setFacts(filterFacts(updatedConsumer.getFacts()));
        existingConsumer.setName(updatedConsumer.getName());
        existingConsumer.setOwner(updatedConsumer.getOwner());
        existingConsumer.setParent(updatedConsumer.getParent());
        existingConsumer.setType(updatedConsumer.getType());
        existingConsumer.setUuid(updatedConsumer.getUuid());

        save(existingConsumer);
        
        return existingConsumer;
    }
    
    /**
     * @param facts
     * @return the list of facts filtered by the fact filter regex config
     */
    private Map<String, String> filterFacts(Map<String, String> factsIn) {
        Map<String, String> facts = new HashMap<String, String>();
        for (String key : factsIn.keySet()) {
            if (key.matches(config.getString(ConfigProperties.CONSUMER_FACTS_MATCHER))) {
                facts.put(key, factsIn.get(key));
            }
        }
        return facts;
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

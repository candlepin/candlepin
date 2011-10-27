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
package org.candlepin.model;

import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * ConsumerCurator
 */
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {

    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private Config config;
    @Inject private I18n i18n;
    private static final int NAME_LENGTH = 250;
    //private static Logger log = Logger.getLogger(ConsumerCurator.class);

    protected ConsumerCurator() {
        super(Consumer.class);
    }

    @Transactional
    @EnforceAccessControl
    @Override
    public Consumer create(Consumer entity) {
        entity.ensureUUID();
        if (entity.getFacts() != null) {
            entity.setFacts(filterFacts(entity.getFacts()));
        }
        validate(entity);
        return super.create(entity);
    }

    protected void validate(Consumer entity) {
        // #TODO Look at generic validation framework
        if ((entity.getName() != null) &&
            (entity.getName().length() >= NAME_LENGTH)) {
            throw new BadRequestException(i18n.tr(
                "Name of the consumer should be shorter than {0} characters.",
                NAME_LENGTH));
        }
    }

    @Transactional
    public Consumer replicate(Consumer consumer) {
        for (Entitlement entitlement : consumer.getEntitlements()) {
            entitlement.setConsumer(consumer);
        }

        ConsumerType consumerType = consumerTypeCurator.lookupByLabel(consumer
            .getType().getLabel());
        consumer.setType(consumerType);

        IdentityCertificate idCert = consumer.getIdCert();
        this.currentSession().replicate(idCert.getSerial(),
            ReplicationMode.EXCEPTION);
        this.currentSession().replicate(idCert, ReplicationMode.EXCEPTION);

        this.currentSession().replicate(consumer, ReplicationMode.EXCEPTION);

        return consumer;
    }

    /**
     * Lookup consumer by its name
     *
     * @param name consumer name to find
     * @return Consumer whose name matches the given name, null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByName(Owner o, String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("name", name))
            .add(Restrictions.eq("owner", o))
            .uniqueResult();
    }

    /**
     * Lookup consumer by its virt.uuid
     *
     * @param uuid consumer virt.uuid to find
     * @return Consumer whose name matches the given virt.uuid, null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByVirtUuid(String uuid) {
        Consumer result = null;
        List<Consumer> options = currentSession()
            .createCriteria(Consumer.class)
            .addOrder(Order.desc("updated"))
            .add(Restrictions.sqlRestriction("{alias}.id in (select cp_consumer_id " +
                "from cp_consumer_facts where mapkey = 'virt.uuid' and element = ?)",
                uuid, Hibernate.STRING)).list();
        if (options != null && options.size() != 0) {
            result = options.get(0);
        }
        return result;
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param user User
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByUser(User user) {
        ConsumerType person = consumerTypeCurator
            .lookupByLabel(ConsumerType.ConsumerTypeEnum.PERSON.getLabel());
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("username", user.getUsername()))
            .add(Restrictions.eq("type", person)).uniqueResult();
    }

    /**
     * Lookup the Consumer by its uuid.
     *
     * @param uuid Consumer uuid sought.
     * @return Consumer whose uuid matches the given value, or null otherwise.
     */
    @Transactional
    @EnforceAccessControl
    public Consumer findByUuid(String uuid) {
        return getConsumer(uuid);
    }

    // NOTE: This is a giant hack that is for use *only* by SSLAuth in order
    // to bypass the authentication. Do not call it!
    // TODO: Come up with a better way to do this!
    public Consumer getConsumer(String uuid) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("uuid", uuid)).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Consumer> listByOwner(Owner owner) {
        return currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("owner", owner)).list();
    }

    /**
     * Search for Consumers with fields matching those provided.
     *
     * @param userName the username to match, or null to ignore
     * @param type the type to match, or null to ignore
     * @param owner Optional owner to filter on, pass null to skip.
     * @return a list of matching Consumers
     */
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Consumer> listByUsernameAndType(String userName,
        ConsumerType type, Owner owner) {

        Criteria criteria = currentSession().createCriteria(Consumer.class);

        if (userName != null) {
            criteria.add(Restrictions.eq("username", userName));
        }
        if (type != null) {
            criteria.add(Restrictions.eq("type", type));
        }
        if (owner != null) {
            criteria.add(Restrictions.eq("owner", owner));
        }

        return criteria.list();
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

        validate(updatedConsumer);
        // TODO: Are any of these read-only?
        existingConsumer.setEntitlements(entitlementCurator
            .bulkUpdate(updatedConsumer.getEntitlements()));
        Map<String, String> newFacts = filterFacts(updatedConsumer.getFacts());
        if (factsChanged(newFacts, existingConsumer.getFacts())) {
            existingConsumer.setFacts(newFacts);
        }
        existingConsumer.setName(updatedConsumer.getName());
        existingConsumer.setOwner(updatedConsumer.getOwner());
        existingConsumer.setType(updatedConsumer.getType());
        existingConsumer.setUuid(updatedConsumer.getUuid());

        save(existingConsumer);

        return existingConsumer;
    }

    private boolean factsChanged(Map<String, String> updatedFacts,
        Map<String, String> existingFacts) {
        return !existingFacts.equals(updatedFacts);
    }

    /**
     * @param facts
     * @return the list of facts filtered by the fact filter regex config
     */
    private Map<String, String> filterFacts(Map<String, String> factsIn) {
        Map<String, String> facts = new HashMap<String, String>();
        for (Entry<String, String> entry : factsIn.entrySet()) {
            if (entry.getKey().matches(
                config.getString(ConfigProperties.CONSUMER_FACTS_MATCHER))) {

                facts.put(entry.getKey(), entry.getValue());
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

    /**
     * Get host consumer for a guest system id.
     *
     * As multiple hosts could have reported the same guest ID, we find the newest
     * and assume this is the authoritative host for the guest.
     *
     * @param guestId a virtual guest ID (not a consumer UUID)
     * @return host consumer who most recently reported the given guestId (if any)
     */
    @Transactional
    @EnforceAccessControl
    public Consumer getHost(String guestId) {

        // TODO: could the query do the work for us here? sort on updated time, limit to 1.
        // Avoids any potential (albeit unlikely) hibernate issues where we mistakenly
        // load a bunch of data.
        List<GuestId> consumers = currentSession()
            .createCriteria(GuestId.class)
            .add(Restrictions.eq("guestId", guestId))
            .list();
        Consumer newest = null;
        if (consumers != null) {
            for (GuestId cg : consumers) {
                if (newest == null ||
                    cg.getUpdated().getTime() > newest.getUpdated().getTime()) {
                    newest = cg.getConsumer();
                }
            }
        }
        return newest;
    }

    /**
     * Get guest consumers for a host consumer.
     *
     * @param consumer host consumer to find the guests for
     * @return list of registered guest consumers for this host
     */
    @Transactional
    @EnforceAccessControl
    public List<Consumer> getGuests(Consumer consumer) {
        if (consumer.getFact("virt.uuid") != null &&
            !consumer.getFact("virt.uuid").trim().equals("")) {
            throw new BadRequestException(i18n.tr(
                "The consumer with UUID {0} is a virtual guest. " +
                "It does not have guests.",
                consumer.getUuid()));
        }
        List<Consumer> guests = new ArrayList<Consumer>();
        List<GuestId> consumerGuests = consumer.getGuestIds();
        if (consumerGuests != null) {
            for (GuestId cg : consumerGuests) {
                // Check if this is the most recent host to report the guest by asking
                // for the consumer's current host and comparing it to ourselves.
                if (consumer.equals(getHost(cg.getGuestId()))) {
                    Consumer guest = findByVirtUuid(cg.getGuestId());
                    if (guest != null) {
                        guests.add(guest);
                    }
                }
            }
        }
        return guests;
    }

}

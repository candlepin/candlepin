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

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EntitlementCurator
 */
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static Logger log = LoggerFactory.getLogger(EntitlementCurator.class);
    private ProductServiceAdapter productAdapter;

    /**
     * default ctor
     */
    @Inject
    public EntitlementCurator(ProductServiceAdapter productAdapter) {
        super(Entitlement.class);
        this.productAdapter = productAdapter;
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

    public Page<List<Entitlement>> listByConsumer(Consumer consumer,
        EntitlementFilterBuilder filterBuilder, PageRequest pageRequest) {
        Criteria query = createSecureCriteria().createAlias("pool", "p");
        query.add(Restrictions.eq("consumer", consumer));
        // Never show a consumer expired entitlements
        query.add(Restrictions.ge("p.endDate", new Date()));
        filterBuilder.applyTo(query);
        return listByCriteria(query, pageRequest);
    }

    /**
     * This must return a sorted list in order to avoid deadlocks
     *
     * @param consumer
     * @return list of entitlements belonging to the consumer, ordered by pool id
     */
    @SuppressWarnings("unchecked")
    public List<Entitlement> listByConsumer(Consumer consumer) {
        return listByConsumer(consumer, new EntitlementFilterBuilder());
    }

    @SuppressWarnings("unchecked")
    public List<Entitlement> listByConsumer(Consumer consumer, EntitlementFilterBuilder filters) {
        Criteria c = createSecureCriteria();
        c.createAlias("pool", "p");
        c.add(Restrictions.eq("consumer", consumer));
        // Never show a consumer expired entitlements
        c.add(Restrictions.ge("p.endDate", new Date()));
        filters.applyTo(c);
        c.addOrder(Order.asc("p.id"));
        return c.list();
    }

    public List<Entitlement> listByEnvironment(Environment environment) {
        Criteria criteria = currentSession().createCriteria(Entitlement.class)
            .createCriteria("consumer").add(Restrictions.eq("environment", environment));
        return criteria.list();
    }

    /**
     * List entitlements for a consumer which are valid for a specific date.
     *
     * @param consumer Consumer to list entitlements for.
     * @param activeOn The date we want to see entitlements which are active on.
     * @return List of entitlements.
     */
    public List<Entitlement> listByConsumerAndDate(Consumer consumer, Date activeOn) {

        /*
         * Essentially the opposite of the above query which searches for entitlement
         * overlap with a "modifying" entitlement being granted. This query is used to
         * search for modifying entitlements which overlap with a regular entitlement
         * being granted. As such the logic is basically reversed.
         *
         */
        Criteria criteria = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createCriteria("pool")
                .add(Restrictions.le("startDate", activeOn))
                .add(Restrictions.ge("endDate", activeOn));
        List<Entitlement> entitlements = criteria.list();
        return entitlements;
    }

    public List<Entitlement> listByOwner(Owner owner) {
        Criteria query = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("owner", owner));

        return listByCriteria(query);
    }

    /**
     * List all entitled product IDs from entitlements which overlap the given date range.
     *
     * i.e. given start date must be within the entitlements start/end dates, or
     * the given end date must be within the entitlements start/end dates,
     * or the given start date must be before the entitlement *and* the given end date
     * must be after entitlement. (i.e. we are looking for *any* overlap)
     *
     * @param c
     * @param startDate
     * @param endDate
     * @return entitled product IDs
     */
    public Set<String> listEntitledProductIds(Consumer c, Date startDate, Date endDate) {
        // FIXME Either address the TODO below, or move this method out of the curator.
        // TODO: Swap this to a db query if we're worried about memory:
        Set<String> entitledProductIds = new HashSet<String>();
        for (Entitlement e : c.getEntitlements()) {
            Pool p = e.getPool();
            if (!poolOverlapsRange(p, startDate, endDate)) {
                // Skip this entitlement:
                continue;
            }

            entitledProductIds.add(e.getPool().getProductId());
            for (ProvidedProduct pp : e.getPool().getProvidedProducts()) {
                entitledProductIds.add(pp.getProductId());
            }

            // A distributor should technically be entitled to derived products and
            // will need to be able to sync content downstream.
            if (c.getType().isManifest() && p.getDerivedProductId() != null) {
                entitledProductIds.add(p.getDerivedProductId());
                if (p.getDerivedProvidedProducts() != null) {
                    for (DerivedProvidedProduct dpp : p.getDerivedProvidedProducts()) {
                        entitledProductIds.add(dpp.getProductId());
                    }
                }
            }
        }
        return entitledProductIds;
    }

    private boolean poolOverlapsRange(Pool p, Date startDate, Date endDate) {
        Date poolStart = p.getStartDate();
        Date poolEnd = p.getEndDate();
        // If pool start is within the range we're looking for:
        if (poolStart.compareTo(startDate) >= 0 && poolStart.compareTo(endDate) <= 0) {
            return true;
        }
        // If pool end is within the range we're looking for:
        if (poolEnd.compareTo(startDate) >= 0 && poolEnd.compareTo(endDate) <= 0) {
            return true;
        }
        // If pool completely encapsulates the range we're looking for:
        if (poolStart.compareTo(startDate) <= 0 && poolEnd.compareTo(endDate) >= 0) {
            return true;
        }
        return false;
    }

    /*
     * Creates date filtering criteria to for checking if an entitlement has any overlap
     * with a "modifying" entitlement that has just been granted.
     */
    private Criteria createModifiesDateFilteringCriteria(Consumer consumer, Date startDate,
        Date endDate, Entitlement excludeEnt) {
        Criteria criteria = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer));

        if (excludeEnt != null) {
            criteria = criteria.add(Restrictions.ne("id", excludeEnt.getId()));
        }

        criteria = criteria.createCriteria("pool")
                .add(Restrictions.or(
                    // Dates overlap if the start or end date is in our range
                    Restrictions.or(
                        Restrictions.between("startDate", startDate, endDate),
                        Restrictions.between("endDate", startDate, endDate)),
                    Restrictions.and(
                        // The dates overlap if our range is completely encapsulated
                        Restrictions.le("startDate", startDate),
                        Restrictions.ge("endDate", endDate))));
        return criteria;
    }

    public Set<Entitlement> listModifying(Entitlement entitlement) {
        Set<Entitlement> modifying = new HashSet<Entitlement>();

        // Get the map of product Ids to the set of
        // overlapping entitlements that provide them
        Map<String, Set<Entitlement>> pidEnts = getOverlappingForModifying(entitlement);
        if (pidEnts.isEmpty()) {
            // Empty collections break hibernate queries
            return modifying;
        }
        // Retrieve all products at once from the adapter
        List<Product> products = productAdapter.getProductsByIds(pidEnts.keySet());
        for (Product p : products) {
            boolean modifies = p.modifies(entitlement.getProductId());
            Iterator<ProvidedProduct> ppit =
                entitlement.getPool().getProvidedProducts().iterator();
            // No need to continue checking once we have found a modified product
            while (!modifies && ppit.hasNext()) {
                modifies = modifies || p.modifies(ppit.next().getProductId());
            }
            if (modifies) {
                // Return all entitlements for the modified product
                modifying.addAll(pidEnts.get(p.getId()));
            }
        }

        return modifying;
    }

    /*
     * Add a productId to entitlement mapping, creating the collection if necessary
     */
    private void addProductIdToMap(Map<String, Set<Entitlement>> map,
            String pid, Entitlement e) {
        if (!map.containsKey(pid)) {
            map.put(pid, new HashSet<Entitlement>());
        }
        map.get(pid).add(e);
    }

    /*
     * Add an entitlement to the productId Entitlement map, using the entitlements
     * productId as well as those if its provided products.
     */
    private void addToMap(Map<String, Set<Entitlement>> map, Entitlement e) {
        addProductIdToMap(map, e.getProductId(), e);
        for (ProvidedProduct pp : e.getPool().getProvidedProducts()) {
            addProductIdToMap(map, pp.getProductId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Set<Entitlement>> getOverlappingForModifying(Entitlement e) {
        List<Entitlement> overlapEnts = createModifiesDateFilteringCriteria(
            e.getConsumer(), e.getStartDate(), e.getEndDate(), e).list();
        Map<String, Set<Entitlement>> pidEnts = new HashMap<String, Set<Entitlement>>();
        for (Entitlement ent : overlapEnts) {
            addToMap(pidEnts, ent);
        }
        return pidEnts;
    }

    @Transactional
    public Page<List<Entitlement>> listByConsumerAndProduct(Consumer consumer,
        String productId, PageRequest pageRequest) {
        Criteria query = createSecureCriteria()
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "p")
            .createAlias("p.providedProducts", "pp",
                CriteriaSpecification.LEFT_JOIN)
            // Never show a consumer expired entitlements
            .add(Restrictions.ge("p.endDate", new Date()))
            .add(Restrictions.or(Restrictions.eq("p.productId", productId),
                Restrictions.eq("pp.productId", productId)));

        Page<List<Entitlement>> page = listByCriteria(query, pageRequest);

        return page;
    }

    @Transactional
    public void delete(Entitlement entity) {
        Entitlement toDelete = find(entity.getId());
        log.debug("Deleting entitlement: " + toDelete);
        log.debug("certs.size = " + toDelete.getCertificates().size());

        for (EntitlementCertificate cert : toDelete.getCertificates()) {
            currentSession().delete(cert);
        }
        currentSession().delete(toDelete);
    }

    @Transactional
    public Entitlement findByCertificateSerial(Long serial) {
        return (Entitlement) currentSession().createCriteria(Entitlement.class)
            .createCriteria("certificates")
                .add(Restrictions.eq("serial.id", serial))
            .uniqueResult();
    }

    @Transactional
    public Entitlement replicate(Entitlement ent) {
        for (EntitlementCertificate ec : ent.getCertificates()) {
            ec.setEntitlement(ent);
            CertificateSerial cs = ec.getSerial();
            if (cs != null) {
                this.currentSession().replicate(cs, ReplicationMode.EXCEPTION);
            }
        }
        this.currentSession().replicate(ent, ReplicationMode.EXCEPTION);

        return ent;
    }

    /**
     * Find the entitlements for the given consumer that are part of the specified stack.
     *
     * @param consumer the consumer
     * @param stackId the ID of the stack
     * @return the list of entitlements for the consumer that are in the stack.
     */
    @SuppressWarnings("unchecked")
    public List<Entitlement> findByStackId(Consumer consumer, String stackId) {
        Criteria activeNowQuery = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.productAttributes", "attrs")
            .add(Restrictions.eq("attrs.name", "stacking_id"))
            .add(Restrictions.eq("attrs.value", stackId))
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceStack", "ss",
                JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.isNull("ss.id"));
        return activeNowQuery.list();
    }

    @SuppressWarnings("unchecked")
    public List<Entitlement> findByPoolAttribute(Consumer consumer, String attributeName, String value) {
        Criteria criteria = currentSession().createCriteria(Entitlement.class)
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.attributes", "attrs")
            .add(Restrictions.eq("attrs.name", attributeName))
            .add(Restrictions.eq("attrs.value", value));

        if (consumer != null) {
            criteria.add(Restrictions.eq("consumer", consumer));
        }

        return criteria.list();
    }

    @SuppressWarnings("unchecked")
    public List<Entitlement> findByPoolAttribute(String attributeName, String value) {
        return findByPoolAttribute(null, attributeName, value);
    }

    /**
     * For a given stack, find the eldest active entitlement with a subscription ID.
     * This is used to look up the upstream subscription certificate to use to talk to
     * the CDN.
     *
     * @param consumer the consumer
     * @param stackId the ID of the stack
     * @return the eldest active entitlement with a subscription ID, or null if none can
     * be found.
     */
    public Entitlement findUpstreamEntitlementForStack(Consumer consumer, String stackId) {
        Date currentDate = new Date();
        Criteria activeNowQuery = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.productAttributes", "attrs")
            .add(Restrictions.le("ent_pool.startDate", currentDate))
            .add(Restrictions.ge("ent_pool.endDate", currentDate))
            .add(Restrictions.eq("attrs.name", "stacking_id"))
            .add(Restrictions.eq("attrs.value", stackId))
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceSubscription", "sourceSub")
                .add(Restrictions.isNotNull("sourceSub.id"))
            .addOrder(Order.asc("created")) // eldest entitlement
            .setMaxResults(1);
        return (Entitlement) activeNowQuery.uniqueResult();
    }

    public Page<List<Entitlement>> listAll(EntitlementFilterBuilder filters, PageRequest pageRequest) {
        Criteria criteria = createSecureCriteria();
        criteria.createAlias("pool", "p");
        filters.applyTo(criteria);
        return listByCriteria(criteria, pageRequest);
    }

}

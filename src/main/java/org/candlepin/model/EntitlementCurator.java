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

import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
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
        PageRequest pageRequest) {
        Criteria query = createSecureCriteria()
            .createAlias("pool", "p")
            .add(Restrictions.eq("consumer", consumer))
            // Never show a consumer expired entitlements
            .add(Restrictions.ge("p.endDate", new Date()));
        return listByCriteria(query, pageRequest);
    }

    public List<Entitlement> listByConsumer(Consumer consumer) {
        Page<List<Entitlement>> p = listByConsumer(consumer, null);
        return p.getPageData();
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

    /*
     * Creates date filtering criteria to for checking if an entitlement has any overlap
     * with a "modifying" entitlement that has just been granted.
     */
    private Criteria createModifiesDateFilteringCriteria(Consumer consumer, Date startDate,
        Date endDate) {
        Criteria criteria = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createCriteria("pool")
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

    /**
     * List all entitlements for the given consumer which provide the given product ID,
     * and overlap at least partially with the given start and end dates.
     *
     * i.e. given start date must be within the entitlements start/end dates, or
     * the given end date must be within the entitlements start/end dates,
     * or the given start date must be before the entitlement *and* the given end date
     * must be after entitlement. (i.e. we are looking for *any* overlap)
     *
     * @param consumer Consumer whose entitlements we're checking.
     * @param productId Find entitlements providing this productId.
     * @param startDate Find entitlements
     * @param endDate
     * @return list of entitlements providing the given product
     */
    public Set<Entitlement> listProviding(Consumer consumer, String productId,
        Date startDate, Date endDate) {

        // Will re-use this criteria for both queries we need to do:

        // Find direct matches on the pool's product ID:
        Criteria parentProductCrit = createModifiesDateFilteringCriteria(consumer,
            startDate, endDate).add(
                Restrictions.eq("productId", productId));

        // Using a set to prevent duplicate matches, if somehow
        Set<Entitlement> finalResults = new HashSet<Entitlement>();
        finalResults.addAll(parentProductCrit.list());

        Criteria providedCrit = createModifiesDateFilteringCriteria(consumer, startDate,
            endDate)
            .createCriteria("providedProducts")
            .add(Restrictions.eq("productId", productId));
        finalResults.addAll(providedCrit.list());

        return finalResults;
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
            e.getConsumer(), e.getStartDate(), e.getEndDate()).list();
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
    public List<Entitlement> findByStackId(Consumer consumer, String stackId) {
        DetachedCriteria stackCriteria = DetachedCriteria.forClass(
            ProductPoolAttribute.class, "attr")
                .add(Restrictions.eq("name", "stacking_id"))
                .add(Restrictions.eq("value", stackId))
                .add(Property.forName("ent_pool.id").eqProperty("attr.pool.id"))
                .setProjection(Projections.property("attr.id"));

        Criteria activeNowQuery = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createCriteria("pool", "ent_pool")
                .add(Restrictions.isNull("sourceEntitlement"))
                .add(Restrictions.isNull("sourceStackId"))
                .add(Subqueries.exists(stackCriteria));
        return activeNowQuery.list();
    }
}

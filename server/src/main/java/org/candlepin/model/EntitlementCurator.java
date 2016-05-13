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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;

import com.google.common.collect.Iterables;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Query;



/**
 * EntitlementCurator
 */
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static Logger log = LoggerFactory.getLogger(EntitlementCurator.class);
    private ProductCurator productCurator;

    /**
     * default ctor
     */
    @Inject
    public EntitlementCurator(ProductCurator productCurator) {
        super(Entitlement.class);
        this.productCurator = productCurator;
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

    private Criteria createCriteriaFromFilters(EntitlementFilterBuilder filterBuilder) {
        Criteria criteria = createSecureCriteria();
        criteria.createAlias("pool", "p");

        // Add the required aliases for the filter builder only if required.
        if (filterBuilder != null && filterBuilder.hasMatchFilters()) {
            criteria.createAlias("p.product", "product");
            criteria.createAlias("p.providedProducts", "provProd", CriteriaSpecification.LEFT_JOIN);
            criteria.createAlias("provProd.productContent", "ppcw", CriteriaSpecification.LEFT_JOIN);
            criteria.createAlias("ppcw.content", "ppContent", CriteriaSpecification.LEFT_JOIN);
            criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        }
        // Never show a consumer expired entitlements
        criteria.add(Restrictions.ge("p.endDate", new Date()));
        filterBuilder.applyTo(criteria);
        return criteria;
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
        Criteria criteria = createCriteriaFromFilters(filters);
        criteria.add(Restrictions.eq("consumer", consumer));
        criteria.addOrder(Order.asc("p.id"));
        return criteria.list();
    }

    @SuppressWarnings("unchecked")
    public List<Entitlement> listByConsumerAndPoolId(Consumer consumer, String poolId) {
        Criteria query = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool.id", poolId));
        query.add(Restrictions.eq("consumer", consumer));
        return listByCriteria(query);
    }

    public Page<List<Entitlement>> listByConsumer(Consumer consumer, String productId,
        EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(consumer, "consumer", productId, filters, pageRequest);
    }

    public Page<List<Entitlement>> listByOwner(Owner owner, String productId,
        EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(owner, "owner", productId, filters, pageRequest);
    }

    public Page<List<Entitlement>> listAll(EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(null, null, null, filters, pageRequest);
    }

    private Page<List<Entitlement>> listFilteredPages(AbstractHibernateObject object, String objectType,
        String productId, EntitlementFilterBuilder filters, PageRequest pageRequest) {
        Page<List<Entitlement>> entitlementsPage;
        Owner owner = null;
        if (object != null) {
            owner = (object instanceof Owner) ? (Owner) object : ((Consumer) object).getOwner();
        }

        // No need to add filters when matching by product.
        if (object != null && productId != null) {
            Product p = productCurator.lookupById(owner, productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr(
                    "Product with ID ''{0}'' could not be found.", productId));
            }
            entitlementsPage = listByProduct(object, objectType, productId, pageRequest);
        }
        else {
            // Build up any provided entitlement filters from query params.
            Criteria criteria = createCriteriaFromFilters(filters);
            if (object != null) {
                criteria.add(Restrictions.eq(objectType, object));
            }
            entitlementsPage = listByCriteria(criteria, pageRequest);
        }

        return entitlementsPage;
    }

    public List<Entitlement> listByOwner(Owner owner) {
        Criteria query = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("owner", owner));

        return listByCriteria(query);
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
            entitledProductIds.add(p.getProduct().getId());
            for (Product pp : p.getProvidedProducts()) {
                entitledProductIds.add(pp.getId());
            }

            // A distributor should technically be entitled to derived products and
            // will need to be able to sync content downstream.
            if (c.getType().isManifest() && p.getDerivedProduct() != null) {
                entitledProductIds.add(p.getDerivedProduct().getId());
                if (p.getDerivedProvidedProducts() != null) {
                    for (Product dpp : p.getDerivedProvidedProducts()) {
                        entitledProductIds.add(dpp.getId());
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

    /**
     * A version of list Modifying that finds Entitlements that modify
     * input entitlements.
     * When dealing with large amount of entitlements for which it is necessary
     * to determine their modifier products.
     * @param entitlement
     * @return Entitlements that are being modified by the input entitlements
     */
    public Collection<String> batchListModifying(Iterable<Entitlement> entitlements) {
        List<String> eids = new LinkedList<String>();

        if (entitlements != null && entitlements.iterator().hasNext()) {
            String hql =
                "SELECT DISTINCT eOut.id" +
                "    FROM Entitlement eOut" +
                "        JOIN eOut.pool outPool" +
                "        JOIN outPool.providedProducts outProvided" +
                "        JOIN outProvided.productContent outProvContent" +
                "        JOIN outProvContent.content outContent" +
                "        JOIN outContent.modifiedProductIds outModProdId" +
                "    WHERE" +
                "        outPool.endDate >= current_date AND" +
                "        eOut NOT IN (:ein) AND" +
                "        EXISTS (" +
                "            SELECT eIn" +
                "                FROM Entitlement eIn" +
                "                    JOIN eIn.consumer inConsumer" +
                "                    JOIN eIn.pool inPool" +
                "                    JOIN inPool.product inMktProd" +
                "                    LEFT JOIN inPool.providedProducts inProvidedProd" +
                "                WHERE eIn in (:ein) AND inConsumer = eOut.consumer AND" +
                "                    inPool.endDate >= outPool.startDate AND" +
                "                    inPool.startDate <= outPool.endDate AND" +
                "                    (inProvidedProd.id = outModProdId OR inMktProd.id = outModProdId)" +
                "        )";

            Query query = this.getEntityManager().createQuery(hql);

            Iterable<List<Entitlement>> blocks = Iterables.partition(
                entitlements, AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE
            );

            for (List<Entitlement> block : blocks) {
                eids.addAll(query.setParameter("ein", entitlements).getResultList());
            }
        }

        return eids;
    }

    public Collection<String> listModifying(Entitlement entitlement) {
        return batchListModifying(java.util.Arrays.asList(entitlement));
    }

    public Collection<String> listModifying(Collection entitlements) {
        return batchListModifying(entitlements);
    }

    public Map<Consumer, List<Entitlement>> getDistinctConsumers(List<Entitlement> entsToRevoke) {
        Map<Consumer, List<Entitlement>> result = new HashMap<Consumer, List<Entitlement>>();
        for (Entitlement ent : entsToRevoke) {
            List<Entitlement> ents = result.get(ent.getConsumer());
            if (ents == null) {
                ents = new ArrayList<Entitlement>();
                result.put(ent.getConsumer(), ents);
            }
            ents.add(ent);
        }
        return result;
    }

    public Page<List<Entitlement>> listByConsumerAndProduct(Consumer consumer,
        String productId, PageRequest pageRequest) {
        return listByProduct(consumer, "consumer", productId, pageRequest);
    }

    @Transactional
    private Page<List<Entitlement>> listByProduct(AbstractHibernateObject object, String objectType,
        String productId, PageRequest pageRequest) {

        Criteria query = createSecureCriteria()
            .add(Restrictions.eq(objectType, object))
            .createAlias("pool", "p")
            .createAlias("p.product", "prod")
            .createAlias("p.providedProducts", "pp", CriteriaSpecification.LEFT_JOIN)
            // Never show a consumer expired entitlements
            .add(Restrictions.ge("p.endDate", new Date()))
            .add(Restrictions.or(Restrictions.eq("prod.id", productId), Restrictions.eq("pp.id", productId)));

        Page<List<Entitlement>> page = listByCriteria(query, pageRequest);

        return page;
    }

    @Transactional
    public void delete(Entitlement entity) {
        Entitlement toDelete = find(entity.getId());
        log.debug("Deleting entitlement: {}", toDelete);
        log.debug("certs.size = {}", toDelete.getCertificates().size());

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
        return findByStackIds(consumer, Arrays.asList(stackId));
    }

    /**
     * Find the entitlements for the given consumer that are part of the
     * specified stacks.
     *
     * @param consumer the consumer
     * @param stackIds the IDs of the stacks
     * @return the list of entitlements for the consumer that are in the stack.
     */
    @SuppressWarnings("unchecked")
    public List<Entitlement> findByStackIds(Consumer consumer, Collection stackIds) {
        Criteria activeNowQuery = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.product", "product")
            .createAlias("product.attributes", "attrs")
            .add(Restrictions.eq("attrs.name", "stacking_id"))
            .add(unboundedInCriterion("attrs.value", stackIds))
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceStack", "ss", JoinType.LEFT_OUTER_JOIN)
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

    /**
     * Batch deletes a list of entitlements.
     * @param pools
     */
    public void batchDelete(List<Entitlement> entitlements) {
        for (Entitlement ent : entitlements) {
            log.debug("Deleting entitlement: {}", ent);
            log.debug("certs.size = {}", ent.getCertificates().size());

            for (EntitlementCertificate cert : ent.getCertificates()) {
                getEntityManager().remove(cert);
            }
            ent.getCertificates().clear();
            getEntityManager().remove(ent);

            // Maintain runtime consistency.
            ent.getCertificates().clear();
            ent.getConsumer().getEntitlements().remove(ent);
        }
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
            .createAlias("ent_pool.product", "product")
            .createAlias("product.attributes", "attrs")
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

    /**
     * Marks the given entitlements as dirty; forcing a regeneration the next time it is requested.
     *
     * @param entitlementIds
     *  A collection of IDs of the entitlements to mark dirty
     *
     * @return
     *  The number of certificates updated
     */
    @Transactional
    public int markEntitlementsDirty(Iterable<String> entitlementIds) {
        int count = 0;

        if (entitlementIds != null && entitlementIds.iterator().hasNext()) {
            Iterable<List<String>> blocks = Iterables.partition(
                entitlementIds, AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE
            );

            String hql = "UPDATE Entitlement SET dirty = true WHERE id IN (:entIds)";
            Query query = this.getEntityManager().createQuery(hql);

            for (List<String> block : blocks) {
                count += query.setParameter("entIds", block).executeUpdate();
            }
        }

        return count;
    }
}

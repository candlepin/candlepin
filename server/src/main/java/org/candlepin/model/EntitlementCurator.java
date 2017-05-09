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
import org.hibernate.Hibernate;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.sql.JoinType;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;



/**
 * EntitlementCurator
 */
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static Logger log = LoggerFactory.getLogger(EntitlementCurator.class);

    private CandlepinQueryFactory cpQueryFactory;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;

    /**
     * default ctor
     */
    @Inject
    public EntitlementCurator(OwnerProductCurator ownerProductCurator, ProductCurator productCurator,
        CandlepinQueryFactory cpQueryFactory) {
        super(Entitlement.class);

        this.cpQueryFactory = cpQueryFactory;
        this.ownerProductCurator = ownerProductCurator;
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

    @SuppressWarnings("checkstyle:indentation")
    private Criteria createCriteriaFromFilters(EntitlementFilterBuilder filterBuilder) {
        Criteria criteria = createSecureCriteria()
            .createAlias("pool", "Pool")
            .createAlias("Pool.product", "Product")
            .setProjection(Projections.distinct(Projections.id()))
            .add(Restrictions.ge("Pool.endDate", new Date()));


        boolean joinedProvided = false;

        if (filterBuilder != null) {
            Collection<String> values = filterBuilder.getIdFilters();

            if (values != null && !values.isEmpty()) {
                criteria.add(cpRestrictions.in("Pool.id", values));
            }

            // Product ID filters
            values = filterBuilder.getProductIdFilter();

            if (values != null && !values.isEmpty()) {
                if (!joinedProvided) {
                    criteria.createAlias("Pool.providedProducts", "Provided", JoinType.LEFT_OUTER_JOIN);
                    joinedProvided = true;
                }

                criteria.add(Restrictions.or(
                    cpRestrictions.in("Product.id", values),
                    cpRestrictions.in("Provided.id", values)
                ));
            }

            // Subscription ID filter
            String value = filterBuilder.getSubscriptionIdFilter();

            if (value != null && !value.isEmpty()) {
                criteria.createAlias("Pool.sourceSubscription", "srcsub")
                    .add(Restrictions.eq("srcsub.subscriptionId", value));
            }

            // Matches stuff
            values = filterBuilder.getMatchesFilters();
            if (values != null && !values.isEmpty()) {
                if (!joinedProvided) {
                    // This was an inner join -- might end up being important later
                    criteria.createAlias("Pool.providedProducts", "Provided", JoinType.LEFT_OUTER_JOIN);
                    joinedProvided = true;
                }

                criteria.createAlias("Provided.productContent", "PPC", JoinType.LEFT_OUTER_JOIN);
                criteria.createAlias("PPC.content", "Content", JoinType.LEFT_OUTER_JOIN);

                for (String matches : values) {
                    String sanitized = this.sanitizeMatchesFilter(matches);

                    Disjunction matchesDisjunction = Restrictions.disjunction();

                    matchesDisjunction.add(cpRestrictions.ilike("Pool.contractNumber", sanitized, '!'))
                        .add(cpRestrictions.ilike("Pool.orderNumber", sanitized, '!'))
                        .add(cpRestrictions.ilike("Product.id", sanitized, '!'))
                        .add(cpRestrictions.ilike("Product.name", sanitized, '!'))
                        .add(cpRestrictions.ilike("Provided.id", sanitized, '!'))
                        .add(cpRestrictions.ilike("Provided.name", sanitized, '!'))
                        .add(cpRestrictions.ilike("Content.name", sanitized, '!'))
                        .add(cpRestrictions.ilike("Content.label", sanitized, '!'))
                        .add(this.addProductAttributeFilterSubquery(Product.Attributes.SUPPORT_LEVEL,
                            Arrays.asList(matches)));

                    criteria.add(matchesDisjunction);
                }
            }

            // Attribute filters
            for (Map.Entry<String, List<String>> entry : filterBuilder.getAttributeFilters().entrySet()) {
                String attrib = entry.getKey();
                values = entry.getValue();

                if (attrib != null && !attrib.isEmpty()) {
                    // TODO:
                    // Searching both pool and product attributes is likely an artifact from the days
                    // when we copied SKU product attributes to the pool. I don't believe there's any
                    // precedence for attribute lookups now that they're no longer being copied over.
                    // If this is not the case, then the following logic is broken and will need to be
                    // adjusted to account for one having priority over the other.

                    if (values != null && !values.isEmpty()) {
                        List<String> positives = new LinkedList<String>();
                        List<String> negatives = new LinkedList<String>();

                        for (String attrValue : values) {
                            if (attrValue.startsWith("!")) {
                                negatives.add(attrValue.substring(1));
                            }
                            else {
                                positives.add(attrValue);
                            }
                        }

                        if (!positives.isEmpty()) {
                            criteria.add(this.addAttributeFilterSubquery(attrib, positives));
                        }

                        if (!negatives.isEmpty()) {
                            criteria.add(Restrictions.not(
                                this.addAttributeFilterSubquery(attrib, negatives)));
                        }
                    }
                    else {
                        criteria.add(this.addAttributeFilterSubquery(attrib, values));
                    }
                }
            }
        }

        return criteria;
    }

    @SuppressWarnings("checkstyle:indentation")
    private Criterion addAttributeFilterSubquery(String key, Collection<String> values) {
        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute
        DetachedCriteria poolAttrSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
            .createAlias("PoolI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
            .add(Restrictions.eq("attrib.indices", key));

        // Impl note:
        // The SQL restriction below uses some Hibernate magic value to get the pool ID from the
        // outer-most query. We can't use {alias} here, since we're basing the subquery on Product
        // instead of Pool to save ourselves an unnecessary join. Similarly, we use an SQL
        // restriction here because we can query the information we need, hitting only one table
        // with direct SQL, whereas the matching criteria query would end up requiring a minimum of
        // one join to get from pool to pool attributes.
        DetachedCriteria prodAttrSubquery = DetachedCriteria.forClass(Product.class, "ProdI")
            .createAlias("ProdI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Product.uuid").eqProperty("ProdI.uuid"))
            .add(Restrictions.eq("attrib.indices", key))
            .add(Restrictions.sqlRestriction(
                "NOT EXISTS (SELECT poolattr.pool_id FROM cp_pool_attribute poolattr " +
                "WHERE poolattr.pool_id = pool1_.id AND poolattr.name = ?)",
                key, StringType.INSTANCE
            ));

        if (values != null && !values.isEmpty()) {
            Disjunction poolAttrValueDisjunction = Restrictions.disjunction();
            Disjunction prodAttrValueDisjunction = Restrictions.disjunction();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    poolAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));

                    prodAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    poolAttrValueDisjunction.add(cpRestrictions.ilike("attrib.elements", attrValue, '!'));
                    prodAttrValueDisjunction.add(cpRestrictions.ilike("attrib.elements", attrValue, '!'));
                }
            }

            poolAttrSubquery.add(poolAttrValueDisjunction);
            prodAttrSubquery.add(prodAttrValueDisjunction);
        }

        return Restrictions.or(
            Subqueries.exists(poolAttrSubquery),
            Subqueries.exists(prodAttrSubquery)
        );
    }

    @SuppressWarnings("checkstyle:indentation")
    private Criterion addProductAttributeFilterSubquery(String key, Collection<String> values) {
        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute

        // Impl note:
        // The SQL restriction below uses some Hibernate magic value to get the pool ID from the
        // outer-most query. We can't use {alias} here, since we're basing the subquery on Product
        // instead of Pool to save ourselves an unnecessary join. Similarly, we use an SQL
        // restriction here because we can query the information we need, hitting only one table
        // with direct SQL, whereas the matching criteria query would end up requiring a minimum of
        // one join to get from pool to pool attributes.
        DetachedCriteria prodAttrSubquery = DetachedCriteria.forClass(Product.class, "ProdI")
            .createAlias("ProdI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Product.uuid").eqProperty("ProdI.uuid"))
            .add(Restrictions.eq("attrib.indices", key))
            .add(Restrictions.sqlRestriction(
                "NOT EXISTS (SELECT poolattr.pool_id FROM cp_pool_attribute poolattr " +
                "WHERE poolattr.pool_id = pool1_.id AND LOWER(poolattr.name) LIKE LOWER(?) ESCAPE '!')",
                key, StringType.INSTANCE
            ));

        if (values != null && !values.isEmpty()) {
            Disjunction prodAttrValueDisjunction = Restrictions.disjunction();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    prodAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    prodAttrValueDisjunction.add(cpRestrictions.ilike("attrib.elements", attrValue, '!'));
                }
            }

            prodAttrSubquery.add(prodAttrValueDisjunction);
        }

        return Subqueries.exists(prodAttrSubquery);
    }

    private String sanitizeMatchesFilter(String matches) {
        StringBuilder output = new StringBuilder();
        boolean escaped = false;

        for (int index = 0; index < matches.length(); ++index) {
            char c = matches.charAt(index);

            switch (c) {
                case '!':
                case '_':
                case '%':
                    output.append('!').append(c);
                    break;

                case '\\':
                    if (escaped) {
                        output.append(c);
                    }

                    escaped = !escaped;
                    break;

                case '*':
                case '?':
                    if (!escaped) {
                        output.append(c == '*' ? '%' : '_');
                        break;
                    }

                default:
                    output.append(c);
                    escaped = false;
            }
        }

        return output.toString();
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
        Criteria criteria = this.createCriteriaFromFilters(filters)
            .add(Restrictions.eq("consumer", consumer));

        List<String> entitlementIds = criteria.list();

        if (entitlementIds != null && !entitlementIds.isEmpty()) {
            criteria = this.currentSession()
                .createCriteria(Entitlement.class)
                .add(cpRestrictions.in("id", entitlementIds));

            return criteria.list();
        }

        return Collections.<Entitlement>emptyList();

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
            Product p = this.ownerProductCurator.getProductById(owner, productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr(
                    "Product with ID ''{0}'' could not be found.", productId));
            }
            entitlementsPage = listByProduct(object, objectType, productId, pageRequest);
        }
        else {
            // Build up any provided entitlement filters from query params.
            Criteria criteria = this.createCriteriaFromFilters(filters);
            if (object != null) {
                criteria.add(Restrictions.eq(objectType, object));
            }

            List<String> entitlementIds = criteria.list();

            if (entitlementIds != null && !entitlementIds.isEmpty()) {
                criteria = this.currentSession()
                    .createCriteria(Entitlement.class)
                    .add(cpRestrictions.in("id", entitlementIds));

                entitlementsPage = listByCriteria(criteria, pageRequest);
            }
            else {
                entitlementsPage = new Page<List<Entitlement>>();
                entitlementsPage.setPageData(Collections.<Entitlement>emptyList());
                entitlementsPage.setMaxRecords(0);
            }
        }

        return entitlementsPage;
    }

    public CandlepinQuery<Entitlement> listByOwner(Owner owner) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("owner", owner));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<Entitlement> listByEnvironment(Environment environment) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .createCriteria("consumer")
            .add(Restrictions.eq("environment", environment));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    /**
     * List entitlements for a consumer which are valid for a specific date.
     *
     * @param consumer Consumer to list entitlements for.
     * @param activeOn The date we want to see entitlements which are active on.
     * @return List of entitlements.
     */
    public CandlepinQuery<Entitlement> listByConsumerAndDate(Consumer consumer, Date activeOn) {

        /*
         * Essentially the opposite of the above query which searches for entitlement
         * overlap with a "modifying" entitlement being granted. This query is used to
         * search for modifying entitlements which overlap with a regular entitlement
         * being granted. As such the logic is basically reversed.
         *
         */
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createCriteria("pool")
            .add(Restrictions.le("startDate", activeOn))
            .add(Restrictions.ge("endDate", activeOn));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Lists dirty entitlements for the given consumer. If the consumer does not have any dirty
     * entitlements, this method returns an empty collection.
     *
     * @param consumer
     *  The consumer for which to find dirty entitlements
     *
     * @return
     *  a collection of dirty entitlements for the given consumer
     */
    public List<Entitlement> listDirty(Consumer consumer) {
        Criteria criteria = this.currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("dirty", true));

        return criteria.list();
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
            for (Product pp : productCurator.getPoolProvidedProductsCached(p)) {
                entitledProductIds.add(pp.getId());
            }

            // A distributor should technically be entitled to derived products and
            // will need to be able to sync content downstream.
            if (c.isManifestDistributor() && p.getDerivedProduct() != null) {
                entitledProductIds.add(p.getDerivedProduct().getId());

                for (Product dpp : productCurator.getPoolDerivedProvidedProductsCached(p)) {
                    entitledProductIds.add(dpp.getId());
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
                "        eOut.owner = :owner AND" +
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

            Iterable<List<Entitlement>> blocks = Iterables.partition(entitlements, getInBlockSize());

            for (List<Entitlement> block : blocks) {
                Owner sampleOwner = block.get(0).getOwner();
                eids.addAll(query.setParameter("ein", block)
                    .setParameter("owner", sampleOwner).getResultList());
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

    /**
     * Deletes the given entitlement.
     *
     * @param entity
     *  The entitlement entity to delete
     */
    @Transactional
    public void delete(Entitlement entity) {
        Entitlement toDelete = find(entity.getId());

        if (toDelete != null) {
            this.deleteImpl(toDelete);

            // Maintain runtime consistency.
            entity.getCertificates().clear();
            entity.getConsumer().getEntitlements().remove(entity);
            entity.getPool().getEntitlements().remove(entity);
        }
    }

    /**
     * Deletes the given collection of entitlements.
     * <p/></p>
     * Note: Unlike the standard delete method, this method does not perform a lookup on an entity
     * before deleting it.
     *
     * @param entitlements
     *  The collection of entitlement entities to delete
     */
    public void batchDelete(Collection<Entitlement> entitlements) {
        for (Entitlement entitlement : entitlements) {
            this.deleteImpl(entitlement);

            // Maintain runtime consistency.
            entitlement.getCertificates().clear();

            if (Hibernate.isInitialized(entitlement.getConsumer().getEntitlements())) {
                entitlement.getConsumer().getEntitlements().remove(entitlement);
            }

            if (Hibernate.isInitialized(entitlement.getPool().getEntitlements())) {
                entitlement.getPool().getEntitlements().remove(entitlement);
            }
        }
    }

    private void deleteImpl(Entitlement entity) {
        log.debug("Deleting entitlement: {}", entity);
        EntityManager entityManager = this.getEntityManager();

        if (entity.getCertificates() != null) {
            log.debug("certs.size = {}", entity.getCertificates().size());

            for (EntitlementCertificate cert : entity.getCertificates()) {
                entityManager.remove(cert);
            }
        }

        entityManager.remove(entity);
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
    public CandlepinQuery<Entitlement> findByStackId(Consumer consumer, String stackId) {
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
    public CandlepinQuery<Entitlement> findByStackIds(Consumer consumer, Collection stackIds) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.product", "product")
            .createAlias("product.attributes", "attrs")
            .add(Restrictions.eq("attrs.indices", Product.Attributes.STACKING_ID))
            .add(cpRestrictions.in("attrs.elements", stackIds))
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceStack", "ss", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.isNull("ss.id"));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByPoolAttribute(Consumer consumer, String attributeName,
        String value) {

        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.attributes", "attrs")
            .add(Restrictions.eq("attrs.indices", attributeName))
            .add(Restrictions.eq("attrs.elements", value));

        if (consumer != null) {
            criteria.add(Restrictions.eq("consumer", consumer));
        }

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByPoolAttribute(String attributeName, String value) {
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
            .add(Restrictions.eq("attrs.indices", Product.Attributes.STACKING_ID))
            .add(Restrictions.eq("attrs.elements", stackId).ignoreCase())
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
            Iterable<List<String>> blocks = Iterables.partition(entitlementIds, getInBlockSize());

            String hql = "UPDATE Entitlement SET dirty = true WHERE id IN (:entIds)";
            Query query = this.getEntityManager().createQuery(hql);

            for (List<String> block : blocks) {
                count += query.setParameter("entIds", block).executeUpdate();
            }
        }

        return count;
    }
}

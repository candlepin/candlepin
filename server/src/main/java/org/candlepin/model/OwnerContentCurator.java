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

import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;



/**
 * The OwnerContentCurator provides functionality for managing the mapping between owners and
 * content.
 */
@Singleton
public class OwnerContentCurator extends AbstractHibernateCurator<OwnerContent> {
    private static Logger log = LoggerFactory.getLogger(OwnerContentCurator.class);

    /**
     * Default constructor
     */
    public OwnerContentCurator() {
        super(OwnerContent.class);
    }

    public Content getContentById(Owner owner, String contentId) {
        return this.getContentById(owner.getId(), contentId);
    }

    @Transactional
    public Content getContentById(String ownerId, String contentId) {
        return (Content) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .createAlias("content", "content")
            .setProjection(Projections.property("content"))
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.eq("content.id", contentId))
            .uniqueResult();
    }

    public CandlepinQuery<Owner> getOwnersByContent(Content content) {
        return this.getOwnersByContent(content.getId());
    }

    public CandlepinQuery<Owner> getOwnersByContent(String contentId) {
        // Impl note:
        // We have to do this in two queries due to how Hibernate processes projections here. We're
        // working around a number of issues:
        //  1. Hibernate does not rearrange a query based on a projection, but instead, performs a
        //     second query (as we're doing here).
        //  2. Because the initial query is not rearranged, we are actually pulling a collection of
        //     join objects, so filtering/sorting via CandlepinQuery is incorrect or broken
        //  3. The second query Hibernate performs uses the IN operator without any protection for
        //     the MySQL/MariaDB element limits.
        String jpql = "SELECT oc.owner.id FROM OwnerContent oc WHERE oc.content.id = :content_id";

        List<String> ids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("content_id", contentId)
            .getResultList();

        if (ids != null && !ids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Owner.class, null)
                .add(CPRestrictions.in("id", ids));

            return this.cpQueryFactory.<Owner>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Owner>buildQuery();
    }

    /**
     * Fetches a collection of content UUIDs currently mapped to the given owner. If the owner is
     * not mapped to any content, an empty collection will be returned.
     *
     * @param owner
     *  The owner for which to fetch content UUIDs
     *
     * @return
     *  a collection of content UUIDs belonging to the given owner
     */
    public Collection<String> getContentUuidsByOwner(Owner owner) {
        return this.getContentUuidsByOwner(owner.getId());
    }

    /**
     * Fetches a collection of content UUIDs currently mapped to the given owner. If the owner is
     * not mapped to any content, an empty collection will be returned.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch content UUIDs
     *
     * @return
     *  a collection of content UUIDs belonging to the given owner
     */
    public Collection<String> getContentUuidsByOwner(String ownerId) {
        String jpql = "SELECT oc.content.uuid FROM OwnerContent oc WHERE oc.owner.id = :owner_id";

        List<String> uuids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        return uuids != null ? uuids : Collections.<String>emptyList();
    }

    /**
     * Builds a query for fetching the content currently mapped to the given owner.
     *
     * @param owner
     *  The owner for which to fetch content
     *
     * @return
     *  a query for fetching the content belonging to the given owner
     */
    public CandlepinQuery<Content> getContentByOwner(Owner owner) {
        return this.getContentByOwner(owner.getId());
    }

    /**
     * Builds a query for fetching the content currently mapped to the given owner.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch content
     *
     * @return
     *  a query for fetching the content belonging to the given owner
     */
    public CandlepinQuery<Content> getContentByOwner(String ownerId) {
        // Impl note: See getOwnersByContent for details on why we're doing this in two queries
        Collection<String> uuids = this.getContentUuidsByOwner(ownerId);

        if (!uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Content.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Content>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Content>buildQuery();
    }

    public CandlepinQuery<Content> getContentByIds(Owner owner, Collection<String> contentIds) {
        return this.getContentByIds(owner.getId(), contentIds);
    }

    public CandlepinQuery<Content> getContentByIds(String ownerId, Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return this.cpQueryFactory.<Content>buildQuery();
        }

        // Impl note: See getOwnersByContent for details on why we're doing this in two queries
        Session session = this.currentSession();

        List<String> uuids = session.createCriteria(OwnerContent.class)
            .createAlias("owner", "owner")
            .createAlias("content", "content")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(CPRestrictions.in("content.id", contentIds))
            .setProjection(Projections.property("content.uuid"))
            .list();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Content.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Content>buildQuery(session, criteria);
        }

        return this.cpQueryFactory.<Content>buildQuery();
    }

    @Transactional
    public long getOwnerCount(Content content) {
        String jpql = "SELECT count(op) FROM OwnerContent op WHERE op.content.uuid = :content_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("content_uuid", content.getUuid())
            .getSingleResult();

        return count;
    }

    /**
     * Checks if the owner has an existing version of the specified content. This lookup is
     * different than the mapping check in that this check will find any content with the
     * specified ID, as opposed to checking if a specific version is mapped to the owner.
     *
     * @param owner
     *  The owner of the content to lookup
     *
     * @param contentId
     *  The Red Hat ID of the content to lookup
     *
     * @return
     *  true if the owner has a content with the given RHID; false otherwise
     */
    @Transactional
    public boolean contentExists(Owner owner, String contentId) {
        String jpql = "SELECT count(op) FROM OwnerContent op " +
            "WHERE op.owner.id = :owner_id AND op.content.id = :content_id";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("content_id", contentId)
            .getSingleResult();

        return count > 0;
    }

    @Transactional
    public boolean isContentMappedToOwner(Content content, Owner owner) {
        String jpql = "SELECT count(op) FROM OwnerContent op " +
            "WHERE op.owner.id = :owner_id AND op.content.uuid = :content_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("content_uuid", content.getUuid())
            .getSingleResult();

        return count > 0;
    }

    @Transactional
    public boolean mapContentToOwner(Content content, Owner owner) {
        if (!this.isContentMappedToOwner(content, owner)) {
            this.create(new OwnerContent(owner, content));

            return true;
        }

        return false;
    }

    // TODO:
    // These pseudo-bulk operations should be updated so they're not flushing after each update.

    @Transactional
    public int mapContentToOwners(Content content, Owner... owners) {
        int count = 0;

        for (Owner owner : owners) {
            if (this.mapContentToOwner(content, owner)) {
                ++count;
            }
        }

        return count;
    }

    @Transactional
    public int mapOwnerToContent(Owner owner, Content... content) {
        int count = 0;

        for (Content elem : content) {
            if (this.mapContentToOwner(elem, owner)) {
                ++count;
            }
        }

        return count;
    }

    @Transactional
    public boolean removeOwnerFromContent(Content content, Owner owner) {
        String jpql = "DELETE FROM OwnerContent op " +
            "WHERE op.content.uuid = :content_uuid AND op.owner.id = :owner_id";

        int rows = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("content_uuid", content.getUuid())
            .executeUpdate();

        return rows > 0;
    }

    @Transactional
    public int clearOwnersForContent(Content content) {
        String jpql = "DELETE FROM OwnerContent op " +
            "WHERE op.content.uuid = :content_uuid";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("content_uuid", content.getUuid())
            .executeUpdate();
    }

    @Transactional
    public int clearContentForOwner(Owner owner) {
        String jpql = "DELETE FROM OwnerContent op " +
            "WHERE op.owner.id = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .executeUpdate();
    }

    /**
     * Retrieves a map containing all known versions of the content specified by IDs, for all orgs
     * <em>except</em> the org specified. If no content is found for the specified IDs in other
     * orgs, this method returns an empty map.
     *
     * @param owner
     *  The owner to exclude from the content lookup. If this value is null, no owner-filtering
     *  will be performed.
     *
     * @param contentIds
     *  A collection of content IDs for which to fetch all known versions
     *
     * @return
     *  A map containing all known versions of the given content, mapped by Red Hat ID
     */
    public Map<String, Set<Content>> getVersionedContentById(Owner owner, Collection<String> contentIds) {
        Map<String, Set<Content>> result = new HashMap<>();

        if (contentIds != null && !contentIds.isEmpty()) {
            String jpql;

            if (owner != null) {
                jpql = "SELECT c FROM OwnerContent oc JOIN oc.content c " +
                    "WHERE oc.owner.id != :owner_id AND c.id IN (:cids)";
            }
            else {
                jpql = "SELECT c FROM Content c WHERE c.id IN (:cids)";
            }

            TypedQuery<Content> query = this.getEntityManager().createQuery(jpql, Content.class);

            if (owner != null) {
                query.setParameter("owner_id", owner.getId());
            }

            for (Collection<String> block : this.partition(contentIds)) {
                List<Content> fetched = query.setParameter("cids", block)
                    .getResultList();

                for (Content entity : fetched) {
                    Set<Content> idSet = result.get(entity.getId());

                    if (idSet == null) {
                        idSet = new HashSet<>();
                        result.put(entity.getId(), idSet);
                    }

                    idSet.add(entity);
                }
            }
        }

        return result;
    }

    /**
     * Retrieves a criteria which can be used to fetch a list of content with the specified Red Hat
     * content ID and entity version belonging to owners other than the owner provided. If no
     * content were found matching the given criteria, this method returns an empty list.
     *
     * @param owner
     *  The owner whose content should be excluded from the results. If an owner is not provided,
     *  no additional filtering will be performed.
     *
     * @param contentVersions
     *  A mapping of Red Hat content IDs to content versions to fetch
     *
     * @return
     *  a mapping of Red Hat content IDs to candidate alternate versions
     */
    @SuppressWarnings("checkstyle:indentation")
    public Map<String, List<Content>> getContentByVersions(Owner owner,
        Map<String, Integer> contentVersions) {

        Map<String, List<Content>> contentMap = new HashMap<>();

        if (contentVersions == null || contentVersions.isEmpty()) {
            return contentMap;
        }

        // Impl note:
        // We perform this operation with two queries here to optimize out some unnecessary queries
        // when pulling content information. Even when pulling content in a batch, Hibernate will
        // pull the content collections (modified product IDs) as a separate query for each content
        // (ugh). By breaking this into two queries -- one for getting the content UUIDs and one
        // for pulling the actual content -- we will save upwards of two DB hits per content
        // filtered. We will lose time in the cases where we don't filter any content, or the
        // content we filter don't have any data in their collections; but we're only using one
        // additional query in those cases, versus n additional in the normal case.

        Set<String> uuids = new HashSet<>();

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);

        Root<OwnerContent> root = query.from(OwnerContent.class);
        Join<OwnerContent, Content> content = root.join(OwnerContent_.content);

        query.select(content.get(Content_.uuid))
            .distinct(true);

        // Impl note:
        // We have room to go higher with the block size here, but building this query is so slow that
        // as the block size goes up, it's actually slower than issuing smaller queries more often.
        // Also, around a block size of around 2.5k, Hibernate's query builder starts hitting stack
        // overflows.
        int blockSize = 1000;
        for (List<Map.Entry<String, Integer>> block : this.partition(contentVersions.entrySet(), blockSize)) {
            Predicate[] predicates = new Predicate[block.size()];
            int index = 0;

            for (Map.Entry<String, Integer> entry : block) {
                predicates[index++] = builder.and(
                    builder.equal(content.get(Content_.id), entry.getKey()),
                    builder.equal(content.get(Content_.entityVersion), entry.getValue()));
            }

            Predicate predicate = builder.or(predicates);

            query.where(owner != null ?
                builder.and(builder.notEqual(root.get(OwnerContent_.ownerId), owner.getId()), predicate) :
                predicate);

            uuids.addAll(entityManager.createQuery(query)
                .getResultList());
        }

        if (uuids != null && !uuids.isEmpty()) {
            String jpql = "SELECT c FROM Content c WHERE c.uuid IN (:content_uuids)";
            TypedQuery<Content> cquery = entityManager.createQuery(jpql, Content.class);

            for (List<String> block : this.partition(uuids)) {
                for (Content element : cquery.setParameter("content_uuids", block).getResultList()) {
                    contentMap.computeIfAbsent(element.getId(), k -> new LinkedList<>())
                        .add(element);
                }
            }
        }

        return contentMap;
    }

    /**
     * Builds a query which can be used to fetch the current collection of orphaned content. Due
     * to the nature of this request, it is highly advised that this query be run within a
     * transaction, with a pessimistic lock mode set.
     *
     * @return
     *  A CandlepinQuery for fetching the orphaned content
     */
    public CandlepinQuery<Content> getOrphanedContent() {
        // As with many of the owner=>content lookups, we have to do this in two queries. Since
        // we need to start from content and do a left join back to owner content, we have to use
        // a native query instead of any of the ORM query languages

        String sql = "SELECT c.uuid " +
            "FROM cp2_content c LEFT JOIN cp2_owner_content oc ON c.uuid = oc.content_uuid " +
            "WHERE oc.owner_id IS NULL";

        List<String> uuids = this.getEntityManager()
            .createNativeQuery(sql)
            .getResultList();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = DetachedCriteria.forClass(Content.class)
                .add(CPRestrictions.in("uuid", uuids))
                .addOrder(Order.asc("uuid"));

            return this.cpQueryFactory.<Content>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Content>buildQuery();
    }


    /**
     * Updates the content references currently pointing to the original content to instead point to
     * the updated content for the specified owners.
     * <p/></p>
     * <strong>Note:</strong> product-content mappings are not modified by this method.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active Environment entities should
     * be manually evicted from the session and re-queried to ensure they will not clobber the
     * changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param owner
     *  The owner for which to apply the reference changes
     *
     * @param contentUuidMap
     *  A mapping of source content UUIDs to updated content UUIDs
     */
    @Transactional
    public void updateOwnerContentReferences(Owner owner, Map<String, String> contentUuidMap) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and HQL refuses to do any joining (implicit or otherwise), which
        // prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        if (contentUuidMap == null || contentUuidMap.isEmpty()) {
            // Nothing to update
            return;
        }

        Session session = this.currentSession();

        // FIXME: remove usage of bulkSQLUpdate. While it's a clever use of an SQL CASE to avoid
        // a ton of individual update statements, it still runs over the parameter limit in some
        // circumstances and the job could be better done with a temporary table and a couple
        // join-updates.

        for (Map<String, String> block : this.partitionMap(contentUuidMap)) {
            Map<String, Object> criteria = new HashMap<>();
            Map<Object, Object> uuidMap = Map.class.cast(block);
            criteria.put("content_uuid", block.keySet());
            criteria.put("owner_id", owner.getId());

            // owner content
            int count = this.bulkSQLUpdate(OwnerContent.DB_TABLE, "content_uuid", uuidMap, criteria);
            log.info("{} owner-content relations updated", count);

            // environment content
            String jpql = "SELECT e.id FROM Environment e WHERE e.owner.id = :owner_id";

            List<String> envIds = this.getEntityManager()
                .createQuery(jpql, String.class)
                .setParameter("owner_id", owner.getId())
                .getResultList();

            count = 0;
            if (envIds != null && !envIds.isEmpty()) {
                for (List<String> envBlock : this.partition(envIds)) {
                    criteria.clear();
                    criteria.put("environment_id", envBlock);
                    criteria.put("content_uuid", block.keySet());

                    count += this.bulkSQLUpdate(EnvironmentContent.DB_TABLE, "content_uuid", uuidMap,
                        criteria);
                }
            }

            log.info("{} environment-content relations updated", count);
        }
    }

    /**
     * Removes the content references currently pointing to the specified content for the given
     * owners.
     * <p/></p>
     * <strong>Note:</strong> product-content mappings are not modified by this method.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active Environment entities should
     * be manually evicted from the session and re-queried to ensure they will not clobber the
     * changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param owner
     *  The owner for which to apply the reference changes
     *
     * @param contentUuids
     *  A collection of content UUIDs representing the content entities to orphan
     */
    @Transactional
    public void removeOwnerContentReferences(Owner owner, Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            EntityManager entityManager = this.getEntityManager();

            for (List<String> block : this.partition(contentUuids)) {
                log.info("Removing owner-content references for owner: {}, {}", owner, block);

                // owner content
                String jpql = "DELETE FROM OwnerContent oc " +
                    "WHERE oc.ownerId = :owner_id " +
                    "AND oc.contentUuid IN (:content_uuids)";

                int count = entityManager.createQuery(jpql)
                    .setParameter("owner_id", owner.getId())
                    .setParameter("content_uuids", block)
                    .executeUpdate();

                log.info("{} owner-content relations removed", count);

                // environment content
                jpql = "SELECT e.id FROM Environment e WHERE e.owner.id = :owner_id";

                List<String> envIds = entityManager.createQuery(jpql, String.class)
                    .setParameter("owner_id", owner.getId())
                    .getResultList();

                count = 0;
                if (envIds != null && !envIds.isEmpty()) {
                    jpql = "DELETE FROM EnvironmentContent ec " +
                        "WHERE ec.environment.id IN (:environment_ids) " +
                        "AND ec.content.uuid IN (:content_uuids)";

                    Query query = entityManager.createQuery(jpql)
                        .setParameter("content_uuids", block);

                    for (List<String> envBlock : this.partition(envIds)) {
                        count += query.setParameter("environment_ids", envBlock)
                            .executeUpdate();
                    }
                }

                log.info("{} environment-content relations removed", count);
            }
        }
    }

    /**
     * Returns a mapping of content and content enabled flag.
     *
     * Note - If same content (say C1) is being enabled/disabled by two or more products, with at least
     * one of the product enabling the content C1 then, content (C1) with enabled (true)
     * state will have precedence.
     *
     * @param ownerId
     *  Id of an owner
     *
     * @return
     *  Returns a mapping of content and content enabled flag.
     */
    public Map<Content, Boolean> getActiveContentByOwner(String ownerId) {
        EntityManager entityManager = this.getEntityManager();
        Date now = new Date();

        // Store all of the product UUIDs gathered between the various queries we need to do here
        Set<String> productUuids = new HashSet<>();

        // Get all of the products from active pools for the specified owner
        String jpql = "SELECT pool.product.uuid FROM Pool pool " +
            "WHERE pool.owner.id = :owner_id AND pool.startDate <= :start_date AND pool.endDate >= :end_date";

        String sql = "SELECT prod.derived_product_uuid FROM cp2_products prod " +
            "  WHERE prod.uuid IN (:product_uuids) AND prod.derived_product_uuid IS NOT NULL " +
            "UNION " +
            "SELECT prov.provided_product_uuid FROM cp2_product_provided_products prov " +
            "  WHERE prov.product_uuid IN (:product_uuids)";

        productUuids.addAll(entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .setParameter("start_date", now)
            .setParameter("end_date", now)
            .getResultList());

        Query prodQuery = entityManager.createNativeQuery(sql);
        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() / 2);

        // Get all of the derived products and provided products for the products we've found, recursively
        for (Set<String> lastBlock = productUuids; !lastBlock.isEmpty();) {
            Set<String> children = new HashSet<>();

            for (List<String> block : this.partition(lastBlock, blockSize)) {
                children.addAll(prodQuery.setParameter("product_uuids", block)
                    .getResultList());
            }

            productUuids.addAll(children);
            lastBlock = children;
        }

        // Use the product UUIDs to select all related enabled content
        jpql = "SELECT DISTINCT pc.content, pc.enabled FROM ProductContent pc " +
            "WHERE pc.product.uuid IN (:product_uuids)";

        Query contentQuery = entityManager.createQuery(jpql);
        HashMap<Content, Boolean> activeContent = new HashMap<>();

        for (List<String> block : this.partition(productUuids)) {
            contentQuery.setParameter("product_uuids", block)
                .getResultList()
                .forEach(col -> {
                    Content content = (Content) ((Object[]) col)[0];
                    Boolean enabled = (Boolean) ((Object[]) col)[1];
                    activeContent.merge(content, enabled, (v1, v2) ->
                        v1 != null && v1.booleanValue() ? v1 : v2);
                });
        }

        return activeContent;
    }
}

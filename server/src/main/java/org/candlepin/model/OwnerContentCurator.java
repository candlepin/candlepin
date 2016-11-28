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

import org.hibernate.Criteria;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * The OwnerContentCurator provides functionality for managing the mapping between owners and
 * content.
 */
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
        //     the MySQL/MariaDB or Oracle element limits.
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
        String jpql = "SELECT oc.content.uuid FROM OwnerContent oc WHERE oc.owner.id = :owner_id";

        List<String> uuids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Content.class, null)
                .add(CPRestrictions.in("uuid", uuids))
                .add(CPRestrictions.in("id", contentIds));

            return this.cpQueryFactory.<Content>buildQuery(this.currentSession(), criteria);
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
     * Fetches the number of owners currently mapped to the given contents. If a provided content
     * UUID does not represent an existing content entity, or is not mapped to any owners, it will
     * not be included in the output.
     *
     * @param contentUuids
     *  A collection of content UUIDs for which to fetch the owner counts
     *
     * @return
     *  A mapping of content UUIDs to their owner counts
     */
    @Transactional
    public Map<String, Integer> getOwnerCounts(Collection<String> contentUuids) {
        StringBuilder builder = new StringBuilder("SELECT oc.contentUuid, COUNT(oc) FROM OwnerContent oc");

        if (contentUuids != null && !contentUuids.isEmpty()) {
            builder.append(" WHERE ");

            int blockCount = (int) Math.ceil(contentUuids.size() /
                (float) AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE);

            for (int i = 1; i <= blockCount; ++i) {
                if (i > 1) {
                    builder.append(" OR ");
                }

                builder.append("oc.contentUuid IN :content_uuids_").append(i);
            }
        }

        builder.append(" GROUP BY oc.contentUuid");

        Query query = this.currentSession().createQuery(builder.toString());

        if (contentUuids != null && !contentUuids.isEmpty()) {
            Iterable<List<String>> blocks = Iterables.partition(contentUuids,
                AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE);

            int offset = 0;
            for (List<String> block : blocks) {
                query.setParameterList("content_uuids_" + ++offset, block);
            }
        }

        Map<String, Integer> result = new HashMap<String, Integer>();

        ScrollableResults cursor = query.scroll();
        while (cursor.next()) {
            String uuid = cursor.getString(0);
            Integer count = Integer.valueOf((int) cursor.getLong(1).longValue());

            result.put(uuid, count);
        }

        cursor.close();

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
     *  a criteria for fetching content by version
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Content> getContentByVersions(Owner owner, Map<String, Integer> contentVersions) {
        if (contentVersions == null || contentVersions.isEmpty()) {
            return this.cpQueryFactory.<Content>buildQuery();
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

        Disjunction disjunction = Restrictions.disjunction();
        Criteria uuidCriteria = this.createSecureCriteria("oc")
            .createAlias("oc.content", "c")
            .add(disjunction)
            .setProjection(Projections.property("c.uuid"));

        for (Map.Entry<String, Integer> entry : contentVersions.entrySet()) {
            disjunction.add(Restrictions.and(
                Restrictions.eq("c.id", entry.getKey()),
                Restrictions.or(
                    Restrictions.isNull("c.entityVersion"),
                    Restrictions.eq("c.entityVersion", entry.getValue())
                )
            ));
        }

        if (owner != null) {
            uuidCriteria.add(Restrictions.not(Restrictions.eq("oc.owner", owner)));
        }

        List<String> uuids = uuidCriteria.list();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Content.class, null)
                .add(CPRestrictions.in("uuid", uuids));

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

        Map<String, Object> criteria = new HashMap<String, Object>();
        Map<Object, Object> uuidMap = Map.class.cast(contentUuidMap);
        criteria.put("content_uuid", contentUuidMap.keySet());
        criteria.put("owner_id", owner.getId());

        // Owner content
        int count = this.bulkSQLUpdate(OwnerContent.DB_TABLE, "content_uuid", uuidMap, criteria);
        log.info("{} owner-content relations updated", count);

        // Impl note:
        // We're not managing product-content references, since versioning changes require us to
        // handle that with more explicit logic. Instead, we rely on the content manager using
        // the product manager to fork/update products when a related content entity changes.

        // environment content
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_environment WHERE owner_id = ?1")
            .setParameter("1", owner.getId())
            .list();

        if (ids != null && !ids.isEmpty()) {
            criteria.clear();
            criteria.put("environment_id", ids);
            criteria.put("content_uuid", contentUuidMap.keySet());

            count = this.bulkSQLUpdate(EnvironmentContent.DB_TABLE, "content_uuid", uuidMap, criteria);
            log.info("{} environment-content relations updated", count);
        }
        else {
            log.info("0 environment-content relations updated");
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
        // Impl note:
        // As is the case in updateOwnerContentReferences, HQL's bulk delete doesn't allow us to
        // touch anything that even looks like a join. As such, we have to do this in vanilla SQL.

        if (contentUuids != null && !contentUuids.isEmpty()) {
            log.info("Removing owner-content references for owner: {}, {}", owner, contentUuids);

            Session session = this.currentSession();

            // Owner content
            Map<String, Object> criteria = new HashMap<String, Object>();
            criteria.put("owner_id", owner.getId());
            criteria.put("content_uuid", contentUuids);

            int count = this.bulkSQLDelete(OwnerContent.DB_TABLE, criteria);
            log.info("{} owner-content relations removed", count);

            // Impl note:
            // We're not managing product-content references, since versioning changes require us to
            // handle that with more explicit logic. Instead, we rely on the content manager using
            // the product manager to fork/update products when a related content entity changes.

            // environment content
            String sql = "SELECT id FROM " + Environment.DB_TABLE + " WHERE owner_id = ?1";
            List<String> ids = session.createSQLQuery(sql)
                .setParameter("1", owner.getId())
                .list();

            if (ids != null && !ids.isEmpty()) {
                criteria.clear();
                criteria.put("environment_id", ids);
                criteria.put("content_uuid", contentUuids);

                count = this.bulkSQLDelete(EnvironmentContent.DB_TABLE, criteria);
                log.info("{} environment-content relations updated", count);
            }
            else {
                log.info("0 environment-content relations updated");
            }
        }
    }

}

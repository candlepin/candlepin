/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.hibernate.annotations.QueryHints;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
     * Fetches a list containing all content mapped to the organization specified by the specified
     * organization ID. If a given content instance is utilized by an organization but has not been
     * properly mapped via owner-content mapping, it will not be included in the output of this
     * method. If the specified organization ID is null or no matching organization exists, this
     * method returns an empty list.
     *
     * @param ownerId
     *  the ID of the organization for which to fetch all mapped content
     *
     * @return
     *  a list containing all mapped content for the given organization
     */
    public List<Content> getContentByOwner(String ownerId) {
        String jpql = "SELECT oc.content FROM OwnerContent oc WHERE oc.ownerId = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("owner_id", ownerId)
            .getResultList();
    }

    /**
     * Fetches a list containing all content mapped to the specified organization. If a given
     * content instance is utilized by an organization but has not been properly mapped via
     * owner-content mapping, it will not be included in the output of this method. If the
     * specified organization is null or no matching organization exists, this method returns an
     * empty list.
     *
     * @param owner
     *  the organization for which to fetch all mapped content
     *
     * @return
     *  a list containing all mapped content for the given organization
     */
    public List<Content> getContentByOwner(Owner owner) {
        return owner != null ? this.getContentByOwner(owner.getId()) : new ArrayList();
    }

    /**
     * Builds a query for fetching the content currently mapped to the given owner.
     *
     * @deprecated
     *  this method utilizes CandlepinQuery, which itself is backed by deprecated Hibernate APIs,
     *  and should not be used. New code should use the untagged getContentByOwner call instead.
     *
     * @param owner
     *  The owner for which to fetch content
     *
     * @return
     *  a query for fetching the content belonging to the given owner
     */
    @Deprecated
    public CandlepinQuery<Content> getContentByOwnerCPQ(Owner owner) {
        return this.getContentByOwnerCPQ(owner.getId());
    }

    /**
     * Builds a query for fetching the content currently mapped to the given owner.
     *
     * @deprecated
     *  this method utilizes CandlepinQuery, which itself is backed by deprecated Hibernate APIs,
     *  and should not be used. New code should use the untagged getContentByOwner call instead.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch content
     *
     * @return
     *  a query for fetching the content belonging to the given owner
     */
    @Deprecated
    public CandlepinQuery<Content> getContentByOwnerCPQ(String ownerId) {
        // Impl note: See getOwnersByContent for details on why we're doing this in two queries
        Collection<String> uuids = this.getContentUuidsByOwner(ownerId);

        if (!uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Content.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Content>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Content>buildQuery();
    }

    /**
     * Fetches content within the given organization by content ID as a mapping of content ID to
     * content entity. If the organization or specified content cannot be found, this method returns
     * an empty map. If the lookup finds only a subset of the requested content, the map will only
     * contain entries for the existing content.
     *
     * @param ownerId
     *  the ID of the organization in which to lookup content
     *
     * @param contentIds
     *  a collection of content IDs (not UUID) by which to lookup content
     *
     * @return
     *  a mapping of content ID to content entity for matching content in the given organization
     */
    public Map<String, Content> getContentByIds(String ownerId, Collection<String> contentIds) {
        Map<String, Content> output = new HashMap<>();

        if (contentIds != null && !contentIds.isEmpty()) {
            String jpql = "SELECT oc.content FROM OwnerContent oc JOIN oc.content content " +
                "WHERE oc.ownerId = :owner_id AND content.id IN (:content_ids)";

            TypedQuery<Content> query = this.getEntityManager()
                .createQuery(jpql, Content.class)
                .setParameter("owner_id", ownerId);

            for (List<String> block : this.partition(contentIds)) {
                query.setParameter("content_ids", block)
                    .getResultList()
                    .forEach(elem -> output.put(elem.getId(), elem));
            }
        }

        return output;
    }

    /**
     * Fetches content within the given organization by content ID as a mapping of content ID to
     * content entity. If the organization or specified content cannot be found, this method returns
     * an empty map. If the lookup finds only a subset of the requested content, the map will only
     * contain entries for the existing content.
     *
     * @param owner
     *  the owner instance representing the organization in which to lookup content
     *
     * @param contentIds
     *  a collection of content IDs (not UUID) by which to lookup content
     *
     * @return
     *  a mapping of content ID to content entity for matching content in the given organization
     */
    public Map<String, Content> getContentByIds(Owner owner, Collection<String> contentIds) {
        return this.getContentByIds(owner != null ? owner.getId() : null, contentIds);
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
     * Fetches all content having an entity version equal to one of the versions provided.
     *
     * @param versions
     *  A collection of entity versions to use to select contents
     *
     * @return
     *  a map containing the contents found, keyed by content ID
     */
    public Map<String, List<Content>> getContentByVersions(Collection<Long> versions) {
        Map<String, List<Content>> result = new HashMap<>();

        if (versions != null && !versions.isEmpty()) {
            String jpql = "SELECT c FROM Content c WHERE c.entityVersion IN (:vblock)";

            TypedQuery<Content> query = this.getEntityManager()
                .createQuery(jpql, Content.class);

            for (Collection<Long> block : this.partition(versions)) {
                for (Content element : query.setParameter("vblock", block).getResultList()) {
                    result.computeIfAbsent(element.getId(), k -> new LinkedList<>())
                        .add(element);
                }
            }
        }

        return result;
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
        //
        // Because we are using native SQL to do this and the query hint is limiting the table space
        // to OwnerContent all methods that call this need to ensure all new content items have been
        // flushed to the db before this is called.

        if (contentUuidMap == null || contentUuidMap.isEmpty()) {
            // Nothing to update
            return;
        }

        String sql = "UPDATE " + OwnerContent.DB_TABLE + " SET content_uuid = :updated " +
            "WHERE content_uuid = :current AND owner_id = :owner_id";

        Query query = this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", owner.getId())
            .setHint(QueryHints.NATIVE_SPACES, OwnerContent.class.getName());

        int count = 0;
        for (Map.Entry<String, String> entry : contentUuidMap.entrySet()) {
            count += query.setParameter("current", entry.getKey())
                .setParameter("updated", entry.getValue())
                .executeUpdate();
        }

        log.debug("{} owner-content relations updated", count);
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
        if (contentUuids == null || contentUuids.isEmpty()) {
            return;
        }

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
        }

        this.removeEnvironmentContentReferences(owner, contentUuids);
    }

    /**
     * Clears and rebuilds the content mapping for the given owner, using the provided map of
     * content IDs to UUIDs.
     *
     * @param owner
     *  the owner for which to rebuild content mappings
     *
     * @param contentIdMap
     *  a mapping of content IDs to content UUIDs to use as the new content mappings for this
     *  organization. If null or empty, the organization will be left without any content mappings.
     *
     * @throws IllegalArgumentException
     *  if owner is null, or lacks an ID
     */
    public void rebuildOwnerContentMapping(Owner owner, Map<String, String> contentIdMap) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null, or lacks an ID");
        }

        EntityManager entityManager = this.getEntityManager();

        int rcount = entityManager.createQuery("DELETE FROM OwnerContent oc WHERE oc.owner.id = :owner_id")
            .setParameter("owner_id", owner.getId())
            .executeUpdate();

        log.debug("Removed {} owner-content mappings for owner: {}", rcount, owner);

        if (contentIdMap != null) {
            // TODO: content ID isn't part of the owner_content table, but it probably should be. Update
            // this query to also include the content ID in the inserts if the column is added to the table.
            String sql = "INSERT INTO " + OwnerContent.DB_TABLE + " (owner_id, content_uuid) " +
                "VALUES(:owner_id, :content_uuid)";

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setHint(QueryHints.NATIVE_SPACES, OwnerContent.class.getName())
                .setParameter("owner_id", owner.getId());

            int icount = 0;
            for (Map.Entry<String, String> entry : contentIdMap.entrySet()) {
                icount += query.setParameter("content_uuid", entry.getValue())
                    .executeUpdate();
            }

            log.debug("Inserted {} owner-content mappings for owner: {}", icount, owner);
        }
    }

    /**
     * Removes the content references from environments in the given organization. Called as part
     * of the removeOwnerContentReferences operation.
     *
     * @param owner
     *  the owner/organization in which to remove content references from environments
     *
     * @param productUuids
     *  a collection of UUIDs representing content to remove from environments within the org
     */
    private void removeEnvironmentContentReferences(Owner owner, Collection<String> contentUuids) {
        EntityManager entityManager = this.getEntityManager();

        String jpql = "SELECT DISTINCT env.id FROM Environment env WHERE env.ownerId = :owner_id";
        List<String> envIds = entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int count = 0;

        if (envIds != null && !envIds.isEmpty()) {
            Set<String> contentIds = new HashSet<>();

            // Convert the environment UUIDs to environment IDs for cleaning up environments
            jpql = "SELECT content.id FROM Content content WHERE content.uuid IN (:content_uuids)";
            Query query = entityManager.createQuery(jpql, String.class);

            for (List<String> block : this.partition(contentUuids)) {
                query.setParameter("content_uuids", block)
                    .getResultList()
                    .forEach(elem -> contentIds.add((String) elem));
            }

            // Delete the entries
            // Impl note: at the time of writing, JPA doesn't support doing this operation without
            // interacting with the objects directly. So, we're doing it with native SQL to avoid
            // even more work here.
            // Also note that MySQL/MariaDB doesn't like table aliases in a delete statement.
            String sql = "DELETE FROM cp_environment_content " +
                "WHERE environment_id IN (:env_ids) AND content_id IN (:content_ids)";

            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize() / 2);
            Iterable<List<String>> eidBlocks = this.partition(envIds, blockSize);
            Iterable<List<String>> cidBlocks = this.partition(contentIds, blockSize);

            query = entityManager.createNativeQuery(sql)
                .setHint(QueryHints.NATIVE_SPACES, EnvironmentContent.class.getName());

            for (List<String> eidBlock : eidBlocks) {
                query.setParameter("env_ids", eidBlock);

                for (List<String> cidBlock : cidBlocks) {
                    count += query.setParameter("content_ids", cidBlock)
                        .executeUpdate();
                }
            }
        }

        log.info("{} environment content reference(s) removed", count);
    }

    /**
     * Clears the entity version for the given content. Calling this method will not unlink the
     * content from any entities referencing it, but it will prevent further updates from converging
     * on the content.
     *
     * @param entity
     *  the content of which to clear the entity version
     */
    public void clearContentEntityVersion(Content entity) {
        if (entity != null) {
            this.clearContentEntityVersion(entity.getUuid());
        }
    }

    /**
     * Clears the entity version for the content with the given UUID. Calling this method will not
     * unlink the content from any entities referencing it, but it will prevent further updates from
     * converging on the content.
     *
     * @param contentUuid
     *  the UUID of the content of which to clear the entity version
     */
    public void clearContentEntityVersion(String contentUuid) {
        if (contentUuid == null || contentUuid.isEmpty()) {
            return;
        }

        String sql = "UPDATE " + Content.DB_TABLE + " SET entity_version = NULL " +
            "WHERE uuid = :content_uuid";

        this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("content_uuid", contentUuid)
            .setHint(QueryHints.NATIVE_SPACES, Content.class.getName())
            .executeUpdate();
    }

}

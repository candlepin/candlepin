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
import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;



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

    public Collection<Owner> getOwnersByContent(Content content) {
        return this.getOwnersByContent(content.getId());
    }

    @Transactional
    public Collection<Owner> getOwnersByContent(String contentId) {
        return (List<Owner>) this.createSecureCriteria()
            .createAlias("content", "content")
            .setProjection(Projections.property("owner"))
            .add(Restrictions.eq("content.id", contentId))
            .list();
    }

    public Collection<Content> getContentByOwner(Owner owner) {
        return this.getContentByOwner(owner.getId());
    }

    @Transactional
    public Collection<Content> getContentByOwner(String ownerId) {
        return (List<Content>) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .setProjection(Projections.property("content"))
            .add(Restrictions.eq("owner.id", ownerId))
            .list();
    }

    public Collection<Content> getContentByIds(Owner owner, Collection<String> contentIds) {
        return this.getContentByIds(owner.getId(), contentIds);
    }

    @Transactional
    public Collection<Content> getContentByIds(String ownerId, Collection<String> contentIds) {
        Collection<Content> result = null;

        if (contentIds != null && contentIds.size() > 0) {
            Criteria criteria = this.createSecureCriteria()
                .createAlias("owner", "owner")
                .createAlias("content", "content")
                .setProjection(Projections.property("content"))
                .add(Restrictions.eq("owner.id", ownerId))
                .add(this.unboundedInCriterion("content.id", contentIds));

            result = (Collection<Content>) criteria.list();
        }

        return result != null ? result : new LinkedList<Content>();
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
     * @param current
     *  The current content other objects are referencing
     *
     * @param updated
     *  The content other objects should reference
     *
     * @param owners
     *  A collection of owners for which to apply the reference changes
     *
     * @return
     *  a reference to the updated content
     */
    @Transactional
    public Content updateOwnerContentReferences(Content current, Content updated, Collection<Owner> owners) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and HQL refuses to do any joining (implicit or otherwise), which
        // prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        if (owners.size() > AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE) {
            throw new UnsupportedOperationException("Large owner collections are currently unsupported");
        }

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Owner content
        String sql = "UPDATE cp2_owner_content SET content_uuid = ?1 " +
            "WHERE content_uuid = ?2 AND owner_id IN (?3)";

        int ocCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} owner-content relations updated", ocCount);

        // environment content
        sql = "SELECT ec.id " +
            "FROM cp2_environment_content ec " +
            "  JOIN cp_environment e ON ec.environment_id = e.id " +
            "WHERE " +
            "  e.owner_id IN (?1) " +
            "  AND ec.content_uuid = ?2";

        List<String> ids = session.createSQLQuery(sql)
            .setParameterList("1", ownerIds)
            .setParameter("2", current.getUuid())
            .list();

        sql = "UPDATE cp2_environment_content SET content_uuid = ?1 WHERE id IN (?2)";

        int ecCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid());
        log.debug("{} environment-content relations updated", ecCount);

        // Impl note:
        // We're not managing product-content references, since versioning changes require us to
        // handle that with more explicit logic. Instead, when rely on the content manager using
        // the product manager to fork/update products when a related content changes.

        return updated;
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
     * @param content
     *  The content other objects are referencing
     *
     * @param owners
     *  A collection of owners for which to apply the reference changes
     */
    @Transactional
    public void removeOwnerContentReferences(Content content, Collection<Owner> owners) {
        // Impl note:
        // As is the case in updateOwnerContentReferences, HQL's bulk delete doesn't allow us to
        // touch anything that even looks like a join. As such, we have to do this in vanilla SQL.

        if (owners.size() > AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE) {
            throw new UnsupportedOperationException("Large owner collections are currently unsupported");
        }

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Owner content
        String sql = "DELETE FROM cp2_owner_content WHERE content_uuid = ?1 AND owner_id IN (?2)";

        int ocCount = session.createSQLQuery(sql)
            .setParameter("1", content.getUuid())
            .setParameterList("2", ownerIds)
            .executeUpdate();

        log.debug("{} owner-content relations updated", ocCount);

        // environment content
        sql = "SELECT ec.id " +
            "FROM cp2_environment_content ec " +
            "  JOIN cp_environment e ON ec.environment_id = e.id " +
            "WHERE " +
            "  e.owner_id IN (?1) " +
            "  AND ec.content_uuid = ?2";

        List<String> ids = session.createSQLQuery(sql)
            .setParameterList("1", ownerIds)
            .setParameter("2", content.getUuid())
            .list();

        sql = "DELETE FROM cp2_environment_content WHERE id IN (?1)";

        int ecCount = this.safeSQLUpdateWithCollection(sql, ids);
        log.debug("{} environment-content relations updated", ecCount);

        // Impl note:
        // We're not managing product-content references, since versioning changes require us to
        // handle that with more explicit logic. Instead, when rely on the content manager using
        // the product manager to fork/update products when a related content changes.
    }

}

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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



/**
 * ContentCurator
 */
public class ContentCurator extends AbstractHibernateCurator<Content> {

    private static Logger log = LoggerFactory.getLogger(ContentCurator.class);

    private ProductCurator productCurator;

    @Inject
    public ContentCurator(ProductCurator productCurator) {
        super(Content.class);

        this.productCurator = productCurator;
    }

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Content entity) {
        Content toDelete = find(entity.getUuid());
        currentSession().delete(toDelete);
    }

    /**
     * @param owner owner to lookup content for
     * @param id Content ID to lookup. (note: not the database ID)
     * @return the Content which matches the given id.
     */
    @Transactional
    public Content lookupById(Owner owner, String id) {
        return this.lookupById(owner.getId(), id);
    }

    /**
     * @param ownerId The ID of the owner for which to lookup a product
     * @param contentId The ID of the content to lookup. (note: not the database ID)
     * @return the content which matches the given id.
     */
    @Transactional
    public Content lookupById(String ownerId, String contentId) {
        return (Content) this.createSecureCriteria("c")
            .createCriteria("owners", "o")
            .add(Restrictions.eq("o.id", ownerId))
            .add(Restrictions.eq("c.id", contentId))
            .uniqueResult();
    }

    /**
     * Retrieves a Content instance for the specified content UUID. If no matching content could be
     * be found, this method returns null.
     *
     * @param uuid
     *  The UUID of the content to retrieve
     *
     * @return
     *  the Content instance for the content with the specified UUID or null if no matching content
     *  was found.
     */
    @Transactional
    public Content lookupByUuid(String uuid) {
        return (Content) currentSession().createCriteria(Content.class)
            .add(Restrictions.eq("uuid", uuid)).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Content> listByOwner(Owner owner) {
        return currentSession().createCriteria(Content.class)
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", owner.getId()))
            .list();
    }

    /**
     * Retrieves a list of content with the specified Red Hat content ID and upstream last-update
     * timestamp. If no content were found matching the given criteria, this method returns an
     * empty list.
     *
     * @param contentId
     *  The Red Hat content ID
     *
     * @param hashcode
     *  The hash code representing the content version
     *
     * @return
     *  a list of content matching the given content ID and upstream update timestamp
     */
    public List<Content> getContentByVersion(String contentId, int hashcode) {
        return this.listByCriteria(
            this.createSecureCriteria()
                .add(Restrictions.eq("id", contentId))
                .add(Restrictions.or(
                    Restrictions.isNull("entityVersion"),
                    Restrictions.eq("entityVersion", hashcode)
                ))
        );
    }

    /**
     * Updates the content references currently pointing to the original content to instead point to
     * the updated content for the specified owners.
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
    public Content updateOwnerContentReferences(Content current, Content updated,
        Collection<Owner> owners) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and HQL refuses to do any joining (implicit or otherwise), which
        // prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        // TODO:
        // These may end up needing to change if we hit the MySQL 65k-element limitation on IN.

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Owner contents
        String sql = "UPDATE cp2_owner_content SET content_uuid = ?1 " +
            "WHERE content_uuid = ?2 AND owner_id IN (?3)";

        int ocCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} owner-content relations updated", ocCount);

        // environment content
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_environment WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "UPDATE cp2_environment_content SET content_uuid = ?1 " +
            "WHERE content_uuid = ?2 AND environment_id IN (?3)";

        int ecCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} environment-content relations updated", ecCount);

        // product content (probably unnecessary in most cases?)
        ids = session.createSQLQuery("SELECT product_uuid FROM cp2_owner_products WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "UPDATE cp2_product_content SET content_uuid = ?1 " +
            "WHERE content_uuid = ?2 AND product_uuid IN (?3)";

        int pcCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} product-content relations updated", pcCount);

        this.refresh(current);
        this.refresh(updated);

        return updated;
    }

    /**
     * Removes the references to the specified content object from all other objects for the given
     * owner.
     *
     * @param current
     *  The content instance other objects are referencing
     *
     * @param owners
     *  A collection of owners for which to apply the reference removal
     *
     * @return
     *  a reference to the updated content
     */
    public void removeOwnerContentReferences(Content content, Collection<Owner> owners) {
        // Impl note:
        // As is the case in updateOwnerContentReferences, HQL's bulk delete doesn't allow us to
        // touch anything that even looks like a join. As such, we have to do this in vanilla SQL.

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
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_environment WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "DELETE FROM cp2_environment_content WHERE content_uuid = ?1 AND environment_id IN (?2)";

        int ecCount = this.safeSQLUpdateWithCollection(sql, ids, content.getUuid());
        log.debug("{} environment-content relations updated", ecCount);
    }

}

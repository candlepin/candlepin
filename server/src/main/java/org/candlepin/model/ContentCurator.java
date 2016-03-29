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

    @Inject
    private ProductCurator productCurator;


    public ContentCurator() {
        super(Content.class);
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
    protected Content updateOwnerContentReferences(Content current, Content updated,
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
    protected void removeOwnerContentReferences(Content content, Collection<Owner> owners) {
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

    /**
     * Creates a new Content for the given owner, potentially using a different version than the
     * entity provided if a matching entity has already been registered for another owner.
     *
     * @param entity
     *  A Content instance representing the content to create
     *
     * @param owner
     *  The owner for which to create the content
     *
     * @return
     *  a new Content instance representing the specified content for the given owner
     */
    @Transactional
    public Content createContent(Content entity, Owner owner) {
        Content existing = this.lookupById(owner, entity.getId());

        if (existing != null) {
            // If we're doing an exclusive creation, this should be an error condition
            throw new IllegalStateException("Content has already been created");
        }

        // Check if we have an alternate version we can use instead.

        // TODO: Not sure if we really even need the version check. If we have any other matching
        // content, we should probably use it -- regardless of the actual version value.
        List<Content> alternateVersions = this.getContentByVersion(entity.getId(), entity.hashCode());

        for (Content alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a content, we shouldn't have any other object references to
                // update for this content. Instead, we'll just add the new owner to the content.
                alt.addOwner(owner);

                return this.merge(alt);
            }
        }

        entity.addOwner(owner);
        return this.create(entity);
    }

    /**
     * Updates the specified content instance, creating a new version of the content as necessary.
     * The content instance returned by this method is not guaranteed to be the same instance passed
     * in. As such, once this method has been called, callers should only use the instance output by
     * this method.
     *
     * @param entity
     *  The content entity to update
     *
     * @param owner
     *  The owner for which to update the content
     *
     * @return
     *  the updated content entity, or a new content entity
     */
    @Transactional
    public Content updateContent(Content entity, Owner owner) {
        log.debug("Applying content update for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Content existing = this.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        if (existing == entity) {
            // Nothing to do, really. The caller likely intends for the changes to be persisted, so
            // we can do that for them.
            return this.merge(existing);
        }

        // Check for newer versions of the same content. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the content (which matches the version provided by
        // the caller), we can just point the given orgs to the new content instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.

        // TODO:
        // We could just use the current hashcode here with some Hibernate auto-updating magic to
        // determine if two contents are equal rather than relying on an outside value we may never
        // receive

        List<Content> alternateVersions = this.getContentByVersion(entity.getId(), entity.hashCode());

        for (Content alt : alternateVersions) {
            if (alt.equals(entity)) {
                return this.updateOwnerContentReferences(existing, alt, Arrays.asList(owner));
            }
        }

        // Make sure we actually have something to update.
        if (!existing.equals(entity)) {
            // If we're making the update for every owner using the content, don't bother creating
            // a new version -- just do a raw update.
            if (existing.getOwners().size() == 1) {
                // The org receiving the update is the only org using it. We can do an in-place
                // update here.
                existing.merge(entity);
                entity = existing;

                this.merge(entity);
            }
            else {
                List<Owner> owners = Arrays.asList(owner);

                // This org isn't the only org using the content. We need to create a new content
                // instance and move the org over to the new content.
                Content copy = (Content) entity.clone();

                // Clear the UUID so Hibernate doesn't think our copy is a detached entity
                copy.setUuid(null);

                // Get products that currently use this content...
                List<Product> affectedProducts =
                    this.productCurator.getProductsWithContent(owner, Arrays.asList(existing.getId()));

                // Set the owner so when we create it, we don't end up with duplicate keys...
                existing.removeOwner(owner);
                copy.setOwners(owners);

                this.merge(existing);
                copy = this.create(copy);

                // Update the products using this content so they are regenerated using the new
                // content
                for (Product product : affectedProducts) {
                    product = (Product) product.clone();

                    for (ProductContent pc : product.getProductContent()) {
                        if (existing == pc.getContent() || existing.equals(pc.getContent())) {
                            pc.setContent(copy);
                        }
                    }

                    this.productCurator.updateProduct(product, owner);
                }

                entity = this.updateOwnerContentReferences(existing, copy, owners);
            }
        }

        return entity;
    }

    /**
     * Removes the specification
     *
     * @param entity
     *  The content entity to remove
     *
     * @param owner
     *  The owner for which to remove the content
     *
     * @return
     *  a list of products affected by the removal of the given content
     */
    @Transactional
    public List<Product> removeContent(Content entity, Owner owner) {
        log.debug("Removing content for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Content existing = this.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        List<Product> affectedProducts =
            this.productCurator.getProductsWithContent(owner, Arrays.asList(existing.getId()));

        existing.removeOwner(owner);
        if (existing.getOwners().size() == 0) {
            this.delete(existing);
        }

        // Clean up any dangling references to content
        this.removeOwnerContentReferences(existing, Arrays.asList(owner));

        // Update affected products and regenerate their certs
        List<Product> updatedAffectedProducts = new LinkedList<Product>();
        List<Content> contentList = Arrays.asList(existing);

        for (Product product : affectedProducts) {
            product = this.productCurator.removeProductContent(product, contentList, owner);
            updatedAffectedProducts.add(product);
        }

        return updatedAffectedProducts;
    }

}

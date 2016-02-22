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

import org.hibernate.criterion.Restrictions;

import java.util.List;



/**
 * ContentCurator
 */
public class ContentCurator extends AbstractHibernateCurator<Content> {

    public ContentCurator() {
        super(Content.class);
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
        AbstractHibernateCurator.log.debug("Looking up content for owner/cid: {}.{}", ownerId, contentId);

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
            .add(Restrictions.eq("owner", owner)).list();
    }

    @Transactional
    public Content createOrUpdate(Content c) {
        Content existing = this.lookupByUuid(c.getUuid());

        if (existing == null) {
            create(c);
            return c;
        }

        // Copy the ID so Hibernate knows this is an existing entity to merge:
        copy(c, existing);
        return merge(existing);
    }

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Content entity) {
        Content toDelete = find(entity.getUuid());
        currentSession().delete(toDelete);
    }

    public void copy(Content src, Content dest) {
        if (src.getId() == null ? dest.getId() != null :
            !src.getId().equals(dest.getId())) {
            throw new RuntimeException(i18n.tr(
                "Contents do not have matching IDs: {0} != {1}", src.getId(), dest.getId()
            ));
        }

        dest.setName(src.getName());
        dest.setArches(src.getArches());
        dest.setContentUrl(src.getContentUrl());
        dest.setGpgUrl(src.getGpgUrl());
        dest.setLabel(src.getLabel());
        dest.setMetadataExpire(src.getMetadataExpire());
        dest.setModifiedProductIds(src.getModifiedProductIds());
        dest.setType(src.getType());
        dest.setVendor(src.getVendor());
        dest.setRequiredTags(src.getRequiredTags());
        dest.setReleaseVer(src.getReleaseVer());
    }

}

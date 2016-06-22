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
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
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
     * Retrieves a criteria which can be used to fetch a list of content with the specified Red Hat
     * content ID and entity version. If no content were found matching the given criteria, this
     * method returns an empty list.
     *
     * @param contentId
     *  The Red Hat content ID
     *
     * @param hashcode
     *  The hash code representing the content version
     *
     * @return
     *  a criteria for fetching content by version
     */
    public CandlepinCriteria<Content> getContentByVersion(String contentId, int hashcode) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("id", contentId))
            .add(Restrictions.or(
                Restrictions.isNull("entityVersion"),
                Restrictions.eq("entityVersion", hashcode)
            ));

        return new CandlepinCriteria<Content>(criteria, this.currentSession());
    }



}

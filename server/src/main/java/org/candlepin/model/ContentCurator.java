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
        return (Content) currentSession().createCriteria(Content.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("id", id)).uniqueResult();
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
        Content existing = this.lookupById(c.getOwner(), c.getId());

        if (existing == null) {
            create(c);
            return c;
        }

        // Copy the ID so Hibernate knows this is an existing entity to merge:
        return merge(c);
    }
}

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

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



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
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinCriteria<Content> getContentByVersion(String contentId, int hashcode) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("id", contentId))
            .add(Restrictions.or(
                Restrictions.isNull("entityVersion"),
                Restrictions.eq("entityVersion", hashcode)
            ));

        return new StdCandlepinCriteria<Content>(criteria, this.currentSession());
    }



}

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

import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;




/**
 * ContentCurator
 */
@Component
public class ContentCurator extends AbstractHibernateCurator<Content> {

    private static Logger log = LoggerFactory.getLogger(ContentCurator.class);

    private ProductCurator productCurator;

    @Autowired
    public ContentCurator(ProductCurator productCurator) {
        super(Content.class);

        this.productCurator = productCurator;
    }

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Content entity) {
        Content toDelete = this.get(entity.getUuid());
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
    public Content getByUuid(String uuid) {
        return (Content) currentSession().createCriteria(Content.class).setCacheable(true)
            .add(Restrictions.eq("uuid", uuid)).uniqueResult();
    }

    /**
     * Fetches a collection of content used by the given products
     *
     * @param products
     *  The products for which to fetch content
     *
     * @return
     *  A collection of content used by the specified products
     */
    @SuppressWarnings("unchecked")
    public CandlepinQuery<Content> getContentByProducts(Collection<Product> products) {
        if (products != null && !products.isEmpty()) {
            // We're doing this in two queries because (a) that's what Hibernate's doing already due
            // to the projection and (b) DISTINCT_ROOT_ENTITY only works when listing, not when
            // scrolling.
            Session session = this.currentSession();

            List<String> uuids = session.createCriteria(ProductContent.class)
                .add(CPRestrictions.in("product", products))
                .setProjection(Projections.distinct(Projections.property("content.uuid")))
                .list();

            if (uuids != null && !uuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", uuids));

                return this.cpQueryFactory.<Content>buildQuery(session, criteria);
            }
        }

        return this.cpQueryFactory.<Content>buildQuery();
    }
}

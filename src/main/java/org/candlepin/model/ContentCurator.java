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

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.Query;



/**
 * ContentCurator
 */
@Singleton
public class ContentCurator extends AbstractHibernateCurator<Content> {
    private static final Logger log = LoggerFactory.getLogger(ContentCurator.class);

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
     * Performs a bulk deletion of content specified by the given collection of content UUIDs.
     *
     * @param contentUuids
     *  the UUIDs of the content to delete
     *
     * @return
     *  the number of content deleted as a result of this operation
     */
    public int bulkDeleteByUuids(Collection<String> contentUuids) {
        int count = 0;

        if (contentUuids != null && !contentUuids.isEmpty()) {
            Query query = this.getEntityManager()
                .createQuery("DELETE Content c WHERE c.uuid IN (:content_uuids)");

            for (List<String> block : this.partition(contentUuids)) {
                count += query.setParameter("content_uuids", block)
                    .executeUpdate();
            }
        }

        return count;
    }

    /**
     * Fetches a list of content UUIDs representing content which are no longer used by any
     * organization. If no such contents exist, this method returns an empty list.
     * <p></p>
     * <strong>Warning:</strong> Due to the nature of this query, it is highly advised that
     * this it be run within a transaction, with a pessimistic lock held.
     *
     * @return
     *  a list of UUIDs of content no longer used by any organization
     */
    public List<String> getOrphanedContentUuids() {
        String sql = "SELECT c.uuid " +
            "FROM cp2_content c LEFT JOIN cp2_owner_content oc ON c.uuid = oc.content_uuid " +
            "WHERE oc.owner_id IS NULL";

        return this.getEntityManager()
            .createNativeQuery(sql)
            .getResultList();
    }

    /**
     * Returns a mapping of content UUIDs to collections of products referencing them. That is, for
     * a given entry in the returned map, the key will be one of the input content UUIDs, and the
     * value will be the set of product UUIDs which reference it. If no products reference any of
     * the specified contents by UUID, this method returns an empty map.
     *
     * @param contentUuids
     *  a collection content UUIDs for which to fetch referencing products
     *
     * @return
     *  a mapping of content UUIDs to sets of UUIDs of the products referencing them
     */
    public Map<String, Set<String>> getProductsReferencingContent(Collection<String> contentUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (contentUuids != null && !contentUuids.isEmpty()) {
            String jpql = "SELECT pc.content.uuid, prod.uuid FROM Product prod " +
                "JOIN prod.productContent pc " +
                "WHERE pc.content.uuid IN (:content_uuids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(contentUuids)) {
                List<Object[]> rows = query.setParameter("content_uuids", block)
                    .getResultList();

                for (Object[] row : rows) {
                    output.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return output;
    }

    /**
     * Returns a mapping of content UUIDs to collections of environments referencing them. That is,
     * for a given entry in the returned map, the key will be one of the input content UUIDs, and
     * the value will be the set of product UUIDs which reference it. If no environments reference
     * any of the specified contents by UUID, this method returns an empty map.
     *
     * @param contentUuids
     *  a collection content UUIDs for which to fetch referencing environments
     *
     * @return
     *  a mapping of content UUIDs to sets of UUIDs of the environments referencing them
     */
    public Map<String, Set<String>> getEnvironmentsReferencingContent(Collection<String> contentUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (contentUuids != null && !contentUuids.isEmpty()) {
            String jpql = "SELECT ec.content.uuid, env.id FROM Environment env " +
                "JOIN env.environmentContent ec " +
                "WHERE ec.content.uuid IN (:content_uuids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(contentUuids)) {
                List<Object[]> rows = query.setParameter("content_uuids", block)
                    .getResultList();

                for (Object[] row : rows) {
                    output.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return output;
    }

}

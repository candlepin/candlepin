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

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



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

    /**
     * Fetches a content instance with the specified lock for the content with the given ID. If a
     * matching content could not be found, this method returns null. If the specified lock mode is
     * null, no lock will be applied to the query.
     *
     * @param contentId
     *  the ID of the content to retrieve
     *
     * @param lockModeType
     *  the lock mode to apply to the query/entity
     *
     * @return
     *  the content for the given content ID, or null if a matching content was not found
     */
    public Content getContentById(String contentId, LockModeType lockModeType) {
        String jpql = "SELECT content FROM Content content WHERE content.id = :content_id";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("content_id", contentId);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        try {
            return query.getSingleResult();
        }
        catch (NoResultException e) {
            // Intentionally left empty
        }

        return null;
    }

    /**
     * Fetches a content instance for the content with the given ID. If a matching content could not
     * be found, this method returns null.
     *
     * @param contentId
     *  the ID of the content to retrieve
     *
     * @return
     *  the content for the given content ID, or null if a matching content was not found
     */
    public Content getContentById(String contentId) {
        return this.getContentById(contentId, null);
    }

    /**
     * Fetches a map containing content instances for the specified content IDs. If any given ID
     * cannot be resolved to an existing content instance, it will be silently discarded from the
     * output.
     *
     * @param contentIds
     *  a collection of IDs of the content instances to retrieve
     *
     * @return
     *  a map of content instances to the respective content IDs, filtered by the given IDs
     */
    public Map<String, Content> getContentsByIds(Collection<String> contentIds) {
        Map<String, Content> output = new HashMap<>();

        if (contentIds != null && !contentIds.isEmpty()) {
            // Deduplicate input so we don't have to worry about equality shenanigans in the output
            Set<String> input = contentIds instanceof Set ?
                (Set<String>) contentIds :
                (new HashSet<>(contentIds));

            String jpql = "SELECT cont FROM Content cont WHERE cont.id IN (:content_ids)";

            TypedQuery<Content> query = this.getEntityManager()
                .createQuery(jpql, Content.class);

            for (Collection<String> block : this.partition(input)) {
                query.setParameter("content_ids", block)
                    .getResultList()
                    .forEach(elem -> output.put(elem.getId(), elem));
            }
        }

        return output;
    }

    /**
     * Checks if a content exists for the given content ID without fetching the content instance
     * itself. Returns true if one or more contents exist with the given content ID.
     *
     * @param contentId
     *  the ID of the content to lookup
     *
     * @return
     *  true if one or more contents exist with the given content ID; false otherwise
     */
    public boolean contentExistsById(String contentId) {
        String jpql = "SELECT count(content) FROM Content content WHERE content.id = :content_id";

        TypedQuery<Long> query = this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("content_id", contentId);

        return query.getSingleResult() != 0;
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
     * Fetches a set consisting of the content of the  products specified by the given UUIDs. If the
     * given products do not have any content or no products exist with the provided UUIDs, this
     * method returns an empty set.
     *
     * @param productUuids
     *  a collection of UUIDs of products for which to fetch children content
     *
     * @return
     *  a set consisting of the content of the products specified by the given UUIDs
     */
    public Set<Content> getChildrenContentOfProductsByUuids(Collection<String> productUuids) {
        Set<Content> output = new HashSet<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            String jpql = "SELECT pc.content FROM Product p JOIN p.productContent pc " +
                "WHERE p.uuid IN (:product_uuids)";

            TypedQuery<Content> query = this.getEntityManager()
                .createQuery(jpql, Content.class);

            for (List<String> block : this.partition(productUuids)) {
                output.addAll(query.setParameter("product_uuids", block).getResultList());
            }
        }

        return output;
    }

    /**
     * Returns a list of IDs of the products referencing the given content by ID. If no products are
     * referencing the content, or the content is invalid, this method returns an empty list.
     *
     * @param contentId
     *  the ID of the content for which to fetch referencing product IDs
     *
     * @return
     *  a list of product IDs referencing the content specified by ID
     */
    public List<String> getProductIdsReferencingContentById(String contentId) {
        String jpql = "SELECT prod.id FROM Product prod " +
            "JOIN prod.productContent pc " +
            "WHERE pc.content.id = :content_id";

        return this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("content_id", contentId)
            .getResultList();
    }

    /**
     * Checks if the specified content is referenced by any subscriptions as its marketing content.
     * Indirect references to contents, such as provided contents and derived contents, are not
     * considered by this method.
     *
     * @param content
     *  The content to check for product usage
     *
     * @return
     *  true if the content is referenced by one or more products; false otherwise
     */
    public boolean contentIsReferencedByProducts(Content content) {
        if (content == null) {
            return false;
        }

        String jpql = "SELECT count(prod) " +
            "FROM Product prod " +
            "JOIN prod.productContent pcontent " +
            "JOIN pcontent.content content " +
            "WHERE content.uuid = :content_uuid";

        TypedQuery<Long> query = this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("content_uuid", content.getUuid());

        return query.getSingleResult() != 0;
    }
}

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

import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
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
     * Fetches a query builder to use for fetching products by various criteria.
     *
     * @return
     *  a new ContentQueryBuilder instance backed by this curator's data provider
     */
    public ContentQueryBuilder getContentQueryBuilder() {
        return new ContentQueryBuilder(this.entityManager);
    }

    /**
     * Fetches a list of contents with the given content UUIDs. If a given content UUID was not
     * found, it will not be included in the resultant list.If no contents could be found, this
     * method returns an empty list.
     *
     * @param uuids
     *  a collection of content UUIDs to use to fetch contents
     *
     * @return
     *  an unordered list of contents matching the input UUIDs
     */
    public List<Content> getContentsByUuids(Collection<String> uuids) {
        String jpql = "SELECT cont FROM Content cont WHERE cont.uuid IN :content_uuids";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class);

        List<Content> output = new ArrayList<>();

        for (Collection<String> block : this.partition(uuids)) {
            output.addAll(query.setParameter("content_uuids", block)
                .getResultList());
        }

        return output;
    }

    /**
     * Fetches the content referenced by the given ID from the specified namespace. If no content
     * with the given ID exists in the specified namespace, this method returns null.
     *
     * @param namespace
     *  the target namespace from which to fetch the content
     *
     * @param contentId
     *  the ID of the content to fetch
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  the content with the given ID from the specified namespace, or null if the ID does not exist
     *  in the namespace
     */
    public Content getContentById(String namespace, String contentId, LockModeType lockModeType) {
        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        String jpql = "SELECT cont FROM Content cont " +
            "WHERE cont.namespace = :namespace AND cont.id = :content_id";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace)
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
     * Fetches the content referenced by the given ID from the specified namespace. If no content
     * with the given ID exists in the specified namespace, this method returns null.
     *
     * @param namespace
     *  the target namespace from which to fetch the content
     *
     * @param contentId
     *  the ID of the content to fetch
     *
     * @return
     *  the content with the given ID from the specified namespace, or null if the ID does not exist
     *  in the namespace
     */
    public Content getContentById(String namespace, String contentId) {
        return this.getContentById(namespace, contentId, null);
    }

    /**
     * Fetches contents for the given collection of content IDs in the specified namespace. The
     * The contents are returned in a map, keyed by their IDs. Content IDs which were not found will
     * not have an entry in the map. If no contents were found in the specified namespace, this
     * method returns an empty map.
     *
     * @param namespace
     *  the target namespace from which to fetch contents
     *
     * @param contentIds
     *  a collection of IDs of contents to fetch
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a mapping of content IDs to content entities from the given namespace
     */
    public Map<String, Content> getContentsByIds(String namespace, Collection<String> contentIds,
        LockModeType lockModeType) {

        if (contentIds == null) {
            return new HashMap<>();
        }

        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        // Deduplicate and sort input so we don't have to worry about equality or locking
        // shenanigans in the output
        SortedSet<String> input;
        if (!(contentIds instanceof SortedSet)) {
            input = new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
            input.addAll(contentIds);
        }
        else {
            input = (SortedSet<String>) contentIds;
        }

        String jpql = "SELECT cont FROM Content cont " +
            "WHERE cont.namespace = :namespace AND cont.id IN :content_ids " +
            "ORDER BY cont.id ASC";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        Map<String, Content> output = new HashMap<>();

        for (Collection<String> block : this.partition(input)) {
            query.setParameter("content_ids", block)
                .getResultList()
                .forEach(elem -> output.put(elem.getId(), elem));
        }

        return output;
    }

    /**
     * Fetches contents for the given collection of content IDs in the specified namespace. The
     * The contents are returned in a map, keyed by their IDs. Content IDs which were not found will
     * not have an entry in the map. If no contents were found in the specified namespace, this
     * method returns an empty map.
     *
     * @param namespace
     *  the target namespace from which to fetch contents
     *
     * @param contentIds
     *  a collection of IDs of contents to fetch
     *
     * @return
     *  a mapping of content IDs to content entities from the given namespace
     */
    public Map<String, Content> getContentsByIds(String namespace, Collection<String> contentIds) {
        return this.getContentsByIds(namespace, contentIds, null);
    }

    /**
     * Fetches all contents in the given namespace. If the namespace does not exist or does not have
     * any contents, this method returns an empty list.
     *
     * @param namespace
     *  the namespace from which to fetch all known contents
     *
     * @param lockModeType
     *  the lock mode to apply to entities fetched by this query. If null, no locking will be
     *  applied
     *
     * @return
     *  a list of contents present in the specified namespace
     */
    public List<Content> getContentsByNamespace(String namespace, LockModeType lockModeType) {
        String jpql = "SELECT cont FROM Content cont " +
            "WHERE cont.namespace = :namespace " +
            "ORDER BY cont.id ASC";

        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        return query.getResultList();
    }

    /**
     * Fetches all contents in the given namespace. If the namespace does not exist or does not have
     * any contents, this method returns an empty list.
     *
     * @param namespace
     *  the namespace from which to fetch all known contents
     *
     * @return
     *  a list of contents present in the specified namespace
     */
    public List<Content> getContentsByNamespace(String namespace) {
        return this.getContentsByNamespace(namespace, null);
    }

    /**
     * Resolves a content reference by attempting to use the most specific content available for the
     * given namespace; first checking for the content in the namespace, and then checking the
     * global namespace if nothing was found in the specified namespace.
     *
     * @param namespace
     *  the target namespace for which to resolve the content reference
     *
     * @param contentId
     *  the content ID reference to resolve
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  the resolved content for the given namespace, or null if the content reference could not be
     *  resolved
     */
    public Content resolveContentId(String namespace, String contentId, LockModeType lockModeType) {
        String jpql = "SELECT cont FROM Content cont " +
            "WHERE (cont.namespace = '' OR cont.namespace = :namespace) " +
            "AND cont.id = :content_id " +
            "ORDER BY cont.namespace DESC";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace)
            .setParameter("content_id", contentId)
            .setMaxResults(1);

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
     * Resolves a content reference by attempting to use the most specific content available for the
     * given namespace; first checking for the content in the namespace, and then checking the
     * global namespace if nothing was found in the specified namespace.
     *
     * @param namespace
     *  the target namespace for which to resolve the content reference
     *
     * @param contentId
     *  the content ID reference to resolve
     *
     * @return
     *  the resolved content for the given namespace, or null if the content reference could not be
     *  resolved
     */
    public Content resolveContentId(String namespace, String contentId) {
        return this.resolveContentId(namespace, contentId, null);
    }

    /**
     * Resolves the content references by attempting to use the most specified contents available
     * for the given namespace; first checking for the contents in the namespace, and then falling
     * back to the global namespace if a given reference is not found in the specified namespace.
     * The resolved entities are returned in a map, keyed by their IDs. Content IDs which could not
     * be resolved will not have an entry in the map. If no contents could be resolved, this method
     * returns an empty map.
     *
     * @param namespace
     *  the target namespace for which to resolve content references
     *
     * @param contentIds
     *  a collection of content ID references to resolve
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a mapping of content IDs to resolved content entities for the given namespace
     */
    public Map<String, Content> resolveContentIds(String namespace, Collection<String> contentIds,
        LockModeType lockModeType) {

        if (contentIds == null) {
            return new HashMap<>();
        }

        // Deduplicate and sort input so we don't have to worry about equality or locking
        // shenanigans in the output
        SortedSet<String> input;
        if (!(contentIds instanceof SortedSet)) {
            input = new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
            input.addAll(contentIds);
        }
        else {
            input = (SortedSet<String>) contentIds;
        }

        // Impl note:
        // The ORDER BY bit is *very* important here. The order in which the rows are locked is
        // influenced by the query result order, so we need to ensure that our locks are applied
        // deterministically with respect to our input and any query partitioning we do, but we
        // *also* want namespaced entities to come *after* global entities of the same ID so they
        // will overwrite the global entry in the output map later.
        String jpql = "SELECT cont FROM Content cont " +
            "WHERE (cont.namespace = '' OR cont.namespace = :namespace) " +
            "AND cont.id IN :content_ids " +
            "ORDER BY cont.id ASC, cont.namespace ASC";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        Map<String, Content> output = new HashMap<>();
        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() - 1);

        for (Collection<String> block : this.partition(input, blockSize)) {
            query.setParameter("content_ids", block)
                .getResultList()
                .forEach(elem -> output.put(elem.getId(), elem));
        }

        return output;
    }

    /**
     * Resolves the content references by attempting to use the most specified contents available
     * for the given namespace; first checking for the contents in the namespace, and then falling
     * back to the global namespace if a given reference is not found in the specified namespace.
     * The resolved entities are returned in a map, keyed by their IDs. Content IDs which could not
     * be resolved will not have an entry in the map. If no contents could be resolved, this method
     * returns an empty map.
     *
     * @param namespace
     *  the target namespace for which to resolve content references
     *
     * @param contentIds
     *  a collection of content ID references to resolve
     *
     * @return
     *  a mapping of content IDs to resolved content entities for the given namespace
     */
    public Map<String, Content> resolveContentIds(String namespace, Collection<String> contentIds) {
        return this.resolveContentIds(namespace, contentIds, null);
    }

    /**
     * Fetches a collection of resolved content that exist within the specified namespace. The
     * output of this method is effectively a union of the content in the global namespace, and any
     * content in the specified namespace, using content from the specified namespace in the event
     * of a conflict on the content ID. If the global namespace has no content and the specified
     * namespace also has no contents, or is null, empty, or otherwise invalid, this method returns
     * an empty collection.
     *
     * @param namespace
     *  the target namespace for which to fetch resolved content references
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a collection of resolved content references for the given namespace
     */
    public Collection<Content> resolveContentsByNamespace(String namespace, LockModeType lockModeType) {
        Map<String, Content> output = new HashMap<>();

        // Impl note:
        // The ORDER BY bit is *very* important here. The order in which the rows are locked is
        // influenced by the query result order, so we need to ensure that our locks are applied
        // deterministically with respect to our input and any query partitioning we do, but we
        // *also* want namespaced entities to come *after* global entities of the same ID so they
        // will overwrite the global entry in the output map later.
        String jpql = "SELECT cont FROM Content cont " +
            "WHERE (cont.namespace = '' OR cont.namespace = :namespace) " +
            "ORDER BY cont.id ASC, cont.namespace ASC";

        TypedQuery<Content> query = this.getEntityManager()
            .createQuery(jpql, Content.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        query.getResultList()
            .forEach(elem -> output.put(elem.getId(), elem));

        return output.values();
    }

    /**
     * Fetches a collection of resolved content that exist within the specified namespace. The
     * output of this method is effectively a union of the content in the global namespace, and any
     * content in the specified namespace, using content from the specified namespace in the event
     * of a conflict on the content ID. If the global namespace has no content and the specified
     * namespace also has no contents, or is null, empty, or otherwise invalid, this method returns
     * an empty collection.
     *
     * @param namespace
     *  the target namespace for which to fetch resolved content references
     *
     * @return
     *  a collection of resolved content references for the given namespace
     */
    public Collection<Content> resolveContentsByNamespace(String namespace) {
        return this.resolveContentsByNamespace(namespace, null);
    }

    /**
     * Deletes the specified content entity. If the given content is null, lacks a UUID, or does not
     * represent a valid content, this method silently returns.
     *
     * Note that this method first re-fetches the entity, which may trigger some pending updates buffered
     * by the ORM layer.
     *
     * @param entity
     *  a content instance representing the entity to delete
     */
    @Override
    public void delete(Content entity) {
        Content target = Optional.ofNullable(entity)
            .map(Content::getUuid)
            .map(this::get)
            .orElse(null);

        // If our original entity was null, had no UUID, or doesn't map to a known product, silently return
        if (target == null) {
            return;
        }

        this.getEntityManager()
            .remove(target);
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
     * Checks if the specified content is referenced by one or more parent products
     *
     * @param content
     *  the content to check for parent products
     *
     * @return
     *  true if the content is referenced by one or more products; false otherwise
     */
    public boolean contentHasParentProducts(Content content) {
        if (content == null) {
            return false;
        }

        // Impl note: JPA doesn't support EXISTS yet/still, so we'll do the next best thing: select minimal
        // data and limit the query to a single row. If we get any rows, then we have a parent product,
        // otherwise if we get an exception (ugh...), then no such products exist.
        String jpql = "SELECT 1 FROM Product prod " +
            "JOIN prod.productContent pc " +
            "JOIN pc.content cont " +
            "WHERE cont.uuid = :content_uuid";

        try {
            this.getEntityManager()
                .createQuery(jpql)
                .setParameter("content_uuid", content.getUuid())
                .setMaxResults(1)
                .getSingleResult();

            return true;
        }
        catch (NoResultException e) {
            // intentionally left empty
        }

        return false;
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
     * Returns a list of active product content for a given owner
     *
     * Note - If same content (say C1) is being enabled/disabled by two or more products, with at least
     * one of the product enabling the content C1 then, content (C1) with enabled (true)
     * state will have precedence.
     *
     * @param ownerId
     *  Id of an owner
     *
     * @return
     *  Returns list of active product content
     */
    public List<ProductContent> getActiveContentByOwner(String ownerId) {
        EntityManager entityManager = this.getEntityManager();

        // Use CTEs to fetch the graph of active products from which we will pull content

        // Impl note:
        // This query is written primarily as a function of what works best for MySQL and the shape of
        // the data in production. It may make sense to periodically update it as the shape of prod's
        // data changes. Or if/when we update the model to not have two N-tier paths that have to be
        // traversed to fully/correctly build the product graph. :/
        String sql = """
            WITH RECURSIVE pool_products (product_uuid) AS (
                SELECT DISTINCT pool.product_uuid
                    FROM cp_pool pool
                    WHERE pool.owner_id = :owner_id AND now() BETWEEN pool.startdate AND pool.enddate
            ),
            pcmap_anchor (product_uuid, child_uuid) AS (
                SELECT uuid AS product_uuid, derived_product_uuid AS child_uuid
                    FROM cp_products
                    WHERE derived_product_uuid IS NOT NULL
                UNION
                SELECT ppp.product_uuid, ppp.provided_product_uuid AS child_uuid
                    FROM cp_product_provided_products ppp
                    JOIN pool_products pp ON pp.product_uuid = ppp.product_uuid
            ),
            parent_child_map (product_uuid, child_uuid, depth) AS (
                SELECT product_uuid, child_uuid, 1 AS depth FROM pcmap_anchor
                UNION ALL
                SELECT ppp.product_uuid, ppp.provided_product_uuid AS child_uuid, pcmap.depth + 1 AS depth
                    FROM parent_child_map pcmap
                    JOIN cp_product_provided_products ppp ON ppp.product_uuid = pcmap.child_uuid
            ),
            active_products (uuid) AS (
                SELECT product_uuid AS uuid FROM pool_products
                UNION
                SELECT pcmap.child_uuid AS uuid
                    FROM active_products ap
                    JOIN parent_child_map pcmap ON pcmap.product_uuid = ap.uuid
            )
            SELECT DISTINCT pc.content_uuid, pc.enabled
                FROM cp_product_contents pc
                JOIN active_products ap ON ap.uuid = pc.product_uuid
            """;

        Map<String, Boolean> enablementMap = (Map<String, Boolean>) entityManager.createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedEntityClass(ProductContent.class)
            .addSynchronizedQuerySpace("cp_product_provided_products")
            .setParameter("owner_id", ownerId)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(
                row -> (String) ((Object[]) row)[0],  // UUID of the content
                row -> (Boolean) ((Object[]) row)[1], // Enabled state
                (enabled1, enabled2) -> enabled1 || enabled2 // Collision resolution: logical OR
            ));

        List<ProductContent> output = new ArrayList<>();

        String jpql = "SELECT content FROM Content content WHERE content.uuid IN (:content_uuids)";
        TypedQuery<Content> query = entityManager.createQuery(jpql, Content.class);

        // This step is necessary to handle large sets of UUIDs and avoid query parameter limits
        for (List<String> block : this.partition(enablementMap.keySet())) {
            query.setParameter("content_uuids", block)
                .getResultList()
                .forEach(elem -> output.add(new ProductContent(elem, enablementMap.get(elem.getUuid()))));
        }

        return output;
    }

}

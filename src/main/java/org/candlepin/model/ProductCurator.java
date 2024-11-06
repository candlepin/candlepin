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

import org.candlepin.util.AttributeValidator;

import com.google.inject.persist.Transactional;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



/**
 * interact with Products.
 */
@Singleton
public class ProductCurator extends AbstractHibernateCurator<Product> {
    private static final Logger log = LoggerFactory.getLogger(ProductCurator.class);

    private final AttributeValidator attributeValidator;

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(AttributeValidator attributeValidator) {
        super(Product.class);
        this.attributeValidator = Objects.requireNonNull(attributeValidator);
    }

    /**
     * Fetches a query builder to use for fetching products by various criteria.
     *
     * @return
     *  a new ProductQueryBuilder instance backed by this curator's data provider
     */
    public ProductQueryBuilder getProductQueryBuilder() {
        return new ProductQueryBuilder(this.entityManager);
    }

    /**
     * Fetches a list of products with the given product UUIDs. If a given product UUID was not
     * found, it will not be included in the resultant list.If no products could be found, this
     * method returns an empty list.
     *
     * @param uuids
     *  a collection of product UUIDs to use to fetch products
     *
     * @return
     *  an unordered list of products matching the input UUIDs
     */
    public List<Product> getProductsByUuids(Collection<String> uuids) {
        String jpql = "SELECT prod FROM Product prod WHERE prod.uuid IN :product_uuids";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class);

        List<Product> output = new ArrayList<>();

        for (Collection<String> block : this.partition(uuids)) {
            output.addAll(query.setParameter("product_uuids", block)
                .getResultList());
        }

        return output;
    }

    /**
     * Fetches the product referenced by the given ID from the specified namespace. If no product
     * with the given ID exists in the specified namespace, this method returns null.
     *
     * @param namespace
     *  the target namespace from which to fetch the product
     *
     * @param productId
     *  the ID of the product to fetch
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  the product with the given ID from the specified namespace, or null if the ID does not exist
     *  in the namespace
     */
    public Product getProductById(String namespace, String productId, LockModeType lockModeType) {
        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        String jpql = "SELECT prod FROM Product prod " +
            "WHERE prod.namespace = :namespace AND prod.id = :product_id";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace)
            .setParameter("product_id", productId);

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
     * Fetches the product referenced by the given ID from the specified namespace. If no product
     * with the given ID exists in the specified namespace, this method returns null.
     *
     * @param namespace
     *  the target namespace from which to fetch the product
     *
     * @param productId
     *  the ID of the product to fetch
     *
     * @return
     *  the product with the given ID from the specified namespace, or null if the ID does not exist
     *  in the namespace
     */
    public Product getProductById(String namespace, String productId) {
        return this.getProductById(namespace, productId, null);
    }

    /**
     * Fetches products for the given collection of product IDs in the specified namespace. The
     * The products are returned in a map, keyed by their IDs. Product IDs which were not found will
     * not have an entry in the map. If no products were found in the specified namespace, this
     * method returns an empty map.
     *
     * @param namespace
     *  the target namespace from which to fetch products
     *
     * @param productIds
     *  a collection of IDs of products to fetch
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a mapping of product IDs to product entities from the given namespace
     */
    public Map<String, Product> getProductsByIds(String namespace, Collection<String> productIds,
        LockModeType lockModeType) {

        if (productIds == null) {
            return new HashMap<>();
        }

        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        // Deduplicate and sort input so we don't have to worry about equality or locking
        // shenanigans in the output
        SortedSet<String> input;
        if (!(productIds instanceof SortedSet)) {
            input = new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
            input.addAll(productIds);
        }
        else {
            input = (SortedSet<String>) productIds;
        }

        String jpql = "SELECT prod FROM Product prod " +
            "WHERE prod.namespace = :namespace AND prod.id IN :product_ids " +
            "ORDER BY prod.id ASC";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        Map<String, Product> output = new HashMap<>();

        for (Collection<String> block : this.partition(input)) {
            query.setParameter("product_ids", block)
                .getResultList()
                .forEach(elem -> output.put(elem.getId(), elem));
        }

        return output;
    }

    /**
     * Fetches products for the given collection of product IDs in the specified namespace. The
     * The products are returned in a map, keyed by their IDs. Product IDs which were not found will
     * not have an entry in the map. If no products were found in the specified namespace, this
     * method returns an empty map.
     *
     * @param namespace
     *  the target namespace from which to fetch products
     *
     * @param productIds
     *  a collection of IDs of products to fetch
     *
     * @return
     *  a mapping of product IDs to product entities from the given namespace
     */
    public Map<String, Product> getProductsByIds(String namespace, Collection<String> productIds) {
        return this.getProductsByIds(namespace, productIds, null);
    }

    /**
     * Fetches all products in the given namespace. If the namespace does not exist or does not have
     * any products, this method returns an empty list.
     *
     * @param namespace
     *  the namespace from which to fetch all known products
     *
     * @param lockModeType
     *  the lock mode to apply to entities fetched by this query. If null, no locking will be
     *  applied
     *
     * @return
     *  a list of products present in the specified namespace
     */
    public List<Product> getProductsByNamespace(String namespace, LockModeType lockModeType) {
        String jpql = "SELECT prod FROM Product prod " +
            "WHERE prod.namespace = :namespace " +
            "ORDER BY prod.id ASC";

        // Impl note: The global/null namespace is stored as an empty string
        if (namespace == null) {
            namespace = "";
        }

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        return query.getResultList();
    }

    /**
     * Fetches all products in the given namespace. If the namespace does not exist or does not have
     * any products, this method returns an empty list.
     *
     * @param namespace
     *  the namespace from which to fetch all known products
     *
     * @return
     *  a list of products present in the specified namespace
     */
    public List<Product> getProductsByNamespace(String namespace) {
        return this.getProductsByNamespace(namespace, null);
    }

    /**
     * Resolves a product reference by attempting to use the most specific product available for the
     * given namespace; first checking for the product in the namespace, and then checking the
     * global namespace if nothing was found in the specified namespace.
     *
     * @param namespace
     *  the target namespace for which to resolve the product reference
     *
     * @param productId
     *  the product ID reference to resolve
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  the resolved product for the given namespace, or null if the product reference could not be
     *  resolved
     */
    public Product resolveProductId(String namespace, String productId, LockModeType lockModeType) {
        String jpql = "SELECT prod FROM Product prod " +
            "WHERE (prod.namespace = '' OR prod.namespace = :namespace) " +
            "AND prod.id = :product_id " +
            "ORDER BY prod.namespace DESC";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace)
            .setParameter("product_id", productId)
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
     * Resolves a product reference by attempting to use the most specific product available for the
     * given namespace; first checking for the product in the namespace, and then checking the
     * global namespace if nothing was found in the specified namespace.
     *
     * @param namespace
     *  the target namespace for which to resolve the product reference
     *
     * @param productId
     *  the product ID reference to resolve
     *
     * @return
     *  the resolved product for the given namespace, or null if the product reference could not be
     *  resolved
     */
    public Product resolveProductId(String namespace, String productId) {
        return this.resolveProductId(namespace, productId, null);
    }

    /**
     * Resolves the product references by attempting to use the most specified products available
     * for the given namespace; first checking for the products in the namespace, and then falling
     * back to the global namespace if a given reference is not found in the specified namespace.
     * The resolved entities are returned in a map, keyed by their IDs. Product IDs which could not
     * be resolved will not have an entry in the map. If no products could be resolved, this method
     * returns an empty map.
     *
     * @param namespace
     *  the target namespace for which to resolve product references
     *
     * @param productIds
     *  a collection of product ID references to resolve
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a mapping of product IDs to resolved product entities for the given namespace
     */
    public Map<String, Product> resolveProductIds(String namespace, Collection<String> productIds,
        LockModeType lockModeType) {

        if (productIds == null) {
            return new HashMap<>();
        }

        // Deduplicate and sort input so we don't have to worry about equality or locking
        // shenanigans in the output
        SortedSet<String> input;
        if (!(productIds instanceof SortedSet)) {
            input = new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
            input.addAll(productIds);
        }
        else {
            input = (SortedSet<String>) productIds;
        }

        // Impl note:
        // The ORDER BY bit is *very* important here. The order in which the rows are locked is
        // influenced by the query result order, so we need to ensure that our locks are applied
        // deterministically with respect to our input and any query partitioning we do, but we
        // *also* want namespaced entities to come *after* global entities of the same ID so they
        // will overwrite the global entry in the output map later.
        String jpql = "SELECT prod FROM Product prod " +
            "WHERE (prod.namespace = '' OR prod.namespace = :namespace) " +
            "AND prod.id IN :product_ids " +
            "ORDER BY prod.id ASC, prod.namespace ASC";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        Map<String, Product> output = new HashMap<>();
        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() - 1);

        for (Collection<String> block : this.partition(input, blockSize)) {
            query.setParameter("product_ids", block)
                .getResultList()
                .forEach(elem -> output.put(elem.getId(), elem));
        }

        return output;
    }

    /**
     * Resolves the product references by attempting to use the most specified products available
     * for the given namespace; first checking for the products in the namespace, and then falling
     * back to the global namespace if a given reference is not found in the specified namespace.
     * The resolved entities are returned in a map, keyed by their IDs. Product IDs which could not
     * be resolved will not have an entry in the map. If no products could be resolved, this method
     * returns an empty map.
     *
     * @param namespace
     *  the target namespace for which to resolve product references
     *
     * @param productIds
     *  a collection of product ID references to resolve
     *
     * @return
     *  a mapping of product IDs to resolved product entities for the given namespace
     */
    public Map<String, Product> resolveProductIds(String namespace, Collection<String> productIds) {
        return this.resolveProductIds(namespace, productIds, null);
    }

    /**
     * Fetches a collection of resolved products that exist within the specified namespace. The
     * output of this method is effectively a union of the products in the global namespace, and any
     * products in the specified namespace, using products from the specified namespace in the event
     * of a conflict on the product ID. If the global namespace has no products and the specified
     * namespace also has no products, or is null, empty, or otherwise invalid, this method returns
     * an empty collection.
     *
     * @param namespace
     *  the target namespace for which to fetch resolved product references
     *
     * @param lockModeType
     *  an optional lock mode to apply to entities fetched by this query. If null, no locking will
     *  be applied
     *
     * @return
     *  a collection of resolved product references for the given namespace
     */
    public Collection<Product> resolveProductsByNamespace(String namespace, LockModeType lockModeType) {
        Map<String, Product> output = new HashMap<>();

        // Impl note:
        // The ORDER BY bit is *very* important here. The order in which the rows are locked is
        // influenced by the query result order, so we need to ensure that our locks are applied
        // deterministically with respect to our input and any query partitioning we do, but we
        // *also* want namespaced entities to come *after* global entities of the same ID so they
        // will overwrite the global entry in the output map later.
        String jpql = "SELECT prod FROM Product prod " +
            "WHERE (prod.namespace = '' OR prod.namespace = :namespace) " +
            "ORDER BY prod.id ASC, prod.namespace ASC";

        TypedQuery<Product> query = this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("namespace", namespace);

        if (lockModeType != null) {
            query.setLockMode(lockModeType);
        }

        query.getResultList()
            .forEach(elem -> output.put(elem.getId(), elem));

        return output.values();
    }

    /**
     * Fetches a collection of resolved products that exist within the specified namespace. The
     * output of this method is effectively a union of the products in the global namespace, and any
     * products in the specified namespace, using products from the specified namespace in the event
     * of a conflict on the product ID. If the global namespace has no products and the specified
     * namespace also has no products, or is null, empty, or otherwise invalid, this method returns
     * an empty collection.
     *
     * @param namespace
     *  the target namespace for which to fetch resolved product references
     *
     * @return
     *  a collection of resolved product references for the given namespace
     */
    public Collection<Product> resolveProductsByNamespace(String namespace) {
        return this.resolveProductsByNamespace(namespace, null);
    }

    /**
     * Fetches a set consisting of the children products (derived and provided products) of the
     * products specified by the given UUIDs. If the given products do not have any children
     * products or no products exist with the provided UUIDs, this method returns an empty set.
     *
     * @param productUuids
     *  a collection of UUIDs of products for which to fetch children products
     *
     * @return
     *  a set consisting of the children products of the products specified by the given UUIDs
     */
    public Set<Product> getChildrenProductsOfProductsByUuids(Collection<String> productUuids) {
        Set<Product> output = new HashSet<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            String ppJpql = "SELECT pp FROM Product p JOIN p.providedProducts pp " +
                "WHERE p.uuid IN (:product_uuids)";
            String dpJpql = "SELECT p.derivedProduct FROM Product p " +
                "WHERE p.derivedProduct IS NOT NULL AND p.uuid IN (:product_uuids)";

            TypedQuery<Product> ppQuery = this.getEntityManager()
                .createQuery(ppJpql, Product.class);

            TypedQuery<Product> dpQuery = this.getEntityManager()
                .createQuery(dpJpql, Product.class);

            for (List<String> block : this.partition(productUuids)) {
                output.addAll(ppQuery.setParameter("product_uuids", block).getResultList());
                output.addAll(dpQuery.setParameter("product_uuids", block).getResultList());
            }
        }

        return output;
    }

    /**
     * Validates and corrects the object references maintained by the given product instance.
     *
     * @param entity
     *  The product entity to validate
     *
     * @return
     *  The provided product reference
     */
    protected Product validateProductReferences(Product entity) {
        for (Map.Entry<String, String> entry : entity.getAttributes().entrySet()) {
            this.attributeValidator.validate(entry.getKey(), entry.getValue());
        }

        // TODO: Add more reference checks here.

        return entity;
    }

    @Transactional
    public Product create(Product entity) {
        log.debug("Persisting new product entity: {}", entity);

        this.validateProductReferences(entity);

        Product newProduct = super.create(entity, false);

        for (ProductContent productContent : entity.getProductContent()) {
            if (productContent.getId() == null) {
                this.getEntityManager().persist(productContent);
            }
        }

        return newProduct;
    }

    @Transactional
    public Product merge(Product entity) {
        log.debug("Merging product entity: {}", entity);

        this.validateProductReferences(entity);

        return super.merge(entity);
    }

    /**
     * Deletes the specified product entity. If the given product is null, lacks a UUID, or does not
     * represent a valid product, this method silently returns.
     *
     * Note that this method first re-fetches the entity, which may trigger some pending updates buffered
     * by the ORM layer.
     *
     * @param entity
     *  a product instance representing the entity to delete
     */
    @Override
    @Transactional
    public void delete(Product entity) {
        Product target = Optional.ofNullable(entity)
            .map(Product::getUuid)
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
     * Performs a bulk deletion of products specified by the given collection of product UUIDs
     *
     * @param productUuids
     *  the UUIDs of the products to delete
     *
     * @return
     *  the number of products deleted as a result of this operation
     */
    public int bulkDeleteByUuids(Collection<String> productUuids) {
        int count = 0;

        if (productUuids != null && !productUuids.isEmpty()) {
            Query query = this.getEntityManager()
                .createQuery("DELETE Product p WHERE p.uuid IN (:product_uuids)");

            for (List<String> block : this.partition(productUuids)) {
                count += query.setParameter("product_uuids", block)
                    .executeUpdate();
            }
        }

        return count;
    }

    /**
     * Checks if the specified product is referenced by any subscriptions as its marketing product (SKU).
     * Indirect references to products, such as provided products and derived products, are not considered
     * by this method.
     *
     * @param product
     *  The product to check for subscriptions
     *
     * @return
     *  true if the product is referenced by one or more pools; false otherwise
     */
    public boolean productHasParentSubscriptions(Product product) {
        if (product == null) {
            return false;
        }

        // Impl note: JPA doesn't support EXISTS yet/still, so we'll do the next best thing: select minimal
        // data and limit the query to a single row. If we get any rows, then we have a parent subscription,
        // otherwise if we get an exception (ugh...), then no such subscription exist.
        String jpql = "SELECT 1 FROM Pool pool " +
            "WHERE pool.product.uuid = :product_uuid";

        try {
            this.getEntityManager()
                .createQuery(jpql)
                .setParameter("product_uuid", product.getUuid())
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
     * Checks if the specified product is referenced by any other products as a derived or provided
     * product.
     *
     * @param product
     *  the product to check for parent products
     *
     * @return
     *  true if the product is referenced by one or more other products; false otherwise
     */
    public boolean productHasParentProducts(Product product) {
        if (product == null) {
            return false;
        }

        // Impl note: JPA doesn't support EXISTS yet/still, so we'll do the next best thing: select minimal
        // data and limit the query to a single row. If we get any rows, then we have a parent product,
        // otherwise if we get an exception (ugh...), then no such products exist.
        String jpql = "SELECT 1 FROM Product prod " +
            "LEFT JOIN prod.providedProducts pp " +
            "WHERE prod.derivedProduct.uuid = :product_uuid OR pp.uuid = :product_uuid";

        try {
            this.getEntityManager()
                .createQuery(jpql)
                .setParameter("product_uuid", product.getUuid())
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
     * Returns a mapping of product UUIDs to collections of pools referencing them. That is, for
     * a given entry in the returned map, the key will be one of the input product UUIDs, and the
     * value will be the set of pool IDs which reference it. If no pools reference any of the
     * specified products by UUID, this method returns an empty map.
     *
     * @param productUuids
     *  a collection product UUIDs for which to fetch referencing pools
     *
     * @return
     *  a mapping of product UUIDs to sets of IDs of the pools referencing them
     */
    public Map<String, Set<String>> getPoolsReferencingProducts(Collection<String> productUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            String jpql = "SELECT p.product.uuid, p.id FROM Pool p " +
                "WHERE p.product.uuid IN (:product_uuids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(productUuids)) {
                List<Object[]> rows = query.setParameter("product_uuids", block)
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
     * Returns a mapping of product UUIDs to collections of products referencing them. That is, for
     * a given entry in the returned map, the key will be one of the input product UUIDs, and the
     * value will be the set of product UUIDs which reference it. If no products reference any of
     * the specified products by UUID, this method returns an empty map.
     *
     * @param productUuids
     *  a collection product UUIDs for which to fetch referencing products
     *
     * @return
     *  a mapping of product UUIDs to sets of UUIDs of the products referencing them
     */
    public Map<String, Set<String>> getProductsReferencingProducts(Collection<String> productUuids) {
        Map<String, Set<String>> output = new HashMap<>();

        if (productUuids != null && !productUuids.isEmpty()) {
            // Impl note:
            // We're using native SQL here as we're needing to use a union to target both fields on
            // the product in a single query.
            String sql = "SELECT p.derived_product_uuid, p.uuid FROM cp_products p " +
                "WHERE p.derived_product_uuid IN (:product_uuids) " +
                "UNION " +
                "SELECT pp.provided_product_uuid, pp.product_uuid FROM cp_product_provided_products pp " +
                "WHERE pp.provided_product_uuid IN (:product_uuids)";

            // The block has to be included twice, so ensure we don't exceed the parameter limit
            // with large blocks
            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize());

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedQuerySpace("cp_product_provided_products");

            for (List<String> block : this.partition(productUuids, blockSize)) {
                List<Object[]> rows = query.setParameter("product_uuids", block)
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
     * Fetches a list of products within the given organization which directly reference the
     * specified content. Indirect references, such as a product which has a derived product that
     * provides the specified content, are not included in the collection returned by this method.
     *
     * @param contentUuid
     *  the UUID of the content for which to fetch product references
     *
     * @return
     *  a collection of products directly referencing the specified content
     */
    public List<Product> getProductsReferencingContent(String contentUuid) {
        String jpql = "SELECT prod FROM Product prod " +
            "JOIN prod.productContent pc " +
            "JOIN pc.content cont " +
            "WHERE cont.uuid = :content_uuid";

        return this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("content_uuid", contentUuid)
            .getResultList();
    }

}

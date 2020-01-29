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

import org.candlepin.common.config.Configuration;
import org.candlepin.util.AttributeValidator;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.Cache;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



/**
 * interact with Products.
 */
@Singleton
public class ProductCurator extends AbstractHibernateCurator<Product> {
    private static Logger log = LoggerFactory.getLogger(ProductCurator.class);

    private Configuration config;
    private AttributeValidator attributeValidator;

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(Configuration config,
        AttributeValidator attributeValidator) {

        super(Product.class);

        this.config = config;
        this.attributeValidator = attributeValidator;
    }

    /**
     * Retrieves a Product instance for the product with the specified name. If a matching product
     * could not be found, this method returns null.
     *
     * @param owner
     *  The owner/org in which to search for a product
     *
     * @param name
     *  The name of the product to retrieve
     *
     * @return
     *  a Product instance for the product with the specified name, or null if a matching product
     *  was not found.
     */
    public Product getByName(Owner owner, String name) {
        return (Product) this.createSecureCriteria(OwnerProduct.class, null)
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", owner.getId()))
            .add(Restrictions.eq("product.name", name))
            .uniqueResult();
    }

    public CandlepinQuery<Product> listAllByUuids(Collection<? extends Serializable> uuids) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(CPRestrictions.in("uuid", uuids));

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
    }

    public Set<Product> getPoolDerivedProvidedProductsCached(Pool pool) {
        return getPoolDerivedProvidedProductsCached(pool.getId());
    }

    public Set<Product> getPoolDerivedProvidedProductsCached(String poolId) {
        Set<String> uuids = getDerivedPoolProvidedProductUuids(poolId);
        return getProductsByUuidCached(uuids);
    }

    public Set<Product> getPoolProvidedProductsCached(Pool pool) {
        return getPoolProvidedProductsCached(pool.getId());
    }

    public Set<Product> getPoolProvidedProductsCached(String poolId) {
        Set<String> providedUuids = getPoolProvidedProductUuids(poolId);
        return getProductsByUuidCached(providedUuids);
    }

    /**
     * Finds all provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getPoolProvidedProductUuids(String poolId) {
        TypedQuery<String> query = getEntityManager().createQuery(
            "SELECT product.uuid FROM Pool p INNER JOIN p.product.providedProducts product " +
            "where p.id = :poolid",
            String.class);
        query.setParameter("poolid", poolId);
        return new HashSet<>(query.getResultList());
    }

    /**
     * Finds all derived provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getDerivedPoolProvidedProductUuids(String poolId) {
        TypedQuery<String> query = getEntityManager().createQuery(
            "SELECT product.uuid FROM Pool p INNER JOIN p.derivedProduct.providedProducts product " +
            "WHERE p.id = :poolid",
            String.class);
        query.setParameter("poolid", poolId);
        return new HashSet<>(query.getResultList());
    }

    /**
     * Gets products by Id from JCache or database
     *
     * The retrieved objects are fully hydrated. If an entity is not present in the cache,
     * then it is retrieved them from the database and is fully hydrated
     *
     * @param productUuids
     * @return Fully hydrated Product objects
     */
    public Set<Product> getProductsByUuidCached(Set<String> productUuids) {
        if (productUuids.size() == 0) {
            return new HashSet<>();
        }

        // Determine what is already in the L2 cache and load it directly. Multiload the remainder.
        // This is because of https://hibernate.atlassian.net/browse/HHH-12944 where multiload ignores the
        // L2 Cache.
        Set<Product> products = new HashSet<>();
        Set<String> productsNotInCache = new HashSet<>();
        Cache cache = currentSession().getSessionFactory().getCache();
        for (String uuid : productUuids) {
            if (cache.contains(this.entityType(), uuid)) {
                products.add(currentSession().get(Product.class, uuid));
            }
            else {
                productsNotInCache.add(uuid);
            }
        }
        if (productsNotInCache.size() > 0) {
            log.debug("Loading objects that were not already in the cache: " + productsNotInCache.size());
            Session session = this.currentSession();
            java.util.List entities = session.byMultipleIds(this.entityType())
                .enableSessionCheck(true)
                .multiLoad(productsNotInCache.toArray(new String[productsNotInCache.size()]));
            products.addAll(entities);
        }

        // Hydrate all the objects fully this is because a lot of serialization happens outside of
        // the transactional boundry when we do not have a valid session.
        for (Product product : products) {
            // Fetching the size on these collections triggers a lazy load of the collections
            product.getAttributes().size();
            product.getDependentProductIds().size();
            for (ProductContent pc : product.getProductContent()) {
                pc.getContent().getModifiedProductIds().size();
            }
        }

        return products;
    }

    /**
     * Loads the set of products from database and triggers all lazy loads.
     * @param uuids
     * @return Map of UUID to Product instance
     */
    public Map<String, Product> getHydratedProductsByUuid(Set<String> uuids) {
        Map<String, Product> productsByUuid = new HashMap<>();

        if (uuids != null && !uuids.isEmpty()) {
            for (Product product : this.listAllByUuids(uuids)) {
                // Fetching the size on these collections triggers a lazy load of the collections
                product.getAttributes().size();
                product.getDependentProductIds().size();
                for (ProductContent pc : product.getProductContent()) {
                    pc.getContent().getModifiedProductIds().size();
                }

                productsByUuid.put(product.getUuid(), product);
            }
        }

        return productsByUuid;
    }

    /**
     * Fetches the provided and derived provided products for the specified pools, populating the
     * respective collections in each pool object. The products will be pulled from the product
     * cache where possible, and the cache will be hydrated with products that must be pulled from
     * the database.
     *
     * @param pools
     *  A collection of pools for which to fetch provided and derived provided products
     *
     * @return
     *  the number of products cached as a result of this operation
     */
    public int hydratePoolProvidedProducts(Iterable<Pool> pools) {
        int count = 0;
        if (pools != null && pools.iterator().hasNext()) {
            EntityManager entityManager = this.getEntityManager();

            // We use these set for both product UUIDs and product instances, once populated
            Map<String, Set<String>> poolProvidedProducts = new HashMap<>();
            Map<String, Set<String>> poolDerivedProvidedProducts = new HashMap<>();
            Map<String, Product> allProducts = new HashMap<>();

            for (Pool pool : pools) {
                poolProvidedProducts.put(pool.getId(), new HashSet<>());
                poolDerivedProvidedProducts.put(pool.getId(), new HashSet<>());
            }

            String ppSql =
                "SELECT po.id, pp.provided_product_uuid FROM cp2_product_provided_products pp " +
                "JOIN cp_pool po on po.product_uuid = pp.product_uuid " +
                "WHERE po.id IN (:poolIds)";

            String dpSql =
                "SELECT po.id, pp.provided_product_uuid FROM cp2_product_provided_products pp " +
                "JOIN cp_pool po on po.derived_product_uuid = pp.product_uuid " +
                "WHERE po.id IN (:poolIds)";

            Query ppUuidQuery = entityManager.createNativeQuery(ppSql);
            Query dpUuidQuery = entityManager.createNativeQuery(dpSql);

            int blockSize = this.getInBlockSize();
            for (List<String> block : Iterables.partition(poolProvidedProducts.keySet(), blockSize)) {
                // Fetch pool provided products...
                ppUuidQuery.setParameter("poolIds", block);

                for (Object[] row : (List<Object[]>) ppUuidQuery.getResultList()) {
                    String poolId = (String) row[0];
                    String productUuid = (String) row[1];
                    Set<String> ppSet = poolProvidedProducts.get(poolId);
                    ppSet.add(productUuid);
                    allProducts.put(productUuid, null);
                }

                dpUuidQuery.setParameter("poolIds", block);

                for (Object[] row : (List<Object[]>) dpUuidQuery.getResultList()) {
                    String poolId = (String) row[0];
                    String productUuid = (String) row[1];
                    Set<String> dpSet = poolDerivedProvidedProducts.get(poolId);
                    dpSet.add(productUuid);
                    allProducts.put(productUuid, null);
                }
            }

            // Now go get all the products and hydrate them
            Set<Product> hydratedProducts = getProductsByUuidCached(allProducts.keySet());
            for (Product product : hydratedProducts) {
                allProducts.put(product.getUuid(), product);
            }

            // Use the UUID sets for each pools provided & derived provided products
            // to populate the actual product models.
            for (Pool pool : pools) {
                Set<Product> providedProducts = new HashSet<>();
                for (String uuid : poolProvidedProducts.get(pool.getId())) {
                    providedProducts.add(allProducts.get(uuid));
                }

                Set<Product> derivedProvidedProducts = new HashSet<>();
                for (String uuid : poolDerivedProvidedProducts.get(pool.getId())) {
                    derivedProvidedProducts.add(allProducts.get(uuid));
                }

                pool.getProduct().setProvidedProducts(providedProducts);

                if (pool.getDerivedProduct() != null) {
                    pool.getDerivedProduct().setProvidedProducts(derivedProvidedProducts);
                }
            }
        }

        return count;
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

        if (entity.getProductContent() != null) {
            for (ProductContent pc : entity.getProductContent()) {
                if (pc.getContent() == null) {
                    throw new IllegalStateException(
                        "Product contains a ProductContent with a null content reference");
                }

                pc.setProduct(entity);
            }
        }

        if (entity.getBranding() != null) {
            for (Branding brand : entity.getBranding()) {
                if (brand.getProductId() == null ||
                    brand.getName() == null ||
                    brand.getType() == null) {
                    throw new IllegalStateException(
                        "Product contains a Branding with a null product id, name or type.");
                }

                brand.setProduct(entity);
            }
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
                this.currentSession().save(productContent);
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

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Product entity) {
        Product toDelete = this.get(entity.getUuid());
        currentSession().delete(toDelete);
    }

    /**
     * Checks if any of the provided product is linked to one or more pools for the given owner.
     *
     * @param owner
     *  The owner to use for finding pools/subscriptions
     *
     * @param product
     *  The product to check for subscriptions
     *
     * @return
     *  true if the product is linked to one or more subscriptions; false otherwise.
     */
    public boolean productHasSubscriptions(Owner owner, Product product) {
        return ((Long) currentSession().createCriteria(Pool.class, "Pool")
            .createAlias("Pool.product", "Product")
            .createAlias("Pool.derivedProduct", "DProduct")
            .createAlias("Product.providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("DProduct.providedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.or(
                Restrictions.eq("product.uuid", product.getUuid()),
                Restrictions.eq("derivedProduct.uuid", product.getUuid()),
                Restrictions.eq("providedProd.uuid", product.getUuid()),
                Restrictions.eq("derivedProvidedProd.uuid", product.getUuid())))
            .setProjection(Projections.count("id"))
            .uniqueResult()) > 0;
    }

    public CandlepinQuery<Product> getProductsByContent(Owner owner, Collection<String> contentIds) {
        return this.getProductsByContent(owner, contentIds, null);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContent(Owner owner, Collection<String> contentIds,
        Collection<String> productsToOmit) {
        if (owner != null && contentIds != null && !contentIds.isEmpty()) {
            // Impl note:
            // We have to break this up into two queries for proper cursor and pagination support.
            // Hibernate currently has two nasty "features" which break these in their own special
            // way:
            // - Distinct, when applied in any way outside of direct SQL, happens in Hibernate
            //   *after* the results are pulled down, if and only if the results are fetched as a
            //   list. The filtering does not happen when the results are fetched with a cursor.
            // - Because result limiting (first+last result specifications) happens at the query
            //   level and distinct filtering does not, cursor-based pagination breaks due to
            //   potential results being removed after a page of results is fetched.
            Criteria idCriteria = this.createSecureCriteria(OwnerProduct.class, null)
                .createAlias("product", "product")
                .createAlias("product.productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .createAlias("owner", "owner")
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(CPRestrictions.in("content.id", contentIds))
                .setProjection(Projections.distinct(Projections.property("product.uuid")));

            if (productsToOmit != null && !productsToOmit.isEmpty()) {
                idCriteria.add(Restrictions.not(CPRestrictions.in("product.id", productsToOmit)));
            }

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContentUuids(Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            // See note above in getProductsByContent for details on why we do two queries here
            // instead of one.
            Criteria idCriteria = this.createSecureCriteria()
                .createAlias("productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .add(CPRestrictions.in("content.uuid", contentUuids))
                .setProjection(Projections.distinct(Projections.id()));

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsByContentUuids(Owner owner, Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            // See note above in getProductsByContent for details on why we do two queries here
            // instead of one.
            Criteria idCriteria = this.createSecureCriteria(OwnerProduct.class, null)
                .createAlias("product", "product")
                .createAlias("product.productContent", "pcontent")
                .createAlias("pcontent.content", "content")
                .createAlias("owner", "owner")
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(CPRestrictions.in("content.uuid", contentUuids))
                .setProjection(Projections.distinct(Projections.property("product.uuid")));

            List<String> productUuids = idCriteria.list();

            if (productUuids != null && !productUuids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria()
                    .add(CPRestrictions.in("uuid", productUuids));

                return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
            }
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }
}

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

import org.candlepin.cache.CandlepinCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache;
import javax.persistence.TypedQuery;



/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {
    private static Logger log = LoggerFactory.getLogger(ProductCurator.class);

    private Configuration config;
    private I18n i18n;
    private CandlepinCache candlepinCache;

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(Configuration config, I18n i18n, CandlepinCache candlepinCache) {
        super(Product.class);

        this.config = config;
        this.i18n = i18n;
        this.candlepinCache = candlepinCache;
    }

    /**
     * Check if this pool provides the given product
     *
     * Figures out if the pool with poolId provides a product providedProductId.
     * 'provides' means that the product is either Pool product or is linked through
     * cp2_pool_provided_products table
     * @param poolId
     * @param providedProductId
     * @return True if and only if providedProductId is provided product or pool product
     */
    public Boolean provides(Pool pool, String providedProductId) {
        TypedQuery<Long> query = getEntityManager().createQuery(
            "SELECT count(product.uuid) FROM Pool p " + "LEFT JOIN p.providedProducts pproduct " +
            "LEFT JOIN p.product product " +
            "WHERE p.id = :poolid and (pproduct.id = :providedProductId OR product.id = :providedProductId)",
            Long.class);
        query.setParameter("poolid", pool.getId());
        query.setParameter("providedProductId", providedProductId);
        return query.getSingleResult() > 0;
    }

    /**
     * Check if this pool provides the given product ID as a derived provided product.
     * Used when we're looking for pools we could give to a host that will create
     * sub-pools for guest products.
     *
     * If derived product ID is not set, we just use the normal set of products.
     *
     * @param pool
     * @param derivedProvidedProductId
     * @return True if and only if derivedProvidedProductId is provided product or derived product
     */
    public Boolean providesDerived(Pool pool, String derivedProvidedProductId) {
        if (pool.getDerivedProduct() != null) {
            TypedQuery<Long> query = getEntityManager().createQuery(
                "SELECT count(product.uuid) FROM Pool p " +
                "LEFT JOIN p.derivedProvidedProducts pproduct " +
                "LEFT JOIN p.derivedProduct product " + "WHERE p.id = :poolid and " +
                "(pproduct.id = :providedProductId OR product.id = :providedProductId)",
                Long.class);
            query.setParameter("poolid", pool.getId());
            query.setParameter("providedProductId", derivedProvidedProductId);
            return query.getSingleResult() > 0;
        }
        else {
            return provides(pool, derivedProvidedProductId);
        }
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
    public Product lookupByName(Owner owner, String name) {
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
        Set<String> uuids = getDerivedPoolProvidedProducts(poolId);
        return getProductsByUuidCached(uuids);
    }

    public Set<Product> getPoolProvidedProductsCached(Pool pool) {
        return getPoolProvidedProductsCached(pool.getId());
    }

    public Set<Product> getPoolProvidedProductsCached(String poolId) {
        Set<String> providedUuids = getPoolProvidedProducts(poolId);
        return getProductsByUuidCached(providedUuids);
    }

    /**
     * Finds all provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getPoolProvidedProducts(String poolId) {
        TypedQuery<String> query = getEntityManager().createQuery(
            "SELECT product.uuid FROM Pool p INNER JOIN p.providedProducts product where p.id = :poolid",
            String.class);
        query.setParameter("poolid", poolId);
        return new HashSet<String>(query.getResultList());
    }

    /**
     * Finds all derived provided products for a given poolId
     *
     * @param poolId
     * @return Set of UUIDs
     */
    public Set<String> getDerivedPoolProvidedProducts(String poolId) {
        TypedQuery<String> query = getEntityManager().createQuery(
            "SELECT product.uuid FROM Pool p INNER JOIN p.derivedProvidedProducts product " +
            "WHERE p.id = :poolid",
            String.class);
        query.setParameter("poolid", poolId);
        return new HashSet<String>(query.getResultList());
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
        Set<Product> products = new HashSet<Product>();
        Set<String> notInCache = new HashSet<String>();
        Cache<String, Product> productCache = candlepinCache.getProductCache();

        //First find all products that are in cache. Those keys that
        //are not present in the cache will not be in the result Map
        Map<String, Product> productsFromCache = productCache.getAll(productUuids);
        products.addAll(productsFromCache.values());

        notInCache.addAll(productUuids);
        notInCache.removeAll(productsFromCache.keySet());

        //Now find, hydrate and cache all the products that has been
        //missing in the cache
        Map<String, Product> freshProducts =  getHydratedProductsByUuid(notInCache);

        productCache.putAll(freshProducts);
        products.addAll(freshProducts.values());

        return products;
    }

    /**
     * Loads the set of products from database and triggers all lazy loads.
     * @param uuids
     * @return Map of UUID to Product instance
     */
    public Map<String, Product> getHydratedProductsByUuid(Set<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return new HashMap<String, Product>();
        }

        Map<String, Product> productsByUuid = new HashMap<String, Product>();

        for (Product p : this.listAllByUuids(uuids)) {
            if (p == null) {
                continue;
            }

            p.getAttributes().size();
            for (ProductContent cont : p.getProductContent()) {
                cont.getContent().getModifiedProductIds().size();
            }
            p.getDependentProductIds().size();
            productsByUuid.put(p.getUuid(), p);
        }

        return productsByUuid;
    }

    /**
     * Retrieves a criteria which can be used to fetch a list of products with the specified Red
     * Hat product ID and entity version. If no products were found matching the given criteria,
     * this method returns an empty list.
     *
     * @param productId
     *  The Red Hat product ID
     *
     * @param hashcode
     *  The hash code representing the product version
     *
     * @return
     *  a criteria for fetching product by version
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Product> getProductsByVersion(String productId, int hashcode) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("id", productId))
            .add(Restrictions.or(
                Restrictions.isNull("entityVersion"),
                Restrictions.eq("entityVersion", hashcode)
            ));

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Retrieves a criteria which can be used to fetch a list of products with the specified Red Hat
     * product ID and entity version. If no products were found matching the given criteria, this
     * method returns an empty list.
     *
     * @param productVersions
     *  A mapping of Red Hat product IDs to product versions to fetch
     *
     * @return
     *  a criteria for fetching products by version
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Product> getProductByVersions(Map<String, Integer> productVersions) {
        if (productVersions == null || productVersions.isEmpty()) {
            return this.cpQueryFactory.<Product>buildQuery();
        }

        Disjunction disjunction = Restrictions.disjunction();
        DetachedCriteria criteria = this.createSecureDetachedCriteria().add(disjunction);

        for (Map.Entry<String, Integer> entry : productVersions.entrySet()) {
            disjunction.add(Restrictions.and(
                Restrictions.eq("id", entry.getKey()),
                Restrictions.or(
                    Restrictions.isNull("entityVersion"),
                    Restrictions.eq("entityVersion", entry.getValue())
                )
            ));
        }

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
    }

    // TODO:
    // This seems like something that should happen at the resource level, not in the curator.
    protected void validateAttributeValue(ProductAttribute attr) {
        Set<String> intAttrs = config.getSet(ConfigProperties.INTEGER_ATTRIBUTES);
        Set<String> posIntAttrs = config.getSet(
            ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES);
        Set<String> longAttrs = config.getSet(ConfigProperties.LONG_ATTRIBUTES);
        Set<String> posLongAttrs = config.getSet(
            ConfigProperties.NON_NEG_LONG_ATTRIBUTES);
        Set<String> boolAttrs = config.getSet(ConfigProperties.BOOLEAN_ATTRIBUTES);

        if (StringUtils.isBlank(attr.getValue())) { return; }

        if (intAttrs != null && intAttrs.contains(attr.getName()) ||
            posIntAttrs != null && posIntAttrs.contains(attr.getName())) {
            int value = -1;
            try {
                value = Integer.parseInt(attr.getValue());
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be an integer value.",
                    attr.getName()));
            }
            if (posIntAttrs != null && posIntAttrs.contains(
                attr.getName()) &&
                value < 0) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must have a positive value.",
                    attr.getName()));
            }
        }
        else if (longAttrs != null && longAttrs.contains(attr.getName()) ||
            posLongAttrs != null && posLongAttrs.contains(attr.getName())) {
            long value = -1;
            try {
                value = Long.parseLong(attr.getValue());
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be a long value.",
                    attr.getName()));
            }
            if (posLongAttrs != null && posLongAttrs.contains(
                attr.getName()) &&
                value <= 0) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must have a positive value.",
                    attr.getName()));
            }
        }
        else if (boolAttrs != null && boolAttrs.contains(attr.getName())) {
            if (attr.getValue() != null &&
                !"true".equalsIgnoreCase(attr.getValue().trim()) &&
                !"false".equalsIgnoreCase(attr.getValue()) &&
                !"1".equalsIgnoreCase(attr.getValue()) &&
                !"0".equalsIgnoreCase(attr.getValue())) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be a Boolean value.",
                    attr.getName()));
            }
        }
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
        if (entity.getAttributes() != null) {
            for (ProductAttribute pa : entity.getAttributes()) {
                pa.setProduct(entity);
                this.validateAttributeValue(pa);
            }
        }

        if (entity.getProductContent() != null) {
            for (ProductContent pc : entity.getProductContent()) {
                pc.setProduct(entity);
            }
        }

        // TODO: Add more reference checks here.

        return entity;
    }

    @Transactional
    public Product create(Product entity) {
        log.debug("Persisting new product entity: {}", entity);

        this.validateProductReferences(entity);

        return super.create(entity);
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
        Product toDelete = find(entity.getUuid());
        currentSession().delete(toDelete);
    }

    public boolean productHasSubscriptions(Product prod, Owner owner) {
        return ((Long) currentSession().createCriteria(Pool.class)
            .createAlias("providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("derivedProvidedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.or(
                Restrictions.eq("product.uuid", prod.getUuid()),
                Restrictions.eq("derivedProduct.uuid", prod.getUuid()),
                Restrictions.eq("providedProd.uuid", prod.getUuid()),
                Restrictions.eq("derivedProvidedProd.uuid", prod.getUuid())))
            .setProjection(Projections.count("id"))
            .uniqueResult()) > 0;
    }

    public CandlepinQuery<Product> getProductsWithContent(Owner owner, Collection<String> contentIds) {
        return this.getProductsWithContent(owner, contentIds, null);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Product> getProductsWithContent(Owner owner, Collection<String> contentIds,
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
    public CandlepinQuery<Product> getProductsWithContent(Collection<String> contentUuids) {
        if (contentUuids != null && !contentUuids.isEmpty()) {
            // See note above in getProductsWithContent for details on why we do two queries here
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


}

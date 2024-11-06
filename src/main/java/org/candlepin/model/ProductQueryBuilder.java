/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Provider;
import javax.persistence.Column;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.metamodel.EntityType;



/**
 * The ProductQueryBuilder is a utility class which provides a fluent-style interface for performing
 * arbitrary product lookups with a number of parameters.
 * <p></p>
 * This class is a loose wrapper around the JPA interface and one or more native queries to fetch results
 * as efficiently as possible, without the caller needing to jump back and forth between the curators
 * to get both the count and results.
 */
public class ProductQueryBuilder extends QueryBuilder<ProductQueryBuilder, Product> {
    private static final Logger log = LoggerFactory.getLogger(ProductQueryBuilder.class);

    private final Set<String> productIds;
    private final Set<String> productNames;
    private final Map<String, Owner> owners;
    private Inclusion active;
    private Inclusion custom;

    /**
     * Creates a new query builder backed by the specified entity manager provider.
     * <p></p>
     * <strong>Note:</strong> Query builder instances should not be constructed directly and should be
     * fetched from their corresponding curators.
     *
     * @param entityManagerProvider
     *  the provider to use for fetching an entity manager when building queries
     */
    protected ProductQueryBuilder(Provider<EntityManager> entityManagerProvider) {
        super(entityManagerProvider);

        this.productIds = new HashSet<>();
        this.productNames = new HashSet<>();
        this.owners = new HashMap<>();
        this.active = Inclusion.INCLUDE;
        this.custom = Inclusion.INCLUDE;
    }

    /**
     * Adds one or more product IDs to use for filtering query output.
     *
     * @param productIds
     *  a collection of product IDs to use to filter query output, or null to clear any previously
     *  set product IDs
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addProductIds(Collection<String> productIds) {
        if (productIds == null) {
            return this;
        }

        productIds.stream()
            .filter(pid -> pid != null && !pid.isBlank())
            .sequential()
            .forEach(this.productIds::add);

        if (this.productIds.size() > QueryBuilder.COLLECTION_SIZE_LIMIT) {
            throw new IllegalStateException("query builder collection size limit exceeded for product IDs");
        }

        return this;
    }

    /**
     * Adds one or more product IDs to use for filtering query output.
     *
     * @param productIds
     *  a collection of product IDs to use to filter query output
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addProductIds(String... productIds) {
        return this.addProductIds(productIds != null ? Arrays.asList(productIds) : null);
    }

    /**
     * Adds one or more product names to use for filtering query output.
     *
     * @param productNames
     *  a collection of product names to use to filter query output
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addProductNames(Collection<String> productNames) {
        if (productNames == null) {
            return this;
        }

        productNames.stream()
            .filter(name -> name != null && !name.isBlank())
            .map(String::toLowerCase)
            .sequential()
            .forEach(this.productNames::add);

        if (this.productNames.size() > QueryBuilder.COLLECTION_SIZE_LIMIT) {
            throw new IllegalStateException("query builder collection size limit exceeded for product names");
        }

        return this;
    }

    /**
     * Adds one or more product names to use for filtering query output.
     *
     * @param productNames
     *  a collection of product names to use to filter query output
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addProductNames(String... productNames) {
        return this.addProductNames(productNames != null ? Arrays.asList(productNames) : null);
    }

    /**
     * Adds one or more owners to use for filtering query output.
     *
     * @param owners
     *  a collection of owners to use to filter query output
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addOwners(Collection<Owner> owners) {
        if (owners == null) {
            return this;
        }

        owners.stream()
            .filter(Objects::nonNull)
            .filter(owner -> owner.getId() != null)
            .sequential()
            .forEach(owner -> this.owners.put(owner.getId(), owner));

        if (this.owners.size() > QueryBuilder.COLLECTION_SIZE_LIMIT) {
            throw new IllegalStateException("query builder collection size limit exceeded for owners");
        }

        return this;
    }

    /**
     * Adds one or more owners to use for filtering query output.
     *
     * @param owners
     *  a collection of owners to use to filter query output
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder addOwners(Owner... owners) {
        return this.addOwners(owners != null ? Arrays.asList(owners) : null);
    }

    /**
     * Sets the inclusion filter to use for filtering queries by active products, where "active" is defined
     * as a product that is currently in use by a subscription that has started and not yet expired, or
     * is linked to such a product. If the provided inclusion filter is null, the value "INCLUDE" will
     * be used instead.
     *
     * @param active
     *  an inclusion filter to set for active products
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder setActive(Inclusion active) {
        this.active = active != null ? active : Inclusion.INCLUDE;
        return this;
    }

    /**
     * Sets the inclusion filter to use for filtering queries by custom products, where "custom" is defined
     * as a product that originates from a source other than manifest import or pool refresh. If the provided
     * inclusion filter is null, the value "INCLUDE" will be used instead.
     *
     * @param custom
     *  an inclusion filter to set for custom products
     *
     * @return
     *  a reference to this query builder
     */
    public ProductQueryBuilder setCustom(Inclusion custom) {
        this.custom = custom != null ? custom : Inclusion.INCLUDE;
        return this;
    }

    // Impl note:
    // Everything about this query is awful due to JPA limitations both in terms of what it can do
    // and what its API allows you to do.
    // It'd be really nice to *not* build this with string concatenation, but since we can't join against
    // a native query from a criteria query, criteria queries can't join against a temporary table, no
    // part of JPA is ready for CTEs, and paging support makes partitioning large result sets clunky and
    // error prone (or a waste due to throwing everything in memory), we aren't left with many options
    // other than string concatenation.

    /**
     * Assembles the components of the query that make up the active products filter.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for filtering on active products
     *
     * @param criteriaChunks
     *  the list of criteria chunks to receive the predicates for filtering on active products
     *
     * @param queryArgs
     *  the map of parameters to receive the specific query arguments provided to the builder
     *
     * @param querySpaces
     *  a set to receive any additional tables or "spaces" hit by the components built by this method
     */
    private void buildActiveProductsComponents(List<String> queryChunks, List<String> criteriaChunks,
        Map<String, Object> queryArgs, Set<String> querySpaces) {

        // The "active" flag has a massive impact on how this query is constructed. If it's any value
        // other than "include", we start with the CTE. Otherwise we're a boring ol' select and we do
        // nothing in this method.
        if (this.active == Inclusion.INCLUDE) {
            return;
        }

        // Impl notes:
        // These CTEs are a bit of a mess, and are driven by three different DBs imposing their own goofy
        // limitations or quirks (mostly only two goofy DBs, but whatever):
        //  - HSQLDB requires that recursive CTEs define their parameters as part of the declaration of
        //    the CTE itself, which is the driver behind the rather redundant definitions.
        //  - HSQLDB also disallows more than one UNION operation in a recursive CTE, which means the
        //    anchor members *also* can't have more than one union without being wrapped in another query
        //    or CTE, leading to the clunky declaration of the "pcmap_anchor" CTE.
        //  - MySQL/MariaDB cannot self-reference more than once in the recursive step of a recursive
        //    CTE, which really hamstrings how these queries are built and is why the parent-child mapping
        //    does what it does.
        //  - PostgreSQL, as per usual, laughs at these queries and beasts through them like I'm fetching
        //    the count of an almost-empty table.
        //
        // As far as how this is all intended to work:
        //  - We start with pools since in production this gives us our smallest working product set.
        //  - Next we need to figure out every parent-child product relationship that will be significant
        //    to us. In other areas it may make sense for us to just grab the union of all derived and
        //    provided product refs, but doing so in the prod database takes 5-10s on its own. Instead,
        //    we step through from the pool products down into derived products and then recursively into
        //    provided products based on those we know we'll have.
        //  - Finally, we go back to the pool products and use our parent-child map to walk down the graph
        //    to discover all of our nested/descendant products, since our parent-child map has extraneous
        //    pools from getting the full set of derived products
        //
        // Note that this query is written primarily as a function of what works best for MySQL and the
        // shape of the data in production. It may make sense to periodically update it as the shape of
        // prod's data changes. Or if/when we update the model to not have two N-tier paths that have
        // to be traversed to fully/correctly build the product graph. :/
        String poolProductsQuery = "SELECT DISTINCT pool.product_uuid " +
            "  FROM cp_pool pool " +
            "  WHERE now() BETWEEN pool.startdate AND pool.enddate ";

        if (!this.owners.isEmpty()) {
            poolProductsQuery += "AND pool.owner_id IN (:owner_ids)";
            queryArgs.put("owner_ids", this.owners.keySet());
        }

        String ctes = "WITH RECURSIVE pool_products (product_uuid) AS (" + poolProductsQuery + "), " +
            """
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
            """;

        // This *must* go at the beginning of the query or we break
        queryChunks.add(0, ctes);

        // Add the tables/spaces we're querying against
        querySpaces.add("cp_pool");
        querySpaces.add("cp_product_provided_products");

        if (this.active == Inclusion.EXCLUSIVE) {
            queryChunks.add("JOIN active_products active ON active.uuid = prod.uuid");
        }
        else if (this.active == Inclusion.EXCLUDE) {
            queryChunks.add("LEFT JOIN active_products active ON active.uuid = prod.uuid");
            criteriaChunks.add("active.uuid IS NULL");
        }
    }

    /**
     * Assembles the components of the query that make up the custom products filter.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for filtering on custom products
     *
     * @param criteriaChunks
     *  the list of criteria chunks to receive the predicates for filtering on custom products
     *
     * @param queryArgs
     *  the map of parameters to receive the specific query arguments provided to the builder
     *
     * @param querySpaces
     *  a set to receive any additional tables or "spaces" hit by the components built by this method
     */
    private void buildCustomProductsComponents(List<String> queryChunks, List<String> criteriaChunks,
        Map<String, Object> queryArgs, Set<String> querySpaces) {

        List<String> namespaces = this.owners.values()
            .stream()
            .map(Owner::getKey)
            .toList();

        switch (this.custom) {
            case EXCLUSIVE:
                if (!namespaces.isEmpty()) {
                    criteriaChunks.add("prod.namespace IN (:org_namespaces)");
                    queryArgs.put("org_namespaces", namespaces);
                }
                else {
                    criteriaChunks.add("prod.namespace != ''");
                }
                break;

            case EXCLUDE:
                criteriaChunks.add("prod.namespace = ''");
                break;

            default:
            case INCLUDE:
                if (!namespaces.isEmpty()) {
                    criteriaChunks.add("(prod.namespace = '' OR prod.namespace IN (:org_namespaces))");
                    queryArgs.put("org_namespaces", namespaces);
                }
        }
    }

    /**
     * Builds the components of the query that make up the product ID filter.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for filtering on product IDs
     *
     * @param criteriaChunks
     *  the list of criteria chunks to receive the predicates for filtering on product IDs
     *
     * @param queryArgs
     *  the map of parameters to receive the specific query arguments provided to the builder
     *
     * @param querySpaces
     *  a set to receive any additional tables or "spaces" hit by the components built by this method
     */
    private void buildProductIdFilterComponents(List<String> queryChunks, List<String> criteriaChunks,
        Map<String, Object> queryArgs, Set<String> querySpaces) {

        if (!this.productIds.isEmpty()) {
            criteriaChunks.add("prod.product_id IN (:product_ids)");
            queryArgs.put("product_ids", this.productIds);
        }
    }

    /**
     * Builds the components of the query that make up the product name filter.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for filtering on product names
     *
     * @param criteriaChunks
     *  the list of criteria chunks to receive the predicates for filtering on product names
     *
     * @param queryArgs
     *  the map of parameters to receive the specific query arguments provided to the builder
     *
     * @param querySpaces
     *  a set to receive any additional tables or "spaces" hit by the components built by this method
     */
    private void buildProductNameFilterComponents(List<String> queryChunks, List<String> criteriaChunks,
        Map<String, Object> queryArgs, Set<String> querySpaces) {

        if (!this.productNames.isEmpty()) {
            // Impl note: This may end up being slow without an index to go along with it
            criteriaChunks.add("LOWER(prod.name) IN (:product_names)");
            queryArgs.put("product_names", this.productNames);
        }
    }

    /**
     * Assembles the WHERE clause from the various chunks built from the criteria provided to this builder.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for filtering on product names
     *
     * @param criteriaChunks
     *  the list of criteria chunks to assemble
     */
    private void assembleWhereClause(List<String> queryChunks, List<String> criteriaChunks) {
        if (criteriaChunks.isEmpty()) {
            return;
        }

        String whereClause = criteriaChunks.stream()
            .collect(Collectors.joining(" AND ", "WHERE ", ""));

        queryChunks.add(whereClause);
    }

    /**
     * Fetches the field object for a given field name, first checking on the immediate type, and then
     * checking any supertypes as necessary.
     *
     * @param type
     *  the base type to check for a field with the given name
     *
     * @param fieldName
     *  the name of the field to fetch
     *
     * @throws NoSuchFieldException
     *  if the field cannot be found within the type or its superclasses
     *
     * @return
     *  the field with the matching name on the nearest type defined in the hierarchy
     */
    private Field getAttributeField(Class<?> type, String fieldName) throws NoSuchFieldException {
        if (type == null) {
            throw new NoSuchFieldException(fieldName);
        }

        try {
            return type.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException e) {
            return this.getAttributeField(type.getSuperclass(), fieldName);
        }
    }

    /**
     * Assembles the ORDER BY clause from the query ordering provided to this builder.
     *
     * @param queryChunks
     *  the list of query chunks to receive the query components for ordering the query
     *
     * @param metamodel
     *  the metamodel to use to validate the ordering columns
     *
     * @param prefix
     *  a prefix to prepend to all ordering attributes
     *
     * @throws InvalidOrderKeyException
     *  if an order is provided referencing an attribute name (key) that does not exist
     */
    private void assembleOrderByClause(List<String> queryChunks, EntityType<?> metamodel, String prefix) {
        List<Order> ordering = this.getQueryOrdering();
        if (ordering.isEmpty()) {
            return;
        }

        Function<QueryBuilder.Order, String> orderMapper = order -> {
            try {
                // Impl note:
                // We should probably just have a known DTO field name to internal DB column/field mapper.
                // Even with this reflection magic, we're still going to eventually hit some desync in
                // terms of naming conventions between the DB model and our DTOs.
                //
                // However, things get a little spicy with the fact the DTOs are procedurally generated
                // from the API spec file with a quirky generator; so we can still desync even with some
                // semi-hardcoded mappings. There is no winning here. Perhaps it's best left to the resource
                // level anyway...?
                //
                // Regardless of the solution picked, we absolutely need to validate this input here because
                // the field name is going into the query raw otherwise. THE RISK OF SQL INJECTION IS
                // VERY REAL HERE.
                String validated = metamodel.getSingularAttribute(order.column())
                    .getName();

                Column annotation = this.getAttributeField(metamodel.getJavaType(), validated)
                    .getAnnotation(Column.class);

                // If we have a column annotation, use that; otherwise default to the validated field name
                String column = Optional.ofNullable(annotation)
                    .map(Column::name)
                    .filter(name -> !name.isBlank())
                    .orElse(validated);

                return new StringBuilder(prefix)
                    .append(column)
                    .append(' ')
                    .append(order.reverse() ? "DESC" : "ASC")
                    .toString();
            }
            catch (IllegalArgumentException | NoSuchFieldException e) {
                throw new InvalidOrderKeyException(order.column(), metamodel, e);
            }
        };

        String orderClause = ordering.stream()
            .map(orderMapper)
            .collect(Collectors.joining(", ", "ORDER BY ", ""));

        queryChunks.add(orderClause);
    }

    /**
     * Builds a query for counting the number of products matching the criteria provided to this query
     * builder.
     *
     * @return
     *  a query for counting products matching the criteria provided to this query builder
     */
    private Query buildProductCountQuery() {
        EntityManager entityManager = this.getEntityManager();

        List<String> queryChunks = new ArrayList<>();
        List<String> criteriaChunks = new ArrayList<>();
        Map<String, Object> queryArgs = new HashMap<>();
        Set<String> querySpaces = new HashSet<>();

        // Prime the query with our basic select...
        queryChunks.add("SELECT COUNT(*) FROM cp_products prod");

        // Add the various components...
        this.buildActiveProductsComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildCustomProductsComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildProductIdFilterComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildProductNameFilterComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);

        // Assemble the where clause
        this.assembleWhereClause(queryChunks, criteriaChunks);
        String sql = String.join(" ", queryChunks);

        log.trace("BUILT QUERY: {}", sql);
        log.trace("USING QUERY ARGUMENTS: {}", queryArgs);
        log.trace("DECLARING QUERY SPACES: {}", querySpaces);

        NativeQuery query = entityManager.createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Product.class);

        querySpaces.forEach(query::addSynchronizedQuerySpace);

        queryArgs.forEach(query::setParameter);

        return query;
    }

    /**
     * Builds a query for fetching products matching the criteria provided to this query builder.
     *
     * @return
     *  a query for fetching products matching the criteria provided to this query builder
     */
    private Query buildProductQuery() {
        EntityManager entityManager = this.getEntityManager();
        EntityType<Product> metamodel = entityManager.getMetamodel()
            .entity(Product.class);

        List<String> queryChunks = new ArrayList<>();
        List<String> criteriaChunks = new ArrayList<>();
        Map<String, Object> queryArgs = new HashMap<>();
        Set<String> querySpaces = new HashSet<>();

        // Impl note:
        // According to Hibernate docs, native queries can be turned into entities using the native mapper
        // so long as all of the columns are properly defined with @Column annotations in the entity itself
        // and the column names are returned with the query. Joins are also possible with some janky looking,
        // JPA/Hibernate-specific syntax, and lazy-loading proxies will be built as normal. In either
        // case, the docs actually recommend using the wildcard operator for entity retrevial.
        queryChunks.add("SELECT prod.* FROM cp_products prod");

        // Add the various components...
        this.buildActiveProductsComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildCustomProductsComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildProductIdFilterComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);
        this.buildProductNameFilterComponents(queryChunks, criteriaChunks, queryArgs, querySpaces);

        // Assemble the where clause and order statements
        this.assembleWhereClause(queryChunks, criteriaChunks);
        this.assembleOrderByClause(queryChunks, metamodel, "prod.");

        String sql = String.join(" ", queryChunks);

        log.trace("BUILT QUERY: {}", sql);
        log.trace("USING QUERY ARGUMENTS: {}", queryArgs);
        log.trace("DECLARING QUERY SPACES: {}", querySpaces);

        NativeQuery<Product> query = entityManager.createNativeQuery(sql, Product.class)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Product.class);

        querySpaces.forEach(query::addSynchronizedQuerySpace);

        queryArgs.forEach(query::setParameter);

        return query;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getResultCount() {
        Number count = (Number) this.buildProductCountQuery()
            .getSingleResult();

        return count.longValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Product> getResultList() {
        Query query = this.buildProductQuery();

        this.applyQueryOffset(query)
            .applyQueryLimit(query);

        return (List<Product>) query.getResultList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<Product> getResultStream() {
        Query query = this.buildProductQuery();

        this.applyQueryOffset(query)
            .applyQueryLimit(query);

        return (Stream<Product>) query.getResultStream();
    }

}

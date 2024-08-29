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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;



/**
 * The QueryBuilder class wraps the construction a database query behind an entity-focused abstraction,
 * allowing for specifying queryable entity properties with a fluent-style interface.
 * <p></p>
 * Subclasses are expected to provide additional properties relevant to the entity or data set being fetched
 * by the builder.
 *
 * @param <Q>
 *  the type of the query builder; used for fluent-style method declarations
 *
 * @param <T>
 *  the type of the rows or entities returned by this query builder
 */
public abstract class QueryBuilder<Q extends QueryBuilder, T> {

    /** The number of elements a given collection in the query builder may have */
    public static final int COLLECTION_SIZE_LIMIT = 512;

    /**
     * Generic container for storing result ordering information
     *
     * @param column
     *  the name of a column by which to order the query results; cannot be null or empty
     *
     * @param reverse
     *  whether or not to fetch the results in reverse (descending) order
     */
    public static record Order(String column, boolean reverse) {
        /**
         * Creates a new Order instance on the specified column.
         *
         * @param column
         *  the name of a column by which to order the query results; cannot be null or empty
         *
         * @param reverse
         *  whether or not to fetch the results in reverse (descending) order
         *
         * @throws IllegalArgumentException
         *  if column is null or empty
         */
        public Order(String column, boolean reverse) {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("column is null or empty");
            }

            this.column = column;
            this.reverse = reverse;
        }
    }

    /**
     * The inclusion type defines how some binary properties should be treated with respect
     * to their inclusion in the query output
     */
    public enum Inclusion {
        /**
         * INCLUDE indicates that products in the property-defined state should be included in the
         * results without any impact on other products with a different state. This is logically
         * equivalent to not filtering on the property at all.
         */
        INCLUDE,

        /**
         * EXCLUDE indicates that products with the matching property-defined state should be excluded
         * from the query results.
         */
        EXCLUDE,

        /**
         * EXCLUSIVE indicates that only products with the matching property-defined state should
         * be returned
         */
        EXCLUSIVE;

        /**
         * Attempts to find a matching inclusion type from the given name, ignoring case. If the specified
         * name is null, empty, or blank, this method returns the provided empty value, if present. If
         * the specified name is not null, empty, or blank, and cannot be resolved to a known inclusion
         * type, this method returns an empty optional.
         *
         * @param name
         *  the name of the inclusion type to match, case-insensitive
         *
         * @param emptyValue
         *  the inclusion type to use if the provided name is null, empty, or blank
         *
         * @return
         *  the inclusion type mapped to the given name, the empty value if the given name is null, empty
         *  or blank, or an empty optional otherwise
         */
        public static Optional<Inclusion> fromName(String name, Inclusion emptyValue) {
            if (name == null || name.isBlank()) {
                return Optional.ofNullable(emptyValue);
            }

            for (Inclusion inclusion : Inclusion.values()) {
                if (inclusion.name().equalsIgnoreCase(name)) {
                    return Optional.of(inclusion);
                }
            }

            return Optional.empty();
        }

        /**
         * Attempts to find a matching inclusion type from the given name, ignoring case. If the specified
         * name does not map to a known inclusion type, or is null, empty, or blank, this function returns
         * an empty optional.
         *
         * @param name
         *  the name of the inclusion type to match
         *
         * @return
         *  the inclusion type mapped to the given name, or an empty optional if no matching inclusion
         *  was found
         */
        public static Optional<Inclusion> fromName(String name) {
            return Inclusion.fromName(name, null);
        }
    }

    private final Provider<EntityManager> entityManagerProvider;

    private Optional<Integer> offset;
    private Optional<Integer> limit;
    private final List<Order> order;

    /**
     * Creates a new QueryBuilder using the specified entity manager provider instance.
     * <p></p>
     * <strong>Note:</strong> Query builder instances should not be constructed directly and should be
     * fetched from their corresponding curators.
     *
     * @param entityManagerProvider
     *  the provider to use for fetching an entity manager when building queries
     */
    protected QueryBuilder(Provider<EntityManager> entityManagerProvider) {
        this.entityManagerProvider = Objects.requireNonNull(entityManagerProvider);

        this.offset = Optional.empty();
        this.limit = Optional.empty();
        this.order = new ArrayList<>();
    }

    /**
     * Sets or clears the offset at which to begin fetching results. If null, any previously set
     * offset will be cleared.
     *
     * @param offset
     *  the row offset at which to begin fetching results, or null to clear the offset
     *
     * @throws IllegalArgumentException
     *  if the provided offset is a negative integer
     *
     * @return
     *  a reference to this query builder
     */
    public Q setOffset(Integer offset) {
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("offset is a negative integer");
        }

        this.offset = Optional.ofNullable(offset);
        return (Q) this;
    }

    /**
     * Clears the query offset. If the query offset has not yet been set, this method does nothing.
     *
     * @return
     *  a reference to this query builder
     */
    public Q clearOffset() {
        this.offset = Optional.empty();
        return (Q) this;
    }

    /**
     * Sets or clears the limit defining the number of results to fetch. If null, any previously set
     * limit will be cleared.
     *
     * @param limit
     *  the maximum number of results to fetch, or null to clear the limit
     *
     * @throws IllegalArgumentException
     *  if the provided limit is zero or a negative integer
     *
     * @return
     *  a reference to this query builder
     */
    public Q setLimit(Integer limit) {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit is zero or a negative integer");
        }

        this.limit = Optional.ofNullable(limit);
        return (Q) this;
    }

    /**
     * Clears the query limit. If the query limit has not yet been set, this method does nothing.
     *
     * @return
     *  a reference to this query builder
     */
    public Q clearLimit() {
        this.limit = Optional.empty();
        return (Q) this;
    }

    /**
     * Sets the query offset and limit for this query according to the page and page sizes provided.
     *
     * @param page
     *  the one-indexed page at which to set the query offset; must be a positive value
     *
     * @param pageSize
     *  the size of each page; must be a positive value
     *
     * @throws IllegalArgumentException
     *  if either page or pageSize are zero or negative values
     *
     * @return
     *  a reference to this query builder
     */
    public Q setPage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page is not a positive integer");
        }

        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize is not a positive integer");
        }

        this.setOffset((page - 1) * pageSize)
            .setLimit(pageSize);

        return (Q) this;
    }

    /**
     * Adds the specified result ordering to this query builder. If any query ordering has already been
     * provided, the new ordering will be added after any existing ordering and will be applied in the
     * order in which each was provided.
     *
     * @param column
     *  the name of a column by which to order the query results; cannot be null or empty
     *
     * @param reverse
     *  whether or not to fetch the results in reverse (descending) order
     *
     * @throws IllegalArgumentException
     *  if column is null or empty
     *
     * @return
     *  a reference to this query builder
     */
    public Q addOrder(String column, boolean reverse) {
        this.order.add(new Order(column, reverse));
        return (Q) this;
    }

    /**
     * Adds the specified result ordering to this query builder. If any query ordering has already been
     * provided, the new ordering will be added after any existing ordering and will be applied in the
     * order in which each was provided.
     *
     * @param order
     *  a collection of ordering columns to apply to this query
     *
     * @return
     *  a reference to this query builder
     */
    public Q addOrder(Order... order) {
        if (order == null) {
            return (Q) this;
        }

        Stream.of(order)
            .filter(Objects::nonNull)
            .sequential() // critical since we care about insertion order
            .forEach(this.order::add);

        return (Q) this;
    }

    /**
     * Clears any query ordering set. If the query order has not yet been set, this method does nothing.
     *
     * @return
     *  a reference to this query builder
     */
    public Q clearOrder() {
        this.order.clear();
        return (Q) this;
    }

    // TODO: We're not going to move off JPA any time soon, but if we really want to generalize this
    // interface, we could move these protected methods to a JPAQueryBuilder and leave them undefined
    // here; possibly even converting this class into an interface.

    /**
     * Fetches an entity manager to use to build the queries necessary for this build. While this method
     * should not ever return null, there's no guarantee that the entity manager returned will be the
     * same instance between two invocations.
     *
     * @return
     *  an entity manager to use to build queries
     */
    protected EntityManager getEntityManager() {
        return this.entityManagerProvider.get();
    }

    /**
     * Applies query ordering to the specified criteria query and root. If no builder, query, or root
     * are provided, or no query ordering was defined, this method silently returns.
     *
     * @param cquery
     *  The criteria query to which to apply the query ordering
     *
     * @param root
     *  the query root from which the order columns are derived
     *
     * @throws InvalidOrderKeyException
     *  if an order is provided referencing an attribute name (key) that does not exist
     *
     * @return
     *  a reference to this query builder
     */
    protected <T> Q applyQueryOrdering(CriteriaBuilder builder, CriteriaQuery<T> cquery, Root<T> root) {
        if (builder == null || cquery == null || root == null || this.order.isEmpty()) {
            return (Q) this;
        }

        Function<Order, javax.persistence.criteria.Order> orderMapper = order -> {
            try {
                return order.reverse() ?
                    builder.desc(root.get(order.column())) :
                    builder.asc(root.get(order.column()));
            }
            catch (IllegalArgumentException e) {
                throw new InvalidOrderKeyException(order.column(), root.getModel(), e);
            }
        };

        List<javax.persistence.criteria.Order> ordering = this.order.stream()
            .map(orderMapper)
            .toList();

        cquery.orderBy(ordering);
        return (Q) this;
    }

    /**
     * Fetches an unmodifiable list consisting of the query ordering arguments provided to this builder
     * in the order they were provided. If no ordering has been provided, this method returns an empty
     * list.
     * <p></p>
     * Note: This method is intended for query builders which cannot use the apply method.
     *
     * @return
     *  an unmodifiable list of ordering arguments to apply to the results of this query builder
     */
    protected List<Order> getQueryOrdering() {
        return Collections.unmodifiableList(this.order);
    }

    /**
     * Applies the query offset to the specified typed query. If no query is provided, or no query offset
     * was defined, this method silently returns.
     *
     * @param query
     *  the query to which to apply the query offset
     *
     * @return
     *  a reference to this query builder
     */
    protected Q applyQueryOffset(Query query) {
        if (query == null) {
            return (Q) this;
        }

        this.offset.ifPresent(query::setFirstResult);
        return (Q) this;
    }

    /**
     * Applies the query limit to the specified typed query. If no query is provided, or no query limit
     * was defined, this method silently returns.
     *
     * @param query
     *  the query to which to apply the query limit
     *
     * @return
     *  a reference to this query builder
     */
    protected Q applyQueryLimit(Query query) {
        if (query == null) {
            return (Q) this;
        }

        this.limit.ifPresent(query::setMaxResults);
        return (Q) this;
    }

    /**
     * Fetches the number of entities matching the current query criteria, without respect to any query
     * offsets, result limits, or ordering.
     *
     * @return
     *  the number of entities matching the current criteria, ignoring any specified query offsets or
     *  limits
     */
    public abstract long getResultCount();

    /**
     * Builds and executes the query with the specified query criteria and fetches a list containing
     * all matching entities found. If no entities were found matching the given query criteria, this
     * method returns an empty list.
     *
     * @return
     *  a list of entities matching the current query critieria
     */
    public abstract List<T> getResultList();

    /**
     * Builds and executes the query with the specified query criteria and fetches a stream containing
     * all matching entities found. If no entities were found matching the given query criteria, this
     * method returns an empty stream.
     * <p></p>
     * <strong>Warning</strong>: In some environments, this method may return a stream which is backed
     * by a database cursor. In such an environment, attempting to resolve a lazily loaded field will
     * close the cursor even if the stream contains more elements. Further processing of the stream in
     * this state will result in an SQL exception. If this is a potential concern, callers which need
     * stream processing may want to consider using <tt>.getResultList().stream()</tt> instead.
     *
     * @return
     *  a stream of entities matching the current query critieria
     */
    public abstract Stream<T> getResultStream();

    // add any additional operations here (delete? probably not)
}

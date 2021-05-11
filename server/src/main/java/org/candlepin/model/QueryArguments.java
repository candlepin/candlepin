/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.LinkedList;



/**
 * Container object for providing various arguments to the consumerlookup method(s).
 *
 * @param <T>
 *  The type of the QueryArguments subclass; used for method chaining
 */
public class QueryArguments<T extends QueryArguments> {

    /** The number of elements a given collection in the query builder may have */
    public static final int COLLECTION_SIZE_LIMIT = 512;

    /** Generic container class for storing result ordering information */
    public static final class Order {
        private final String column;
        private final boolean reverse;

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
            if (column == null || column.isEmpty()) {
                throw new IllegalArgumentException("column is null or empty");
            }

            this.column = column;
            this.reverse = reverse;
        }

        /**
         * Fetches the name of a column by which to order the query results
         *
         * @return
         *  the name of a column by which to order query results
         */
        public String column() {
            return this.column;
        }

        /**
         * Whether or not the ordering of results should be in reverse (descending) order.
         *
         * @return
         *  true if the results should be ordered in descending order; false otherwise
         */
        public boolean reverse() {
            return this.reverse;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof Order) {
                Order that = (Order) obj;

                return new EqualsBuilder()
                    .append(this.column(), that.column())
                    .append(this.reverse(), that.reverse())
                    .isEquals();
            }

            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return new HashCodeBuilder(37, 7)
                .append(this.column())
                .append(this.reverse())
                .toHashCode();
        }
    }

    protected Integer offset;
    protected Integer limit;
    protected Collection<Order> order;


    /**
     * Sets or clears the offset at which to begin fetching results. If null, any previously set
     * offset will be cleared.
     *
     * @param offset
     *  the row offset at which to begin fetching results, or null to clear the offset
     *
     * @return
     *  a reference to this QueryArguments
     */
    public T setOffset(Integer offset) {
        this.offset = offset;
        return (T) this;
    }

    /**
     * Gets the offset at which to begin fetching results. If an offset has not yet been defined,
     * this method returns null.that
     *
     * @return
     *  the offset at which to begin fetching results, or null if the offset has not been defined
     */
    public Integer getOffset() {
        return this.offset;
    }

    /**
     * Sets or clears the limit defining the number of results to fetch. If null, any previously set
     * limit will be cleared.
     *
     * @param limit
     *  the maximum number of results to fetch, or null to clear the limit
     *
     * @return
     *  a reference to this QueryArguments
     */
    public T setLimit(Integer limit) {
        this.limit = limit;
        return (T) this;
    }

    /**
     * Gets the maximum number of results to fetch. If a limit has not yet been defined, this method
     * returns null.
     *
     * @return
     *  the maximum number of results to fetch, or null if limit has not been defined
     */
    public Integer getLimit() {
        return this.limit;
    }

    /**
     * Sets or clears the collection of result ordering to apply to the query. If null, any
     * previously set order is cleared.
     *
     * @param order
     *  a collection of Order objects defining the result ordering to apply to the query, or null
     *  to clear the result order
     *
     * @return
     *  a reference to this QueryArguments
     */
    public T setOrder(Collection<Order> order) {
        this.order = order;
        return (T) this;
    }

    /**
     * Adds the specified result ordering to this query builder.
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
     *  a reference to this QueryArguments
     */
    public T addOrder(String column, boolean reverse) {
        if (this.order == null) {
            this.order = new LinkedList<>();
        }

        this.order.add(new Order(column, reverse));
        return (T) this;
    }

    /**
     * Gets the result ordering to apply to the query. If no ordering has been set, this method
     * returns null.
     *
     * @return
     *  a collection containing all of the result ordering to apply to the query, or null if no
     *  ordering has been defined
     */
    public Collection<Order> getOrder() {
        return this.order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof QueryArguments) {
            QueryArguments that = (QueryArguments) obj;

            return new EqualsBuilder()
                .append(this.getOffset(), that.getOffset())
                .append(this.getLimit(), that.getLimit())
                .append(this.getOrder(), that.getOrder())
                .isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.getOffset())
            .append(this.getLimit())
            .append(this.getOrder())
            .toHashCode();
    }

}

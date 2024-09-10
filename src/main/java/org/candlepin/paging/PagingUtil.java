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
package org.candlepin.paging;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.InvalidOrderKeyException;
import org.candlepin.model.QueryBuilder;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;



/**
 * The PagingUtil class provides methods for applying paging to common collections when present in
 * the request context.
 *
 * @param <T>
 *  the type this util will page
 */
public class PagingUtil<T> {

    private final Configuration config;
    private final I18n i18n;
    private final FieldComparatorFactory<T> comparatorFactory;

    private final int maxPageSize;

    /**
     * Builds a new PagingUtil with the given internationalization and comparator factory.
     *
     * @param i18n
     *  the internationalization module to use for translating error messages
     *
     * @param comparatorFactory
     *  the comparator factory to use for sorting elements paged by this paging util
     */
    public PagingUtil(Configuration config, I18n i18n, FieldComparatorFactory<T> comparatorFactory) {
        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.comparatorFactory = Objects.requireNonNull(comparatorFactory);

        this.maxPageSize = this.config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
    }

    /**
     * Checks the given page size against the max page size and throws a bad request exception if the
     * page size exceeds the maximum configured page size.
     *
     * @param pageSize
     *  the request page size to validate
     *
     * @throws BadRequestException
     *  if the requested page size is larger than the maximum configured page size
     *
     * @return
     *  the validated page size
     */
    private long validateRequestedPageSize(long pageSize) {
        if (pageSize <= this.maxPageSize) {
            return pageSize;
        }

        String errmsg = this.i18n.tr("This endpoint does not support returning {0} elements in a single " +
            "request; please apply paging with a page size no larger than {1}",
            pageSize, this.maxPageSize);

        throw new BadRequestException(errmsg);
    }

    /**
     * Applies any paging in the request context to the given stream. This method returns a copy of
     * the stream with any sorting, offset, and/or limits applied as specified in the request. If
     * the request does not define any paging information, this method returns the provided stream
     * unmodified. If the provided stream is null, this method returns an empty stream.
     *
     * @param stream
     *  the stream to page
     *
     * @param count
     *  the number of expected elements in the stream; used to populate the "max records" header
     *
     * @throws BadRequestException
     *  if the field in the request cannot be resolved by this PagingUtil's field mapper
     *
     * @return
     *  a stream with any paging from the request context applied
     */
    public Stream<T> applyPaging(Stream<T> stream, int count) {
        // Check that we actually have something to page
        if (stream == null) {
            stream = Stream.of();
            count = 0;
        }

        // Ensure we have a paged request
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest == null) {
            return stream;
        }

        // Impl note:
        // Sorting will always be required (for consistency) if a page request object is
        // present -- either .isPaging() will be true, or we'll have ordering config.
        String sortField = pageRequest.getSortBy();
        Comparator<T> comparator = sortField != null && !sortField.isBlank() ?
            this.comparatorFactory.getComparator(sortField) :
            this.comparatorFactory.getDefaultComparator();

        if (comparator == null) {
            // We need to change up the error message depending on the presence of the sortBy field.
            String errmsg = sortField != null && !sortField.isBlank() ?
                this.i18n.tr("Invalid or unsupported sort-by field: {0}", sortField) :
                this.i18n.tr("Paging requested, but no sort-by field provided");

            throw new BadRequestException(errmsg);
        }

        // Ordering
        PageRequest.Order order = Optional.ofNullable(pageRequest.getOrder())
            .orElse(PageRequest.DEFAULT_ORDER);

        if (order == PageRequest.Order.DESCENDING) {
            comparator = comparator.reversed();
        }

        stream = stream.sorted(comparator);

        // Paging
        if (pageRequest.isPaging()) {
            int page = pageRequest.getPage();
            int pageSize = pageRequest.getPerPage();
            int offset = (page - 1) * pageSize;

            stream = stream.skip(offset)
                .limit(pageSize);

            // Create a page object for the link header response
            Page<T> contextPage = new Page<T>()
                .setMaxRecords(count)
                .setPageRequest(pageRequest);

            // Note: we don't need to (nor should we) store the page data in the page
            ResteasyContext.pushContext(Page.class, contextPage);
        }

        return stream;
    }

    /**
     * Converts the given collection to a stream, with any paging in the request context applied.
     * If the request does not define any paging information, this method returns the a stream
     * containing all of the elements of the collection in their original, unmodified order;
     * functionally identical to calling its <tt>.stream()</tt> method. If the provided collection
     * is null, this method returns an empty stream.
     *
     * @param collection
     *  the collection to page
     *
     * @throws BadRequestException
     *  if the field in the request cannot be resolved by this PagingUtil's field mapper
     *
     * @return
     *  a stream with applied paging from the request context, or null if no collection was provided
     */
    public Stream<T> applyPaging(Collection<T> collection) {
        return collection != null ?
            this.applyPaging(collection.stream(), collection.size()) :
            this.applyPaging(Stream.of(), 0);
    }


    // TODO: This could use some nicer, cleaner integration; it's rather bolted on at the moment.

    /**
     * Applies any paging in the request context to the provided query builder, then executes it, returning
     * a stream consisting of the results of the query's execution. While applying any requested paging,
     * this method will also validate that the number of elements fetched won't exceed the configured
     * maximum paging size, and the sorting fields are valid. If either of these checks fail, this method
     * throws an exception. If the provided query is null, or  otherwise fetches nothing, this method
     * returns an empty stream.
     *
     * @param queryBuilder
     *  the query builder to which any request context paging should be applied
     *
     * @throws BadRequestException
     *  if the number of results the query builder will return exceeds the maximum page size
     *
     * @throws InvalidOrderKeyException
     *  if the query has been ordered by an invalid field name
     *
     * @return
     *  A stream consisting of the results of executing the provided query once paging has been applied
     */
    public <T> Stream<T> applyPaging(QueryBuilder<?, T> queryBuilder) {
        if (queryBuilder == null) {
            return Stream.of();
        }

        try {
            long count = queryBuilder.getResultCount();

            PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
            if (pageRequest != null) {
                // Impl note:
                // Sorting will always be required (for consistency) if a page request object is present --
                // either .isPaging() will be true, or we'll have ordering config.
                String sortField = pageRequest.getSortBy();
                if (sortField == null || sortField.isBlank()) {
                    // This is actually a very bad default, but it's here for backwards compatibility
                    // purposes. Existing solutions use this field whenever the sort-by field is absent,
                    // so to be drop-in compliant, we do the same.
                    sortField = PageRequest.DEFAULT_SORT_FIELD;
                }

                boolean reverse = pageRequest.getOrder() == PageRequest.DEFAULT_ORDER;
                queryBuilder.addOrder(sortField, reverse);

                if (pageRequest.isPaging()) {
                    queryBuilder.setPage(pageRequest.getPage(), pageRequest.getPerPage());

                    // Create a page object for the link header response
                    Page<T> contextPage = new Page<T>()
                        .setMaxRecords(count < Integer.MAX_VALUE ? (int) count : Integer.MAX_VALUE)
                        .setPageRequest(pageRequest);

                    // Note: we don't need to (nor should we) store the page data in the page
                    ResteasyContext.pushContext(Page.class, contextPage);
                }
            }
            else {
                this.validateRequestedPageSize(count);
            }

            // TODO: This should be left to the caller so the decision between returning a list or a stream
            // can be made on a call-by-call basis, but at the time of writing, the sort-by field validation
            // happens on query execution time, so kicking this back to the caller means forcing the
            // boilerplate of the order key exception translation onto them.
            //
            // Impl note:
            // We're getting the list and them streaming it explicitly to avoid the issue with
            // .getResultStream() potentially returning a stream of entities with lazily loaded values,
            // backed by a cursor. In such a case, .getResultStream() can lead to premature cursor closure
            // and exceptions when the lazy fields are fetched.
            return queryBuilder.getResultList()
                .stream();
        }
        catch (InvalidOrderKeyException e) {
            String errmsg = this.i18n.tr("Invalid or unsupported sort-by field: {0}", e.getColumn());
            throw new BadRequestException(errmsg, e);
        }
    }

}

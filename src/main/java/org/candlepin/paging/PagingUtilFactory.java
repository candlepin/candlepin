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

import org.candlepin.config.Configuration;

import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;



/**
 * Simple provider/factory class for building PagingUtil instances that can be injected into any
 * classes which need paging functionality.
 */
public class PagingUtilFactory {

    private final Configuration config;
    private final Provider<I18n> i18nProvider;

    @Inject
    public PagingUtilFactory(Configuration config, Provider<I18n> i18nProvider) {
        this.config = Objects.requireNonNull(config);
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
    }

    /**
     * Fetches a PagingUtil that will use the specified field mapper for handling conversion of the
     * requested field name string to its matching function.
     *
     * @param comparatorFactory
     *  the comparator factory to pass through to the generated PagingUtil instance
     *
     * @return
     *  a PagingUtil instance backed by the given field comparator factory
     */
    public <T> PagingUtil<T> using(FieldComparatorFactory<T> comparatorFactory) {
        if (comparatorFactory == null) {
            throw new IllegalArgumentException("comparatorFactory is null");
        }

        return new PagingUtil<>(this.config, this.i18nProvider.get(), comparatorFactory);
    }

    /**
     * Fetches a PagingUtil that will use a reflection-based field mapper for the given class to
     * handling the conversion of field names to functions.
     * <p>
     * This can be used in cases where the paged type does not have fields which require special
     * consideration when sorting, all accessors are available for sorting, and the performance hit
     * that comes along with using reflection for the lookup is acceptable. In other cases, a custom
     * comparator factory is recommended.
     *
     * @param type
     *  the class of the objects to be paged
     *
     * @param defaultSortField
     *  the field to sort on if paging is requested but no sort-by field is specified; null to
     *  disable default sorting
     *
     * @return
     *  a PagingUtil instance backed by a reflection-based field comparator factory for the given
     *  class type
     */
    public <T> PagingUtil<T> forClass(Class<T> type, String defaultSortField) {
        return this.using(new ReflectionFieldComparatorFactory<>(type, defaultSortField));
    }

    /**
     * Fetches a PagingUtil that will use a reflection-based field mapper for the given class to
     * handling the conversion of field names to functions. If paging is requested, but no sort-by
     * field is specified, the field mapper will default to the value defined in
     * <tt>PageRequest.DEFAULT_SORT_FIELD</tt>.
     * <p>
     * This can be used in cases where the paged type does not have fields which require special
     * consideration when sorting, all accessors are available for sorting, and the performance hit
     * that comes along with using reflection for the lookup is acceptable. In other cases, a custom
     * comparator factory is recommended.
     *
     * @param type
     *  the class of the objects to be paged
     *
     * @return
     *  a PagingUtil instance backed by a reflection-based field comparator factory for the given
     *  class type
     */
    public <T> PagingUtil<T> forClass(Class<T> type) {
        // This is actually a very bad default, but it's here for backwards compatibility purposes.
        // Existing solutions use this field whenever the sort-by field is absent, so to be drop-in
        // compliant, we do the same.
        return this.forClass(type, PageRequest.DEFAULT_SORT_FIELD);
    }

}

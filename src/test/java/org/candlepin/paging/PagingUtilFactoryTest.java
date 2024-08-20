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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Provider;



public class PagingUtilFactoryTest {

    private Configuration config;
    private Provider<I18n> i18nProvider;

    @BeforeEach
    public void beforeEach() {
        ResteasyContext.clearContextData();

        this.config = TestConfig.defaults();
        this.i18nProvider = () -> I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @AfterEach
    public void afterEach() {
        ResteasyContext.clearContextData();
    }

    private PagingUtilFactory buildPagingUtilFactory() {
        return new PagingUtilFactory(this.config, this.i18nProvider);
    }

    private void setSortingPageRequestContext(String sortBy, PageRequest.Order order) {
        PageRequest pageRequest = new PageRequest()
            .setSortBy(sortBy)
            .setOrder(order);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);
    }

    @Test
    public void testUsingGeneratesUtilUsingFactory() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        // Use a simple comparator factory. It doesn't need to be correct; it just needs to function
        // enough to not break.
        FieldComparatorFactory<Pageable> comparatorFactory = spy(new FieldComparatorFactory<Pageable>() {
            @Override
            public Comparator<Pageable> getComparator(String fieldName) {
                return Comparator.comparing(Pageable::getFieldOne);
            }
        });

        // Generate a new PagingUtil using our spy factory. We can then verify it's using the test
        // comparator factory by verifying we see at least one invocation of getComparator.
        PagingUtil<Pageable> pagingUtil = factory.using(comparatorFactory);
        assertNotNull(pagingUtil);

        // We still have to put a page request in the context or else the paging util will short
        // circuit its logic and not do anything at all.
        this.setSortingPageRequestContext("fieldOne", PageRequest.Order.ASCENDING);

        // Trigger our exception (hopefully)
        pagingUtil.applyPaging(List.of(new Pageable()));

        // Verify our factory was used to get the comparator
        verify(comparatorFactory, times(1)).getComparator(eq("fieldOne"));
    }

    @Test
    public void testUsingRequiresNonNullInput() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.using(null));
    }

    @Test
    public void testForClassWithoutDefaultUsesGenericComparatorFactory() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        PagingUtil<Pageable> pagingUtil = factory.forClass(Pageable.class);
        assertNotNull(pagingUtil);

        // We can't possibly know *which* comparator factory it's using, but we can expect whatever
        // it's using will properly order our objects according to the sort-by and order fields in
        // the page request.
        this.setSortingPageRequestContext("fieldOne", PageRequest.Order.ASCENDING);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        List<Pageable> expected = List.of(
            elementMap.get(1),
            elementMap.get(3),
            elementMap.get(2));

        List<Pageable> output = pagingUtil.applyPaging(elementMap.values())
            .toList();

        assertEquals(expected, output);
    }

    @Test
    public void testForClassWithDefaultUsesGenericComparatorFactory() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        PagingUtil<Pageable> pagingUtil = factory.forClass(Pageable.class, "fieldTwo");
        assertNotNull(pagingUtil);

        // We can't possibly know *which* comparator factory it's using, but we can expect whatever
        // it's using will properly order our objects according to the default comparator
        this.setSortingPageRequestContext(null, PageRequest.Order.ASCENDING);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        List<Pageable> expected = List.of(
            elementMap.get(2),
            elementMap.get(1),
            elementMap.get(3));

        List<Pageable> output = pagingUtil.applyPaging(elementMap.values())
            .toList();

        assertEquals(expected, output);
    }

    @Test
    public void testForClassWithoutDefaultRequiresNonNullType() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.forClass(null));
    }

    @Test
    public void testForClassWithDefaultRequiresNonNullType() {
        PagingUtilFactory factory = this.buildPagingUtilFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.forClass(null, "some_default"));
    }

}

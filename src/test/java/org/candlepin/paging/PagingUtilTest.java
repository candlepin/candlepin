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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.exceptions.BadRequestException;

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
import java.util.stream.Stream;



public class PagingUtilTest {

    /**
     * Simple comparator factory specifically built for testing paging with the Pageable test object
     */
    private static class PageableComparatorFactory implements FieldComparatorFactory<Pageable> {
        @Override
        public Comparator<Pageable> getComparator(String fieldName) {
            if ("fieldOne".equals(fieldName)) {
                return Comparator.comparing(Pageable::getFieldOne);
            }
            else if ("fieldTwo".equals(fieldName)) {
                return Comparator.comparing(Pageable::getFieldTwo);
            }

            return null;
        }

        @Override
        public Comparator<Pageable> getDefaultComparator() {
            return this.getComparator("fieldOne");
        }
    }


    private I18n i18n;

    @BeforeEach
    public void beforeEach() {
        ResteasyContext.clearContextData();

        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @AfterEach
    public void afterEach() {
        ResteasyContext.clearContextData();
    }

    private PagingUtil<Pageable> buildPagingUtil() {
        return new PagingUtil<>(this.i18n, new PageableComparatorFactory());
    }

    public void validateContextPage(PageRequest pageRequest, int maxRecords) {
        Page page = ResteasyContext.getContextData(Page.class);
        assertNotNull(page);
        assertEquals(maxRecords, page.getMaxRecords());

        PageRequest contextPageRequest = page.getPageRequest();
        assertNotNull(contextPageRequest);
        assertEquals(pageRequest.getPage(), contextPageRequest.getPage());
        assertEquals(pageRequest.getPerPage(), contextPageRequest.getPerPage());
        assertEquals(pageRequest.getSortBy(), contextPageRequest.getSortBy());
        assertEquals(pageRequest.getOrder(), contextPageRequest.getOrder());
    }

    @Test
    public void testNoPagingAppliedToStreamWhenRequestLacksPaging() {
        List<Pageable> elements = List.of(
            new Pageable("1", "b"),
            new Pageable("3", "a"),
            new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elements.stream(), elements.size());

        List<Pageable> page = pagedStream.toList();
        assertEquals(elements, page);

        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageAscendingOrderingAppliedToStream() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values().stream(), elementMap.size());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(1),
            elementMap.get(3),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Since we're only ordering, there shouldn't be a page in the request context
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageDescendingOrderingAppliedToStream() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldTwo")
            .setOrder(PageRequest.Order.DESCENDING);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values().stream(), elementMap.size());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(3),
            elementMap.get(1),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Since we're only ordering, there shouldn't be a page in the request context
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageLimitAppliedToStream() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING)
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values().stream(), elementMap.size());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(4),
            elementMap.get(3),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testPageOffsetAppliedToStream() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING)
            .setPerPage(3)
            .setPage(2);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values().stream(), elementMap.size());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(1),
            elementMap.get(5));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testDefaultOrderingAppliedToStreamWhenSortByIsUnspecified() {
        // Since we're not specifying any sorting details, we're expecting
        // to get the default defined by the PageableComparatorFactory --
        // "fieldOne" -- in descending order.

        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values().stream(), elementMap.size());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(5),
            elementMap.get(1),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testNullStreamConvertedToEmptyStream() {
        Stream<Pageable> pagedStream = this.buildPagingUtil().applyPaging(null, 0);
        assertNotNull(pagedStream);
        assertEquals(0, pagedStream.count());

        // No page request in the context, so we shouldn't have one after applying paging
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testNullStreamConvertedToEmptyStreamWithPageContext() {
        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Stream<Pageable> pagedStream = this.buildPagingUtil().applyPaging(null, 0);
        assertNotNull(pagedStream);
        assertEquals(0, pagedStream.count());

        // Even though we tried to return null, we should still populate the page in the context
        // accordingly
        this.validateContextPage(pageRequest, 0);
    }

    @Test
    public void testNoPagingAppliedToCollectionWhenRequestLacksPaging() {
        List<Pageable> elements = List.of(
            new Pageable("1", "b"),
            new Pageable("3", "a"),
            new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elements);

        List<Pageable> page = pagedStream.toList();
        assertEquals(elements, page);

        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageAscendingOrderingAppliedToCollection() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(1),
            elementMap.get(3),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Since we're only ordering, there shouldn't be a page in the request context
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageDescendingOrderingAppliedToCollection() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldTwo")
            .setOrder(PageRequest.Order.DESCENDING);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("1", "b"),
            2, new Pageable("3", "a"),
            3, new Pageable("2", "c"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(3),
            elementMap.get(1),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Since we're only ordering, there shouldn't be a page in the request context
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testPageLimitAppliedToCollection() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING)
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(4),
            elementMap.get(3),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testPageOffsetAppliedToCollection() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("fieldOne")
            .setOrder(PageRequest.Order.ASCENDING)
            .setPerPage(3)
            .setPage(2);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(1),
            elementMap.get(5));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testDefaultOrderingAppliedToCollectionWhenSortByIsUnspecified() {
        // Since we're not specifying any sorting details, we're expecting
        // to get the default defined by the PageableComparatorFactory --
        // "fieldOne" -- in descending order.

        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Map<Integer, Pageable> elementMap = Map.of(
            1, new Pageable("4", "a"),
            2, new Pageable("3", "c"),
            3, new Pageable("2", "e"),
            4, new Pageable("1", "d"),
            5, new Pageable("5", "b"));

        Stream<Pageable> pagedStream = this.buildPagingUtil()
            .applyPaging(elementMap.values());

        assertNotNull(pagedStream);

        List<Pageable> expected = List.of(
            elementMap.get(5),
            elementMap.get(1),
            elementMap.get(2));

        List<Pageable> page = pagedStream.toList();
        assertEquals(expected, page);

        // Validate the resultant page in the context
        this.validateContextPage(pageRequest, elementMap.size());
    }

    @Test
    public void testNullCollectionConvertedToEmptyStream() {
        Stream<Pageable> pagedStream = this.buildPagingUtil().applyPaging(null);
        assertNotNull(pagedStream);
        assertEquals(0, pagedStream.count());

        // No page request in the context, so we shouldn't have one after applying paging
        Page contextPage = ResteasyContext.getContextData(Page.class);
        assertNull(contextPage);
    }

    @Test
    public void testNullCollectionConvertedToEmptyStreamWithPageContext() {
        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(3);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        Stream<Pageable> pagedStream = this.buildPagingUtil().applyPaging(null);
        assertNotNull(pagedStream);
        assertEquals(0, pagedStream.count());

        // Even though we tried to return null, we should still populate the page in the context
        // accordingly
        this.validateContextPage(pageRequest, 0);
    }

    @Test
    public void testInvalidSortByFieldTriggersBadRequestExceptionWhenPagingStream() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("nonsense_field");

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        PagingUtil<Pageable> pagingUtil = this.buildPagingUtil();
        Stream<Pageable> input = Stream.of(new Pageable("1", "a"));

        Exception exception = assertThrows(BadRequestException.class, () -> pagingUtil.applyPaging(input, 1));
        String errmsg = exception.getMessage();
        assertNotNull(errmsg);
        assertTrue(errmsg.contains("Invalid or unsupported sort-by field"));
    }

    @Test
    public void testInvalidSortByFieldTriggersBadRequestExceptionWhenPagingCollection() {
        PageRequest pageRequest = new PageRequest()
            .setSortBy("nonsense_field");

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        PagingUtil<Pageable> pagingUtil = this.buildPagingUtil();
        List<Pageable> input = List.of(new Pageable("1", "a"));

        Exception exception = assertThrows(BadRequestException.class, () -> pagingUtil.applyPaging(input));
        String errmsg = exception.getMessage();
        assertNotNull(errmsg);
        assertTrue(errmsg.contains("Invalid or unsupported sort-by field"));
    }

    @Test
    public void testBadRequestExceptionWhenDefaultSortByIsUnsupportedWhenPagingStream() {
        // This test verifies a BadRequestException is thrown in the case where the underlying
        // field comparator factory does not provide a default comparator, and paging has been
        // requested without specifying the sort-by field.
        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(1);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        PagingUtil<Pageable> pagingUtil = new PagingUtil<>(this.i18n, field -> null);
        Stream<Pageable> input = Stream.of(new Pageable("1", "a"));

        Exception exception = assertThrows(BadRequestException.class, () -> pagingUtil.applyPaging(input, 1));
        String errmsg = exception.getMessage();
        assertNotNull(errmsg);
        assertTrue(errmsg.contains("no sort-by field provided"));
    }

    public void testBadRequestExceptionWhenDefaultSortByIsUnsupportedWhenPagingCollection() {
        // This test verifies a BadRequestException is thrown in the case where the underlying
        // field comparator factory does not provide a default comparator, and paging has been
        // requested without specifying the sort-by field.
        PageRequest pageRequest = new PageRequest()
            .setPage(1)
            .setPerPage(1);

        ResteasyContext.pushContext(PageRequest.class, pageRequest);

        PagingUtil<Pageable> pagingUtil = new PagingUtil<>(this.i18n, field -> null);
        List<Pageable> input = List.of(new Pageable("1", "a"));

        Exception exception = assertThrows(BadRequestException.class, () -> pagingUtil.applyPaging(input));
        String errmsg = exception.getMessage();
        assertNotNull(errmsg);
        assertTrue(errmsg.contains("no sort-by field provided"));
    }
}
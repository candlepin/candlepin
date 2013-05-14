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
package org.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.paging.DataPresentation;

import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;

/**
 * PresentationInterceptorTest
 */
public class DataPresentationInterceptorTest {
    private I18n i18n;
    private DataPresentationInterceptor interceptor;
    private ResourceMethod rmethod;

    @Before
    public void setUp() throws Exception {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        interceptor = new DataPresentationInterceptor(i18n);
        rmethod = mock(ResourceMethod.class);
    }

    @Test
    public void testNoAnything() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertNull(p);
    }

    @Test
    public void testBothLimitAndOffset() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?limit=123&offset=456");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertEquals(new Integer(123), p.getLimit());
        assertEquals(new Integer(456), p.getOffset());
        assertNull(p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test
    public void testNoLimitButOffset() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?offset=123");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertEquals(DataPresentation.DEFAULT_LIMIT, p.getLimit());
        assertEquals(new Integer(123), p.getOffset());
        assertNull(p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test
    public void testLimitButNoOffset() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?limit=123");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertEquals(new Integer(123), p.getLimit());
        assertEquals(DataPresentation.DEFAULT_OFFSET, p.getOffset());
        assertNull(p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test(expected = BadRequestException.class)
    public void testBadIntegerValue() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?limit=foo&offset=456");

        interceptor.preProcess(req, rmethod);
    }

    @Test
    public void testNoPagingIfJustOrderAndSortBy() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?order=asc&sort_by=id");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertFalse(p.isPaging());
        assertEquals(DataPresentation.Order.ASCENDING, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test
    public void testUsesDefaultOrderIfNoOrderProvided() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?sort_by=id");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertFalse(p.isPaging());
        assertEquals(DataPresentation.DEFAULT_ORDER, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test
    public void testDescendingOrder() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?order=descending&sort_by=id");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
        assertFalse(p.isPaging());
        assertEquals(DataPresentation.Order.DESCENDING, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test(expected = BadRequestException.class)
    public void testOrderWithoutSortBy() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?order=descending");

        interceptor.preProcess(req, rmethod);

        DataPresentation p = ResteasyProviderFactory.getContextData(DataPresentation.class);
    }
}

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
package org.candlepin.common.resteasy.interceptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.jackson.DynamicFilterData;

import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;


/**
 * DynamicFilterInterceptorTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicFilterInterceptorTest {

    @Mock private HttpRequest request;

    private DynamicFilterInterceptor interceptor;
    private ResourceMethod rmethod;

    @Before
    public void init() {
        this.interceptor = new DynamicFilterInterceptor();
        rmethod = mock(ResourceMethod.class);
        ResteasyProviderFactory.popContextData(DynamicFilterData.class);
    }

    @Test
    public void testNoFilters() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET", "http://localhost/candlepin/status");
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNull(filterData);
    }

    @Test
    public void testSimpleBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?exclude=a2"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleMultiBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?exclude=a2&exclude=a3"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertTrue(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testNestedAttributeBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?exclude=a2&exclude=a3.c1"
        );
        interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?include=a2"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertTrue(filterData.isAttributeExcluded("a1"));
        assertTrue(filterData.isAttributeExcluded("a1.c1"));
        assertTrue(filterData.isAttributeExcluded("a1.c2"));
        assertFalse(filterData.isAttributeExcluded("a2"));
        assertFalse(filterData.isAttributeExcluded("a2.c1"));
        assertFalse(filterData.isAttributeExcluded("a2.c2"));
        assertTrue(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleMultiWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?include=a1&include=a3"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testNestedAttributeWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?include=a2&include=a3.c1"
        );
        interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertTrue(filterData.isAttributeExcluded("a1"));
        assertTrue(filterData.isAttributeExcluded("a1.c1"));
        assertTrue(filterData.isAttributeExcluded("a1.c2"));
        assertFalse(filterData.isAttributeExcluded("a2"));
        assertFalse(filterData.isAttributeExcluded("a2.c1"));
        assertFalse(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testIncludesWithExcludes() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?include=a.b1&exclude=a.b1.c2&exclude=a.b2&include=a.b2.d2"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        // a: { b1: { c1, c2, c3 }, b2: { d1, d2, d3 } }

        assertFalse(filterData.isAttributeExcluded("a"));
        assertFalse(filterData.isAttributeExcluded("a.b1"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c1"));
        assertTrue(filterData.isAttributeExcluded("a.b1.c2"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c3"));
        assertFalse(filterData.isAttributeExcluded("a.b2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d1"));
        assertFalse(filterData.isAttributeExcluded("a.b2.d2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d3"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d3")));
    }

    @Test
    public void testIncludesWithExcludesUsingWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create(
            "GET",
            "http://localhost/candlepin/status?" +
            "include=a.b1&exclude=a.b1.c2&include=a.b2.d2&filtermode=whitelist"
        );
        this.interceptor.preProcess(req, rmethod);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        // a: { b1: { c1, c2, c3 }, b2: { d1, d2, d3 } }

        assertFalse(filterData.isAttributeExcluded("a"));
        assertFalse(filterData.isAttributeExcluded("a.b1"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c1"));
        assertTrue(filterData.isAttributeExcluded("a.b1.c2"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c3"));
        assertFalse(filterData.isAttributeExcluded("a.b2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d1"));
        assertFalse(filterData.isAttributeExcluded("a.b2.d2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d3"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d3")));
    }
}

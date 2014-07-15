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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.jackson.DynamicFilterData;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * DynamicFilterInterceptorTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicFilterInterceptorTest {

    @Mock private HttpRequest request;
    @Mock private PinsetterKernel pinsetterKernel;

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
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df = new ClassForFilterTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNull(filterData);
    }

    @Test
    public void testSimpleBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?exclude=attributeTwo");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df = new ClassForFilterTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertFalse(filterData.isAttributeExcluded("attributeOne", df));
        assertTrue(filterData.isAttributeExcluded("attributeTwo", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectOne", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectTwo", df));
    }

    @Test
    public void testSimpleMultiBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status" +
            "?exclude=attributeTwo&exclude=otherObjectOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df = new ClassForFilterTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertFalse(filterData.isAttributeExcluded("attributeOne", df));
        assertTrue(filterData.isAttributeExcluded("attributeTwo", df));
        assertTrue(filterData.isAttributeExcluded("otherObjectOne", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectTwo", df));
    }

    @Test
    public void testEncapsulatedBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?exclude=attributeTwo" +
            "&exclude=otherObjectOne.attributeOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df =
            new ClassForFilterTesting("first", "second");
        ClassForFilterTesting innerdf =
            new ClassForFilterTesting("third", "fourth");
        df.setOtherObjectOne(innerdf);
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertFalse(filterData.isAttributeExcluded("attributeOne", df));
        assertTrue(filterData.isAttributeExcluded("attributeTwo", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectOne", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectTwo", df));
        assertTrue(filterData.isAttributeExcluded("attributeOne", innerdf));
        assertFalse(filterData.isAttributeExcluded("attributeTwo", innerdf));
    }

    @Test
    public void testSimpleWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?include=attributeTwo");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df = new ClassForFilterTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertTrue(filterData.isAttributeExcluded("attributeOne", df));
        assertFalse(filterData.isAttributeExcluded("attributeTwo", df));
        assertTrue(filterData.isAttributeExcluded("otherObjectOne", df));
        assertTrue(filterData.isAttributeExcluded("otherObjectTwo", df));
    }

    @Test
    public void testSimpleMultiWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?" +
            "include=attributeTwo&include=otherObjectOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df = new ClassForFilterTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertTrue(filterData.isAttributeExcluded("attributeOne", df));
        assertFalse(filterData.isAttributeExcluded("attributeTwo", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectOne", df));
        assertTrue(filterData.isAttributeExcluded("otherObjectTwo", df));
    }

    @Test
    public void testEncapsulatedWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?include=attributeTwo" +
            "&include=otherObjectOne.attributeOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        ClassForFilterTesting df =
            new ClassForFilterTesting("first", "second");
        ClassForFilterTesting innerdf =
            new ClassForFilterTesting("third", "fourth");
        df.setOtherObjectOne(innerdf);
        resp.setEntity(df);
        interceptor.postProcess(resp);
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertTrue(filterData.isAttributeExcluded("attributeOne", df));
        assertFalse(filterData.isAttributeExcluded("attributeTwo", df));
        assertFalse(filterData.isAttributeExcluded("otherObjectOne", df));
        assertTrue(filterData.isAttributeExcluded("otherObjectTwo", df));
        assertFalse(filterData.isAttributeExcluded("attributeOne", innerdf));
        assertTrue(filterData.isAttributeExcluded("attributeTwo", innerdf));
    }

    public class ClassForFilterTesting {

        private String attributeOne;
        private String attributeTwo;
        private ClassForFilterTesting otherObjectOne;
        private ClassForFilterTesting otherObjectTwo;

        public ClassForFilterTesting(String s1, String s2) {
            this.setAttributeOne(s1);
            this.setAttributeTwo(s2);
        }

        public String getAttributeOne() {
            return attributeOne;
        }

        public void setAttributeOne(String attributeOne) {
            this.attributeOne = attributeOne;
        }

        public String getAttributeTwo() {
            return attributeTwo;
        }

        public void setAttributeTwo(String attributeTwo) {
            this.attributeTwo = attributeTwo;
        }

        public ClassForFilterTesting getOtherObjectOne() {
            return otherObjectOne;
        }

        public void setOtherObjectOne(ClassForFilterTesting otherObjectOne) {
            this.otherObjectOne = otherObjectOne;
        }

        public ClassForFilterTesting getOtherObjectTwo() {
            return otherObjectTwo;
        }

        public void setOtherObjectTwo(ClassForFilterTesting otherObjectTwo) {
            this.otherObjectTwo = otherObjectTwo;
        }
    }
}

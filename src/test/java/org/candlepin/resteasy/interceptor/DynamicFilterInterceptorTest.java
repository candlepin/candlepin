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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.Serializable;
import java.util.Locale;

import org.candlepin.jackson.DynamicFilterable;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * DynamicFilterInterceptorTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicFilterInterceptorTest {

    @Mock private HttpRequest request;
    @Mock private PinsetterKernel pinsetterKernel;

    private DynamicFilterInterceptor interceptor;
    private I18n i18n;
    private ResourceMethod rmethod;

    @Before
    public void init() {
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        this.interceptor = new DynamicFilterInterceptor(i18n);
        rmethod = mock(ResourceMethod.class);
    }

    @Test
    public void testNoFilters() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterable df = new DynamicFilterableForTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertFalse(df.isAttributeFiltered("attributeOne"));
        assertFalse(df.isAttributeFiltered("attributeTwo"));
        assertFalse(df.isAttributeFiltered("otherObjectOne"));
        assertFalse(df.isAttributeFiltered("otherObjectTwo"));
    }

    @Test
    public void testSimpleBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?exclude=attributeTwo");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterable df = new DynamicFilterableForTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertFalse(df.isAttributeFiltered("attributeOne"));
        assertTrue(df.isAttributeFiltered("attributeTwo"));
        assertFalse(df.isAttributeFiltered("otherObjectOne"));
        assertFalse(df.isAttributeFiltered("otherObjectTwo"));
    }

    @Test
    public void testSimpleMultiBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status" +
            "?exclude=attributeTwo&exclude=otherObjectOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterable df = new DynamicFilterableForTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertFalse(df.isAttributeFiltered("attributeOne"));
        assertTrue(df.isAttributeFiltered("attributeTwo"));
        assertTrue(df.isAttributeFiltered("otherObjectOne"));
        assertFalse(df.isAttributeFiltered("otherObjectTwo"));
    }

    @Test
    public void testEncapsulatedBlacklist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?exclude=attributeTwo" +
            "&exclude=otherObjectOne.attributeOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterableForTesting df =
            new DynamicFilterableForTesting("first", "second");
        DynamicFilterableForTesting innerdf =
            new DynamicFilterableForTesting("third", "fourth");
        df.setOtherObjectOne(innerdf);
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertFalse(df.isAttributeFiltered("attributeOne"));
        assertTrue(df.isAttributeFiltered("attributeTwo"));
        assertFalse(df.isAttributeFiltered("otherObjectOne"));
        assertFalse(df.isAttributeFiltered("otherObjectTwo"));
        assertTrue(innerdf.isAttributeFiltered("attributeOne"));
        assertFalse(innerdf.isAttributeFiltered("attributeTwo"));
    }

    @Test
    public void testSimpleWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?include=attributeTwo");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterable df = new DynamicFilterableForTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertTrue(df.isAttributeFiltered("attributeOne"));
        assertFalse(df.isAttributeFiltered("attributeTwo"));
        assertTrue(df.isAttributeFiltered("otherObjectOne"));
        assertTrue(df.isAttributeFiltered("otherObjectTwo"));
    }

    @Test
    public void testSimpleMultiWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?" +
            "include=attributeTwo&include=otherObjectOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterable df = new DynamicFilterableForTesting("first", "second");
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertTrue(df.isAttributeFiltered("attributeOne"));
        assertFalse(df.isAttributeFiltered("attributeTwo"));
        assertFalse(df.isAttributeFiltered("otherObjectOne"));
        assertTrue(df.isAttributeFiltered("otherObjectTwo"));
    }

    @Test
    public void testEncapsulatedWhitelist() throws Exception {
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status?include=attributeTwo" +
            "&include=otherObjectOne.attributeOne");
        interceptor.preProcess(req, rmethod);

        ServerResponse resp = new ServerResponse();
        DynamicFilterableForTesting df =
            new DynamicFilterableForTesting("first", "second");
        DynamicFilterableForTesting innerdf =
            new DynamicFilterableForTesting("third", "fourth");
        df.setOtherObjectOne(innerdf);
        resp.setEntity(df);
        interceptor.postProcess(resp);
        assertTrue(df.isAttributeFiltered("attributeOne"));
        assertFalse(df.isAttributeFiltered("attributeTwo"));
        assertFalse(df.isAttributeFiltered("otherObjectOne"));
        assertTrue(df.isAttributeFiltered("otherObjectTwo"));
        assertFalse(innerdf.isAttributeFiltered("attributeOne"));
        assertTrue(innerdf.isAttributeFiltered("attributeTwo"));
    }

    private class DynamicFilterableForTesting extends AbstractHibernateObject {

        private String attributeOne;
        private String attributeTwo;
        private DynamicFilterableForTesting otherObjectOne;
        private DynamicFilterableForTesting otherObjectTwo;

        public DynamicFilterableForTesting(String s1, String s2) {
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

        public DynamicFilterableForTesting getOtherObjectOne() {
            return otherObjectOne;
        }

        public void setOtherObjectOne(DynamicFilterableForTesting otherObjectOne) {
            this.otherObjectOne = otherObjectOne;
        }

        public DynamicFilterableForTesting getOtherObjectTwo() {
            return otherObjectTwo;
        }

        public void setOtherObjectTwo(DynamicFilterableForTesting otherObjectTwo) {
            this.otherObjectTwo = otherObjectTwo;
        }

        @Override
        public Serializable getId() {
            return null;
        }
    }
}

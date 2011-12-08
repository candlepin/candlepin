/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.exceptions.mappers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.guice.I18nProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.xnap.commons.i18n.I18n;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

/**
 * TestExceptionMapperBase
 */
public abstract class TestExceptionMapperBase {
    protected Injector injector;
    protected HttpServletRequest req;
    protected RuntimeExceptionMapper rem;

    @Before
    public void init() {
        MapperTestModule mtm = new MapperTestModule(getMapperClass());
        injector = Guice.createInjector(mtm);
        req = injector.getInstance(HttpServletRequest.class);
        rem = injector.getInstance(RuntimeExceptionMapper.class);
    }

    protected String rtmsg(String msg) {
        return "Runtime Error " + msg;
    }

    protected void verifyMessage(Response r, String expectedmsg) {
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertNotNull(em);
        assertTrue(expectedmsg,
            em.getDisplayMessage().startsWith(expectedmsg));
    }

    public abstract Class getMapperClass();

    public static class MapperTestModule extends AbstractModule {
        private Class mapper;

        public MapperTestModule(Class clazz) {
            mapper = clazz;
        }

        @Override
        protected void configure() {
            bind(mapper);
            bind(I18n.class).toProvider(I18nProvider.class).asEagerSingleton();
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
        }

    }
}

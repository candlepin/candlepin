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
package org.candlepin.common.exceptions.mappers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.ExceptionMessage;
import org.candlepin.common.guice.CommonI18nProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.RequestScoped;

import org.jukito.JukitoModule;
import org.jukito.TestScope;
import org.junit.Before;
import org.xnap.commons.i18n.I18n;

import javax.servlet.ServletRequest;
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

    public static class MapperTestModule extends JukitoModule {
        private Class mapper;

        public MapperTestModule(Class clazz) {
            mapper = clazz;
        }

        @Override
        protected void configureTest() {
            bindScope(RequestScoped.class, TestScope.EAGER_SINGLETON);
            bind(mapper);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
            bind(ServletRequest.class).toInstance(mock(HttpServletRequest.class));
            bind(I18n.class).toProvider(CommonI18nProvider.class);
        }
    }
}

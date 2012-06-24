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
package org.candlepin.exceptions.mappers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.guice.I18nProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;



/**
 * CandlepinExceptionMapperTest
 */
public class CandlepinExceptionMapperTest {

    private Injector injector;
    private HttpServletRequest req;
    private CandlepinExceptionMapper cem;


    @Before
    public void init() {
        MapperTestModule mtm = new MapperTestModule();
        injector = Guice.createInjector(mtm);
        req = injector.getInstance(HttpServletRequest.class);
        cem = injector.getInstance(CandlepinExceptionMapper.class);
    }

    @Test
    public void defaultBuilder() {
        IOException ioe = new IOException("fake io exception");
        RuntimeException re = new RuntimeException("oops", ioe);
        ResponseBuilder bldr = cem.getDefaultBuilder(re, null,
            MediaType.APPLICATION_ATOM_XML_TYPE);
        Response r = bldr.build();
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        assertEquals(MediaType.APPLICATION_ATOM_XML_TYPE,
            r.getMetadata().get("Content-Type").get(0));
        assertTrue(em.getDisplayMessage().startsWith("Runtime Error " + re.getMessage()));
    }

    public static class MapperTestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(CandlepinExceptionMapper.class);
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
        }

    }
}

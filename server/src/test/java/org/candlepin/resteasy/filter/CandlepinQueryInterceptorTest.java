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
package org.candlepin.resteasy.filter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.model.Owner;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.SessionWrapper;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.persistence.EntityManager;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(JUnitParamsRunner.class)
public class CandlepinQueryInterceptorTest extends DatabaseTestFixture {

    private JsonProvider mockJsonProvider;
    private JsonFactory mockJsonFactory;
    private JsonGenerator mockJsonGenerator;
    private ObjectMapper mockObjectMapper;
    private OutputStream mockOutputStream;
    private Provider<EntityManager> emProvider;
    private Session session;

    @Override
    public void init() throws Exception {
        super.init();

        this.mockJsonProvider = mock(JsonProvider.class);
        this.mockJsonFactory = mock(JsonFactory.class);
        this.mockJsonGenerator = mock(JsonGenerator.class);
        this.mockObjectMapper = mock(ObjectMapper.class);
        this.mockOutputStream = mock(OutputStream.class);

        try {
            when(this.mockJsonProvider.locateMapper(any(Class.class), any(MediaType.class)))
                .thenReturn(this.mockObjectMapper);

            when(this.mockObjectMapper.getJsonFactory()).thenReturn(this.mockJsonFactory);

            when(this.mockJsonFactory.createGenerator(eq(this.mockOutputStream)))
                .thenReturn(this.mockJsonGenerator);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        // This entire block of mock objects is present to workaround a deadlock issue that occurs
        // when attempting to execute two queries against the same table with cursors and lazy
        // properties via hsqldb.
        this.emProvider = mock(Provider.class);
        Session currentSession = (Session) this.getEntityManager().getDelegate();
        this.session = spy(new SessionWrapper(currentSession));
        EntityManager mockEntityManager = mock(EntityManager.class);
        Session mockSession = mock(Session.class);
        SessionFactory mockSessionFactory = mock(SessionFactory.class);

        when(this.emProvider.get()).thenReturn(mockEntityManager);
        when(mockEntityManager.getDelegate()).thenReturn(mockSession);
        when(mockSession.getSessionFactory()).thenReturn(mockSessionFactory);
        when(mockSessionFactory.openSession()).thenReturn(this.session);
        doNothing().when(this.session).close();

        // Create some owners to play with
        for (int i = 0; i < 5; ++i) {
            this.createOwner("test-owner-" + (i + 1), "Test Owner " + (i + 1));
        }

        // Make sure we don't leave any page request on the context to muck with other tests
        ResteasyProviderFactory.popContextData(PageRequest.class);
    }

    @Test
    public void testWriteCandlepinQueryContents() throws IOException {
        List<Owner> owners = this.ownerCurator.listAll().list();

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ServerResponse response = new ServerResponse();
        response.setEntity(this.ownerCurator.listAll());

        cqi.postProcess(response);

        assertTrue(response.getEntity() instanceof StreamingOutput);

        ((StreamingOutput) response.getEntity()).write(this.mockOutputStream);

        verify(this.mockJsonGenerator, times(1)).writeStartArray();
        for (Owner owner : owners) {
            verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owner));
        }
        verify(this.mockJsonGenerator, times(1)).writeEndArray();
    }

    private Object[][] paramsForPaginatedContentTest() {
        return new Object[][] {
            new Object[] { 1, 5, "key", PageRequest.Order.ASCENDING },
            new Object[] { 1, 5, "key", PageRequest.Order.DESCENDING },
            new Object[] { 1, 1, "key", PageRequest.Order.ASCENDING },
            new Object[] { 1, 1, "key", PageRequest.Order.DESCENDING },
            new Object[] { 2, 2, "key", PageRequest.Order.ASCENDING },
            new Object[] { 2, 2, "key", PageRequest.Order.DESCENDING },
            new Object[] { 5, 10, "key", PageRequest.Order.ASCENDING },
            new Object[] { 5, 10, "key", PageRequest.Order.DESCENDING },
        };
    }

    @Test
    @Parameters(method = "paramsForPaginatedContentTest")
    public void testWritePaginatedCandlepinQueryContents(int page, int perPage, String sortBy,
        PageRequest.Order order) throws IOException {

        int offset = (page - 1) * perPage;
        int end = offset + perPage;

        List<Owner> owners = this.ownerCurator.listAll()
            .addOrder(order == PageRequest.Order.ASCENDING ? Order.asc(sortBy) : Order.desc(sortBy))
            .list();

        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setPerPage(perPage);
        pageRequest.setSortBy(sortBy);
        pageRequest.setOrder(order);

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ServerResponse response = new ServerResponse();
        response.setEntity(this.ownerCurator.listAll());

        ResteasyProviderFactory.pushContext(PageRequest.class, pageRequest);
        cqi.postProcess(response);

        assertTrue(response.getEntity() instanceof StreamingOutput);

        ((StreamingOutput) response.getEntity()).write(this.mockOutputStream);

        verify(this.mockJsonGenerator, times(1)).writeStartArray();
        for (int i = 0; i < owners.size(); ++i) {
            Owner owner = owners.get(i);

            if (i < offset || i >= end) {
                verify(this.mockObjectMapper, never()).writeValue(eq(this.mockJsonGenerator), eq(owner));
            }
            else {
                verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owner));
            }
        }
        verify(this.mockJsonGenerator, times(1)).writeEndArray();
    }

    @Test
    public void testNonCandlepinQueryObjectsAreIgnored() {
        // This test can't possibly be all-inclusive, so we'll just test most our common cases

        // List of entities
        List<Owner> owners = this.ownerCurator.listAll().list();

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ServerResponse response = new ServerResponse();
        response.setEntity(owners);

        cqi.postProcess(response);

        assertSame(owners, response.getEntity());

        // Single entity
        Owner owner = owners.get(0);
        response.setEntity(owner);

        cqi.postProcess(response);

        assertSame(owner, response.getEntity());
    }

    @Test
    public void shouldCloseSessionWhenExceptionOccurs() {
        doThrow(new RuntimeException()).when(this.mockJsonProvider)
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        final ServerResponse response = new ServerResponse();
        response.setEntity(this.ownerCurator.listAll());
        final CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(
            this.mockJsonProvider, this.emProvider);

        try {
            cqi.postProcess(response);
            fail("Should not happen!");
        }
        catch (RuntimeException e) {
            verify(this.session).close();
        }

    }

}

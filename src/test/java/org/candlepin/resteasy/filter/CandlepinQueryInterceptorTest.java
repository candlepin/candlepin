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

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

import org.candlepin.model.Owner;
import org.candlepin.paging.PageRequest;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.SessionWrapper;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;



/**
 * Test suite for the CandlepinQueryInterceptor
 */
public class CandlepinQueryInterceptorTest extends DatabaseTestFixture {

    private JsonProvider mockJsonProvider;
    private JsonFactory mockJsonFactory;
    private JsonGenerator mockJsonGenerator;
    private ObjectMapper mockObjectMapper;
    private OutputStream mockOutputStream;
    private Provider<EntityManager> emProvider;
    private Session session;

    @BeforeEach
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
        ResteasyContext.popContextData(PageRequest.class);
    }

    @Test
    public void testWriteCandlepinQueryContents() throws IOException {
        List<Owner> owners = this.ownerCurator.listAll().list();

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
        doReturn(this.ownerCurator.listAll()).when(responseContext).getEntity();

        cqi.filter(requestContext, responseContext);

        ArgumentCaptor<StreamingOutput> captor = ArgumentCaptor.forClass(StreamingOutput.class);
        verify(responseContext, times(1)).setEntity(captor.capture());

        ((StreamingOutput) captor.getValue()).write(this.mockOutputStream);

        verify(this.mockJsonGenerator, times(1)).writeStartArray();
        for (Owner owner : owners) {
            verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owner));
        }
        verify(this.mockJsonGenerator, times(1)).writeEndArray();
    }

    private static Stream<Object[]> paramsForPaginatedContentTest() {
        return Stream.of(
            new Object[] { 1, 5, "key", PageRequest.Order.ASCENDING },
            new Object[] { 1, 5, "key", PageRequest.Order.DESCENDING },
            new Object[] { 1, 1, "key", PageRequest.Order.ASCENDING },
            new Object[] { 1, 1, "key", PageRequest.Order.DESCENDING },
            new Object[] { 2, 2, "key", PageRequest.Order.ASCENDING },
            new Object[] { 2, 2, "key", PageRequest.Order.DESCENDING },
            new Object[] { 5, 10, "key", PageRequest.Order.ASCENDING },
            new Object[] { 5, 10, "key", PageRequest.Order.DESCENDING }
        );
    }

    @ParameterizedTest
    @MethodSource("paramsForPaginatedContentTest")
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

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
        doReturn(this.ownerCurator.listAll()).when(responseContext).getEntity();

        ResteasyContext.pushContext(PageRequest.class, pageRequest);
        cqi.filter(requestContext, responseContext);

        ArgumentCaptor<StreamingOutput> captor = ArgumentCaptor.forClass(StreamingOutput.class);
        verify(responseContext, times(1)).setEntity(captor.capture());

        ((StreamingOutput) captor.getValue()).write(this.mockOutputStream);

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

    // These tests can't possibly be all-inclusive, so we'll just test most our common cases

    @Test
    public void testNonCandlepinQueryCollectionsAreIgnored() {
        // List of entities
        List<Owner> owners = this.ownerCurator.listAll().list();

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
        doReturn(owners).when(responseContext).getEntity();

        cqi.filter(requestContext, responseContext);

        verify(responseContext, never()).setEntity(any());
    }


    @Test
    public void testNonCandlepinQueryObjectsAreIgnored() {
        // Single entity
        Owner owner = this.ownerCurator.listAll().list().get(0);

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
        doReturn(owner).when(responseContext).getEntity();

        cqi.filter(requestContext, responseContext);

        verify(responseContext, never()).setEntity(any());
    }

    @Test
    public void shouldCloseSessionWhenExceptionOccurs() {
        doThrow(new RuntimeException()).when(this.mockJsonProvider)
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        CandlepinQueryInterceptor cqi = new CandlepinQueryInterceptor(this.mockJsonProvider, this.emProvider);

        try {
            ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
            ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
            doReturn(this.ownerCurator.listAll()).when(responseContext).getEntity();

            cqi.filter(requestContext, responseContext);

            fail("An expected exception was not thrown");
        }
        catch (RuntimeException e) {
            verify(this.session).close();
        }

    }

}

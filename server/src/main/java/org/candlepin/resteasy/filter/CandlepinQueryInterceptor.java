/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ResultIterator;
import org.candlepin.resteasy.JsonProvider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;

import java.util.Objects;

import javax.persistence.EntityManager;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

/**
 * The CandlepinQueryInterceptor handles the streaming of a query and applies any paging
 * configuration.
 */
@javax.ws.rs.ext.Provider
@ServerInterceptor
public class CandlepinQueryInterceptor implements PostProcessInterceptor {

    protected final JsonProvider jsonProvider;
    protected final Provider<EntityManager> emProvider;

    @Inject
    public CandlepinQueryInterceptor(
        final JsonProvider jsonProvider, final Provider<EntityManager> emProvider) {
        this.jsonProvider = Objects.requireNonNull(jsonProvider);
        this.emProvider = Objects.requireNonNull(emProvider);
    }

    /**
     * Opens a new session from the current session's session factory.
     *
     * @return a newly opened session
     */
    protected Session openSession() {
        final Session currentSession = (Session) this.emProvider.get().getDelegate();
        final SessionFactory factory = currentSession.getSessionFactory();

        return factory.openSession();
    }

    @Override
    public void postProcess(ServerResponse response) {
        final Object entity = response.getEntity();

        if (entity instanceof CandlepinQuery) {
            final PageRequest pageRequest = ResteasyProviderFactory.getContextData(PageRequest.class);
            final Session session = this.openSession();
            try {
                final CandlepinQuery query = (CandlepinQuery) entity;

                // Use a separate session so we aren't at risk of lazy loading or interceptors closing
                // our cursor mid-stream.
                query.useSession(session);

                // Apply any paging config we may have
                applyPaging(pageRequest, query);

                // Set the output streamer that will stream our query result
                response.setEntity(buildOutputStreamer(session, query));
            }
            catch (final RuntimeException e) {
                if (session != null) {
                    session.close();
                }
                throw e;
            }
        }
    }

    private void applyPaging(final PageRequest pageRequest, final CandlepinQuery query) {
        if (pageRequest == null) {
            return;
        }
        // Impl note:
        // Sorting will always be required (for consistency) if a page request object is
        // present -- either isPaging() will be true, or we'll have ordering config.
        final String sortField = pageRequest.getSortBy() != null ?
            pageRequest.getSortBy() :
            AbstractHibernateObject.DEFAULT_SORT_FIELD;

        final PageRequest.Order order = pageRequest.getOrder() != null ?
            pageRequest.getOrder() :
            PageRequest.DEFAULT_ORDER;

        query.addOrder(order == PageRequest.Order.DESCENDING ?
            Order.desc(sortField) :
            Order.asc(sortField)
        );

        if (pageRequest.isPaging()) {
            query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            query.setMaxResults(pageRequest.getPerPage());

            // Create a page object for the link header response
            final Page page = new Page();
            page.setMaxRecords(query.getRowCount()); // This is expensive :(
            page.setPageRequest(pageRequest);
            // Note: we don't need to store the page data in the page

            ResteasyProviderFactory.pushContext(Page.class, page);
        }
    }

    private StreamingOutput buildOutputStreamer(
        final Session session, final CandlepinQuery query) {

        final ObjectMapper mapper = this.jsonProvider
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        return stream -> {
            try (
                final JsonGenerator generator = mapper.getJsonFactory().createGenerator(stream);
                final ResultIterator<Object> iterator = query.iterate()) {

                generator.writeStartArray();

                while (iterator.hasNext()) {
                    mapper.writeValue(generator, iterator.next());
                }

                generator.writeEndArray();
                generator.flush();
            }
            finally {
                if (session != null) {
                    session.close();
                }
            }
        };
    }

}

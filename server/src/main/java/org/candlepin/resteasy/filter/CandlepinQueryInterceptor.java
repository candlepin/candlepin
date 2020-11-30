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
import org.candlepin.resteasy.CustomResteasyJackson2Provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.jboss.resteasy.core.ResteasyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;



/**
 * The CandlepinQueryInterceptor handles the streaming of a query and applies any paging
 * configuration.
 */
@Component
@javax.ws.rs.ext.Provider
public class CandlepinQueryInterceptor implements ContainerResponseFilter {

    protected final CustomResteasyJackson2Provider jackson2Provider;
    protected final Provider<EntityManager> emProvider;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @PersistenceContext
    public EntityManager entityManager;

    @Autowired
    public CandlepinQueryInterceptor(final CustomResteasyJackson2Provider jackson2Provider,
        final Provider<EntityManager> emProvider) {
        this.jackson2Provider = Objects.requireNonNull(jackson2Provider);
        this.emProvider = Objects.requireNonNull(emProvider);
    }

    /**
     * Opens a new session from the current session's session factory.
     *
     * @return a newly opened session
     */
    protected Session openSession() {
//        Session currentSession = (Session) this.emProvider.get().getDelegate();
//        SessionFactory factory = currentSession.getSessionFactory();
//        return factory.openSession();
        Session session;
        System.out.println("Candlepin Query Interceptor: opening a fresh session");
        session = entityManagerFactory.unwrap(SessionFactory.class).openSession();
        return session;
//        Session currentSession = entityManager.unwrap(Session.class);
//        SessionFactory sessionFactory = currentSession.getSessionFactory();
//        return sessionFactory.openSession();
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        /* TODO: spring- Remove this print statement */
        System.out.println("CandlepinQueryInterceptor");

        Object entity = responseContext.getEntity();

        if (entity instanceof CandlepinQuery) {
            PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
            Session session = this.openSession();

            try {
                CandlepinQuery query = (CandlepinQuery) entity;

                // Use a separate session so we aren't at risk of lazy loading or interceptors closing
                // our cursor mid-stream.
                query.useSession(session);

                // Apply any paging config we may have
                this.applyPaging(pageRequest, query);

                // Set the output streamer that will stream our query result
                responseContext.setEntity(this.buildOutputStreamer(session, query));
            }
            catch (RuntimeException e) {
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
            Page page = new Page();
            page.setMaxRecords(query.getRowCount()); // This is expensive :(
            page.setPageRequest(pageRequest);
            // Note: we don't need to store the page data in the page

            ResteasyContext.pushContext(Page.class, page);
        }
    }

    private StreamingOutput buildOutputStreamer(Session session, CandlepinQuery query) {
        ObjectMapper mapper = this.jackson2Provider
            .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        return stream -> {
            try (
                JsonGenerator generator = mapper.getJsonFactory().createGenerator(stream);
                ResultIterator<Object> iterator = query.iterate()) {

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

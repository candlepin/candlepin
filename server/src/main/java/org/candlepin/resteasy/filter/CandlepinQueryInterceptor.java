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

import com.google.inject.Inject;
import com.google.inject.Provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

import javax.persistence.EntityManager;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;



/**
 * The CandlepinQueryInterceptor handles the streaming of a query and applies any paging
 * configuration.
 */
@javax.ws.rs.ext.Provider
@ServerInterceptor
public class CandlepinQueryInterceptor implements PostProcessInterceptor {
    private static Logger log = LoggerFactory.getLogger(CandlepinQueryInterceptor.class);

    protected JsonProvider jsonProvider;
    protected Provider<EntityManager> emProvider;

    @Inject
    public CandlepinQueryInterceptor(JsonProvider jsonProvider, Provider<EntityManager> emProvider) {
        this.jsonProvider = jsonProvider;
        this.emProvider = emProvider;
    }

    protected Session openSession() {
        Session currentSession = (Session) this.emProvider.get().getDelegate();
        SessionFactory factory = currentSession.getSessionFactory();

        return factory.openSession();
    }

    @Override
    public void postProcess(ServerResponse response) {
        Object entity = response.getEntity();

        if (entity instanceof CandlepinQuery) {
            final PageRequest pageRequest = ResteasyProviderFactory.getContextData(PageRequest.class);
            final Session session = this.openSession();
            final CandlepinQuery query = (CandlepinQuery) entity;
            final ObjectMapper mapper = this.jsonProvider
                .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

            // Use a separate session so we aren't at risk of lazy loading or interceptors closing
            // our cursor mid-stream.
            query.useSession(session);

            // Apply any paging config we may have
            if (pageRequest != null) {
                // Impl note:
                // Sorting will always be required (for consistency) if a page request object is
                // present -- either isPaging() will be true, or we'll have ordering config.
                String sortField = pageRequest.getSortBy() != null ?
                    pageRequest.getSortBy() :
                    AbstractHibernateObject.DEFAULT_SORT_FIELD;

                PageRequest.Order order = pageRequest.getOrder() != null ?
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

                    ResteasyProviderFactory.pushContext(Page.class, page);
                }
            }

            // Set the output streamer that will stream our query result
            response.setEntity(new StreamingOutput() {
                @Override
                public void write(OutputStream stream) throws IOException, WebApplicationException {
                    JsonGenerator generator = mapper.getJsonFactory().createGenerator(stream);
                    ResultIterator<Object> iterator = query.iterate();

                    generator.writeStartArray();

                    while (iterator.hasNext()) {
                        mapper.writeValue(generator, iterator.next());
                    }

                    generator.writeEndArray();
                    generator.flush();
                    generator.close();

                    iterator.close();

                    session.close();
                }
            });
        }
    }

}

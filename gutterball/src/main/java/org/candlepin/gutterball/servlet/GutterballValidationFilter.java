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
package org.candlepin.gutterball.servlet;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;

import javax.inject.Provider;
import javax.persistence.EntityManagerFactory;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.IOException;


/**
 * The GutterballValidationFilter class adds entity validation filtering to Hibernate through the
 * request filters.
 */
@Singleton
public class GutterballValidationFilter implements Filter {

    Provider<EntityManagerFactory> factoryProvider;
    Provider<BeanValidationEventListener> listenerProvider;

    @Inject
    public GutterballValidationFilter(Provider<EntityManagerFactory> factoryProvider,
        Provider<BeanValidationEventListener> listenerProvider) {

        this.factoryProvider = factoryProvider;
        this.listenerProvider = listenerProvider;
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        HibernateEntityManagerFactory hibernateEntityManagerFactory =
            (HibernateEntityManagerFactory) this.factoryProvider.get();
        SessionFactoryImpl sessionFactoryImpl =
            (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
        EventListenerRegistry registry =
            sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);

        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(this.listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(this.listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(this.listenerProvider.get());
    }

    public void destroy() {
        // noop
    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
        final FilterChain filterChain) throws IOException, ServletException {
        filterChain.doFilter(servletRequest, servletResponse);
    }

}

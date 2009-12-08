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
package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementPoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.UserResource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.persist.jpa.JpaUnit;

/**
 * configure Guice with the resource classes.
 */
public class JerseyGuiceConfiguration extends GuiceServletContextListener {

    /** {@inheritDoc} */
    @Override
    protected Injector getInjector() {
        return Guice.createInjector(
            PersistenceService.usingJpa()
                .across(UnitOfWork.TRANSACTION)
                .buildModule(),

            new ServletModule() {
                /** {@inheritDoc} */
                @Override
                protected void configureServlets() {
                    
                    bind(JPAInitializer.class).asEagerSingleton();
                    bindConstant().annotatedWith(JpaUnit.class).to("production");
                    
                    bind(CertificateResource.class);
                    bind(ConsumerResource.class);
                    bind(EntitlementPoolResource.class);
                    bind(EntitlementResource.class);
                    bind(OwnerResource.class);
                    bind(ProductResource.class);
                    bind(UserResource.class);
                    
                    serve("/*").with(GuiceContainer.class);
                }
        });
    }

}

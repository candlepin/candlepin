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

/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://jersey.dev.java.net/CDDL+GPL.html
 * or jersey/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at jersey/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.fedoraproject.candlepin.client.test;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;

import org.fedoraproject.candlepin.CandlepinCommonTestingModule;
import org.fedoraproject.candlepin.model.AttributeCurator;
import org.fedoraproject.candlepin.model.CertificateCurator;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.DateSourceForTesting;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.util.DateSource;

import org.junit.After;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.persist.WorkManager;

public abstract class AbstractGuiceGrizzlyTest {
    private static final Logger LOGGER = Logger.getLogger(
            AbstractGuiceGrizzlyTest.class.getName());
    public static final String CONTEXT = "/test";
    private final int port = 9997;
    private final URI baseUri = getUri().build();
    private GrizzlyWebServer ws;
    private GuiceFilter f;

    protected EntityManagerFactory emf;
    protected Injector injector;
    
    protected OwnerCurator ownerCurator;
    protected ProductCurator productCurator;
    protected ProductServiceAdapter productAdapter;
    protected SubscriptionServiceAdapter subAdapter;
    protected ConsumerCurator consumerCurator;
    protected ConsumerIdentityCertificateCurator consumerIdCertCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected CertificateCurator certificateCurator;
    protected PoolCurator poolCurator;
    protected DateSourceForTesting dateSource;
    protected SpacewalkCertificateCurator spacewalkCertCurator;
    protected EntitlementCurator entitlementCurator;
    protected AttributeCurator attributeCurator;
    protected RulesCurator rulesCurator;
    protected SubscriptionCurator subCurator;
    protected WorkManager unitOfWork;
    protected HttpServletRequest httpServletRequest;
    
    public void setUp() throws Exception {
        startServer(TestServletConfig.class);
        injector = TestServletConfig.getServletInjector();
        initializeCurators();
    }
    
    public void initializeCurators() {
        injector = TestServletConfig.getServletInjector();
        
        injector.getInstance(EntityManagerFactory.class); 
        emf = injector.getProvider(EntityManagerFactory.class).get();
        
        ownerCurator = injector.getInstance(OwnerCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        consumerIdCertCurator = injector.getInstance(ConsumerIdentityCertificateCurator.class);
        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        certificateCurator = injector.getInstance(CertificateCurator.class);
        poolCurator = injector.getInstance(PoolCurator.class);
        spacewalkCertCurator = injector.getInstance(SpacewalkCertificateCurator.class);
        entitlementCurator = injector.getInstance(EntitlementCurator.class);
        attributeCurator = injector.getInstance(AttributeCurator.class);
        rulesCurator = injector.getInstance(RulesCurator.class);
        subCurator = injector.getInstance(SubscriptionCurator.class);
        unitOfWork = injector.getInstance(WorkManager.class);
        productAdapter = injector.getInstance(ProductServiceAdapter.class);
        subAdapter = injector.getInstance(SubscriptionServiceAdapter.class);
       
        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));

    }

    public UriBuilder getUri() {
        return UriBuilder.fromUri("http://localhost").port(port).path(CONTEXT);
    }

    public <T extends GuiceServletContextListener> void startServer(Class<T> c) {
        LOGGER.info("Starting grizzly...");

        ws = new GrizzlyWebServer(port);

        ServletAdapter sa = new ServletAdapter();

        sa.addServletListener(c.getName());
        
        f = new GuiceFilter();
        sa.addFilter(f, "guiceFilter", null);

        sa.setContextPath(baseUri.getRawPath());

        ws.addGrizzlyAdapter(sa, new String[] {""});

        try {
            ws.start();
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Stop the embedded Grizzly server.
     * @throws java.lang.Exception
     */
    private void stopGrizzly() throws Exception {
        try {
            if (ws != null) {
                // Work around bug in Grizzly
                f.destroy();
                ws.stop();
                ws = null;
            }
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not stop grizzly...", e);
        }
    }
    
    @After
    public void tearDown() throws Exception {
        LOGGER.info("tearDown...");
        stopGrizzly();
        LOGGER.info("done...");
    }

    public WebResource resource() {
        final Client c = Client.create();
        final WebResource rootResource = c.resource(getUri().build());
        return rootResource;
    }
    
    public static class TestServletConfig extends GuiceServletContextListener {

        private static Injector servletInjector;

        public static Injector getServletInjector() {
            return servletInjector;
        }
        
        @Override
        protected Injector getInjector() {
            
            servletInjector = Guice.createInjector(
                    new CandlepinCommonTestingModule(),
                    
                    PersistenceService.usingJpa()
                        .across(UnitOfWork.REQUEST)
                        .buildModule(),
                        
                    new ServletModule() {
                        @Override
                        protected void configureServlets() {
                            serve("*").with(GuiceContainer.class);
                        }
                    }
            );

            return servletInjector;
        }
    }
}

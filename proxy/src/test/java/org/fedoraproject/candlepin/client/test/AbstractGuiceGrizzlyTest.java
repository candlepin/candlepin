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

import javax.ws.rs.core.UriBuilder;

import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.After;

import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public abstract class AbstractGuiceGrizzlyTest extends DatabaseTestFixture {
    private static final Logger LOGGER = Logger.getLogger(AbstractGuiceGrizzlyTest.class.getName());

    public static final String CONTEXT = "/test";

    private final int port = 9997;

    private final URI baseUri = getUri().build();

    private GrizzlyWebServer ws;

    private GuiceFilter f;

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

        ws.addGrizzlyAdapter(sa, new String[] {""} );

        try {
            ws.start();
        } catch (IOException ex) {
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
        } catch( Exception e ) {
            LOGGER.log(Level.WARNING, "Could not stop grizzly...", e );
        }
    }
    
    @After
    public void tearDown() throws Exception {
        LOGGER.info( "tearDown..." );
        stopGrizzly();
        LOGGER.info( "done..." );
    }

    public WebResource resource() {
        final Client c = Client.create();
        final WebResource rootResource = c.resource(getUri().build());
        return rootResource;
    }
}
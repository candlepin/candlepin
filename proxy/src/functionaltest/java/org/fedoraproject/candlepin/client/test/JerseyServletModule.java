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
package org.fedoraproject.candlepin.client.test;

import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author paulsandoz
 */
public class JerseyServletModule extends ServletModule {
    private Set<Class<?>> clazzes = new HashSet<Class<?>>();

    private String path = "/*";

    private Map<String, String> initParams = new HashMap<String, String>();

    private Class<? extends GuiceContainer> servletClass = GuiceContainer.class;
    
    public JerseyServletModule servlet(Class<? extends GuiceContainer> servletClass) {
        this.servletClass = servletClass;
        return this;
    }

    public JerseyServletModule path(String path) {
        this.path = path;
        return this;
    }

    public JerseyServletModule bindClass(Class<?> clazz) {
        clazzes.add(clazz);
        return this;
    }

    public JerseyServletModule initParam(String name, String value) {
        initParams.put(name, value);
        return this;
    }

    @Override
    protected void configureServlets() {
        configureBind();
        configureServe();
    }

    protected void configureBind() {
        for (Class<?> clazz : clazzes) {
            bind(clazz);
        }
    }
    
    protected void configureServe() {
        serve(path).with(servletClass, initParams);
    }
}

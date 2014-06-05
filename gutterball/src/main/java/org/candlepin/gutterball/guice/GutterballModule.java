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
package org.candlepin.gutterball.guice;

import org.candlepin.gutterball.resource.StatusResource;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.servlet.ServletScopes;

import org.xnap.commons.i18n.I18n;

/**
 * Simple module to perform bindings.
 */
public class GutterballModule extends AbstractModule {

    private Module configModule;

    public GutterballModule(Module configModule) {
        this.configModule = configModule;
    }

    @Override
    protected void configure() {
        install(configModule);

        // See JavaDoc on I18nProvider for more information of RequestScope
        bind(I18n.class).toProvider(I18nProvider.class).in(ServletScopes.REQUEST);
        bind(StatusResource.class);
    }

}

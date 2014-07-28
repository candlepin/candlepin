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

import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.receive.EventReceiver;
import org.candlepin.gutterball.resource.EventResource;
import org.candlepin.gutterball.resource.StatusResource;
import org.candlepin.gutterball.resteasy.JsonProvider;
import org.xnap.commons.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletScopes;
import com.mongodb.DB;
import com.mongodb.MongoClient;


/**
 * GutterballModule configures the modules used by Gutterball using Guice.
 */
public class GutterballModule extends AbstractModule {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure() {
        // See JavaDoc on I18nProvider for more information of RequestScope
        bind(I18n.class).toProvider(I18nProvider.class).in(ServletScopes.REQUEST);
        bind(JsonProvider.class);

        // Backend classes
        bind(EventReceiver.class).asEagerSingleton();

        // It is safe to share a single instance of the mongodb connection
        bind(MongoClient.class).toProvider(MongoDBClientProvider.class).in(Singleton.class);
        // FIXME: Determine if we need to share the DB connection.
        bind(DB.class).toProvider(MongoDBProvider.class).in(Singleton.class);

        // Bind curators
        bind(EventCurator.class);

        // RestEasy API resources
        bind(StatusResource.class);
        bind(EventResource.class);
    }
}

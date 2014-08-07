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

import org.candlepin.gutterball.eventhandler.EventHandler;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.eventhandler.HandlerTarget;
import org.candlepin.gutterball.receive.EventReceiver;
import org.candlepin.gutterball.report.ConsumerStatusReport;
import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.resource.EventResource;
import org.candlepin.gutterball.resource.ReportsResource;
import org.candlepin.gutterball.resource.StatusResource;
import org.candlepin.gutterball.resteasy.JsonProvider;
import org.candlepin.gutterball.util.EventHandlerLoader;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.servlet.ServletScopes;

import org.xnap.commons.i18n.I18n;


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
        configureEventHandlers();
        bind(EventManager.class).asEagerSingleton();
        bind(EventReceiver.class).asEagerSingleton();

        // Map our report classes so that they can be picked up by the ReportFactory.
        Multibinder<Report> reports = Multibinder.newSetBinder(binder(), Report.class);
        reports.addBinding().to(ConsumerStatusReport.class);
        bind(ReportFactory.class);

        // RestEasy API resources
        bind(StatusResource.class);
        bind(EventResource.class);
        bind(ReportsResource.class);
    }

    private void configureEventHandlers() {
        MapBinder<String, EventHandler> eventBinder =
                MapBinder.newMapBinder(binder(), String.class, EventHandler.class);
        for (Class<? extends EventHandler> clazz : EventHandlerLoader.getClasses()) {
            if (clazz.isAnnotationPresent(HandlerTarget.class)) {
                HandlerTarget targetAnnotation = clazz.getAnnotation(HandlerTarget.class);
                eventBinder.addBinding(targetAnnotation.value()).to(clazz).asEagerSingleton();
            }
        }
    }
}

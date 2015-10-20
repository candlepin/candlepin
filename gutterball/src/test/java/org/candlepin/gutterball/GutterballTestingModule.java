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

package org.candlepin.gutterball;

import static org.mockito.Mockito.mock;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.guice.JPAInitializer;
import org.candlepin.gutterball.guice.GutterballModule;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.receiver.EventReceiver;

import com.google.inject.persist.jpa.JpaPersistModule;

import org.xnap.commons.i18n.I18n;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A guice module used for setting up injectable components for testing gutterball.
 *
 */
public class GutterballTestingModule extends GutterballModule {

    private HttpServletRequest request = mock(HttpServletRequest.class);
    private EventReceiver eventReciever;

    public GutterballTestingModule(Configuration config) {
        super(config);
    }

    @Override
    protected void configure() {
        bind(Configuration.class).toInstance(this.config);

        bind(HttpServletRequest.class).toInstance(request);
        bind(HttpServletResponse.class).toInstance(mock(HttpServletResponse.class));

        super.configure();
    }

    @Override
    protected void bindI18n() {
        // TODO Auto-generated method stub
        bind(I18nProvider.class).toInstance(new I18nProvider(request));
        bind(I18n.class).toProvider(I18nProvider.class);
    }

    @Override
    protected void configureJPA() {
        install(new JpaPersistModule("testing"));
//        install(new JpaPersistModule("postgresql-test"));
        bind(JPAInitializer.class).asEagerSingleton();
    }

    @Override
    protected void configureEventReciever() {
        eventReciever = mock(EventReceiver.class);
        bind(EventReceiver.class).toInstance(eventReciever);
    }

    @Override
    protected void configureTasks() {
        // No need to configure/run tasks during testing.
        // Tasks should be tested in isolation.
    }

}

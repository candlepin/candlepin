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
package org.candlepin.guice;

import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.pinsetter.core.GuiceJobFactory;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.ExportCleaner;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Names;

import org.quartz.JobListener;
import org.quartz.spi.JobFactory;

/**
 * PinsetterModule - Guice module specific to pinsetter jobs, overrides the usual
 * {@link CandlepinModule}.
 */
public class PinsetterModule extends AbstractModule {

    @Override
    public void configure() {
        SimpleScope pinsetterJobScope = new SimpleScope();
        bindScope(PinsetterJobScoped.class, pinsetterJobScope);
        bind(SimpleScope.class).annotatedWith(Names.named("PinsetterJobScope")).toInstance(pinsetterJobScope);

        this.configurePinsetter();
    }

    @Provides
    @PinsetterJobScoped
    protected EventSink getScopedEventSink(Injector injector) {
        return injector.getInstance(EventSinkImpl.class);
    }

    private void configurePinsetter() {
        bind(JobFactory.class).to(GuiceJobFactory.class);
        bind(JobListener.class).to(PinsetterJobListener.class);
        bind(PinsetterKernel.class);
        bind(CertificateRevocationListTask.class);
        bind(JobCleaner.class);
        bind(ExportCleaner.class);
        bind(UnpauseJob.class);
        bind(SweepBarJob.class);
    }

}

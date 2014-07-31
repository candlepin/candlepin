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

import org.candlepin.gutterball.report.ConsumerStatusReport;
import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.resource.ReportsResource;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * A guice module that sets up the necessary bingings for the report API.
 */
public class ReportModule extends AbstractModule {

    @SuppressWarnings("rawtypes")
    @Override
    protected void configure() {
        // Map our report classes so that they can be picked up by the ReportFactory.
        Multibinder<Report> reports = Multibinder.newSetBinder(binder(), Report.class);
        reports.addBinding().to(ConsumerStatusReport.class);
        bind(ReportFactory.class).asEagerSingleton();

        bind(ReportsResource.class);
    }

}

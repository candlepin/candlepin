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

package org.candlepin.gutterball.report;

import com.google.inject.Inject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A factory responsible for mapping a report by report key. All guice bound reports
 * are stored and are accessible via this factory.
 */
public class ReportFactory {

    private Map<String, Report> reports;

    @Inject
    public ReportFactory(Set<Report> reportSet) {
        reports = new HashMap<String, Report>();
        for (Report r : reportSet) {
            reports.put(r.getKey(), r);
        }
    }

    public Collection<Report> getReports() {
        return this.reports.values();
    }

    public Report getReport(String key) {
        return this.reports.get(key);
    }

}

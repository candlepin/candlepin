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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.Inject;

/**
 * A factory responsible for mapping a report by report key. All guice bound reports
 * are stored and are accessible via this factory.
 */
public class ReportFactory {

    private Map<String, Report> reports;

    @Inject
    public ReportFactory(Set<Report> reportSet) {
        // Could have used a BindMap but lose generics across classes.
        // Just build map from the injected set and we won't have to specify
        // the report key at bind time.
        reports = new HashMap<String, Report>();
        for (Report r : reportSet) {
            reports.put(r.getKey(), r);
        }
    }

    public List<Report> getReports() {
        return new ArrayList<Report>(this.reports.values());
    }

    public Report getReport(String key) {
        return this.reports.get(key);
    }

}

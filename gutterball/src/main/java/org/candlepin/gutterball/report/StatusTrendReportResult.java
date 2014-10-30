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

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;



/**
 * StatusTrendReportResult represents the result set returned by the status trend report.
 * <p/>
 * The result is a map of maps, with the outer map mapping the dates to the inner map which maps the
 * statuses to their respective counts.
 */
public class StatusTrendReportResult extends TreeMap<Date, Map<String, Integer>> implements ReportResult {

    /**
     * Creates a new report results instance pre-populated with the specified results. Once created,
     * the given result map may be modified without affecting the state of the new report result
     * object.
     *
     * @param results
     *  A mapping of dates to status counts
     */
    public StatusTrendReportResult(Map<Date, Map<String, Integer>> results) {
        super(results);
    }

    // Nothing to do.
}

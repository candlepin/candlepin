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

import org.candlepin.gutterball.model.snapshot.ComplianceSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * ConsumerTrendReportResult map of consumer uuid -> collection of compliance data
 */
public class ConsumerTrendReportResult extends HashMap<String, Set<ComplianceSnapshot>>
    implements ReportResult {

    public void add(String consumerUuid, ComplianceSnapshot snapshotToAdd) {
        Set<ComplianceSnapshot> appendTo = get(consumerUuid);
        if (appendTo == null) {
            appendTo = new HashSet<ComplianceSnapshot>();
            put(consumerUuid, appendTo);
        }
        appendTo.add(snapshotToAdd);
    }

}

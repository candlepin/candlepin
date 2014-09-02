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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * StatusTrendReportResult map of consumer uuid -> collection of compliance data
 */
public class StatusTrendReportResult extends BasicDBObject implements ReportResult {

    public BasicDBObject add(String key, DBObject value) {
        BasicDBList appendTo = getList(key);
        if (appendTo == null) {
            appendTo = new BasicDBList();
            this.put(key, appendTo);
            // Don't need to check equality here
        }
        appendTo.add(value);
        return this;
    }

    protected BasicDBList getList(String key) {
        return (BasicDBList) get(key);
    }
}

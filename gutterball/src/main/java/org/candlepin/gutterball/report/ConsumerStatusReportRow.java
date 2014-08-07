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

/**
 * A result row returned by the {@link ConsumerStatusReport} object.
 */
public class ConsumerStatusReportRow {
    private String hostName;
    private String systemId;
    private String status;
    private String org;
    private Date lastCheckIn;

    /**
    * @param hostName
    * @param systemId
    * @param status
    * @param sateliteServer
    * @param org
    * @param lastCheckIn
    * @param lifeCycleDate
    */
    public ConsumerStatusReportRow(String hostName, String systemId, String status, String org,
            Date lastCheckIn) {
        this.hostName = hostName;
        this.systemId = systemId;
        this.status = status;
        this.org = org;
        this.lastCheckIn = lastCheckIn;
    }

    /**
    * @return the hostName
    */
    public String getHostName() {
        return hostName;
    }

    /**
    * @return the systemId
    */
    public String getSystemId() {
        return systemId;
    }
    /**
    * @return the status
    */
    public String getStatus() {
        return status;
    }

    /**
    * @return the org
    */
    public String getOrg() {
        return org;
    }

    /**
    * @return the lastCheckIn
    */
    public Date getLastCheckIn() {
        return lastCheckIn;
    }

}

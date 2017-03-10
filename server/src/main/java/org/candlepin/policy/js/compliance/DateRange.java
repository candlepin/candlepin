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
package org.candlepin.policy.js.compliance;

import java.util.Date;



/**
 * Represents a range in time -- Start date to End Date inclusive.
 */
public class DateRange {
    private static final String DATE_FORMAT = "%1$tF %1$tT.%1$tL%1$tz";

    private Date startDate;
    private Date endDate;

    public DateRange() {
        // Intentionally left empty
    }

    public DateRange(Date start, Date end) {
        this.startDate = start;
        this.endDate = end;
    }

    public void setStartDate(Date start) {
        this.startDate = start;
    }

    public void setEndDate(Date end) {
        this.endDate = end;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    /**
     * Determines if the specified date is within the range (inclusive of start and end dates).
     *
     * @param date to check
     * @return true if the range contains date, false otherwise.
     */
    public boolean contains(Date date) {
        return (this.startDate != null && this.startDate.compareTo(date) <= 0) &&
            (this.endDate != null && this.endDate.compareTo(date) >= 0);
    }

    @Override
    public String toString() {
        return String.format("DateRange [%s - %s]",
            (this.startDate != null ? String.format(DATE_FORMAT, this.startDate) : null),
            (this.endDate != null ? String.format(DATE_FORMAT, this.endDate) : null));
    }

}

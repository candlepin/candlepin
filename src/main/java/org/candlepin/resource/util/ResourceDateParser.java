/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.resource.util;

import org.candlepin.exceptions.BadRequestException;

import java.util.Date;

import javax.xml.bind.DatatypeConverter;

/**
 * ResourceDate
 */
public class ResourceDateParser {

    private ResourceDateParser() {

    }

    public static Date getFromDate(String from, String to, String days) {
        if (days != null && !days.trim().equals("")) {
            if (to != null && !to.trim().equals("") ||
                from != null && !from.trim().equals("")) {
                throw new BadRequestException("You can use either the to/from " +
                                               "date parameters or the number of " +
                                               "days parameter, but not both");
            }
        }

        Date daysDate = null;
        if (days != null && !days.trim().equals("")) {
            long mills = 1000 * 60 * 60 * 24;
            int number = Integer.parseInt(days);
            daysDate = new Date(new Date().getTime() - (number * mills));
        }

        Date fromDate = null;
        if (daysDate != null) {
            fromDate = daysDate;
        }
        else {
            fromDate = parseDateString(from);
        }

        return fromDate;
    }

    public static Date parseDateString(String activeOn) {
        Date d;
        if (activeOn == null || activeOn.trim().equals("")) {
            return null;
        }
        try {
            d = DatatypeConverter.parseDateTime(activeOn).getTime();
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(
                "Invalid date, must use ISO 8601 format");
        }
        return d;
    }
}

/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.test;

import org.candlepin.util.DateSource;

import java.util.Calendar;
import java.util.Date;

public class DateSourceForTesting implements DateSource {
    private Date currentDate;

    public DateSourceForTesting() {
        currentDate = null;
    }

    public DateSourceForTesting(Date dateToReturn) {
        this.currentDate = dateToReturn;
    }

    public DateSourceForTesting(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
        currentDate = calendar.getTime();
    }

    public void currentDate(Date date) {
        this.currentDate = date;
    }

    public Date currentDate() {
        return currentDate;
    }
}

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
package org.candlepin.gutterball.bsoncallback;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * DateAndEscapeCallback to properly set date types for events
 */
public class DateAndEscapeCallback extends BsonEscapeCallback {

    // Throw anything in here if it should be turned into a date when the value is a long.
    // It will theoretically be a O(1) check since we're throwing them into a hash.
    private static final String[] DATEFIELDS_ARR = new String[]{"timestamp", "created",
        "updated", "modified", "deleted", "date", "datetime", "expiration", "lastCheckin",
        "startDate", "endDate"};

    /*
     * NOTE: we could use a case insensitive set here, but I'm not sure that gains us anything.
     * TreeMap is already written for us, but lookup time will probably be log(n) rather than 1.
     * Not that we've got enough elements for it to make any difference...
     */
    private static final Set<String> DATEFIELDS = new HashSet<String>(Arrays.asList(DATEFIELDS_ARR));

    public DateAndEscapeCallback() {
        this(true);
    }

    public DateAndEscapeCallback(boolean write) {
        super(write);
    }

    @Override
    public void gotLong(final String name, final long v) {
        // We only want to transform dates when we're preparing to write to the db
        // Once they're there, we can disregard the check
        if (write && DATEFIELDS.contains(name)) {
            this.gotDate(name, v);
        }
        else {
            super.gotLong(name, v);
        }
    }
}

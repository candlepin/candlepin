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

import org.candlepin.gutterball.model.Event;

import com.mongodb.util.JSON;

import org.apache.commons.lang.StringUtils;
import org.bson.BSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * EventCallback to create and properly escape the event from json
 */
public class EventCallback extends DateAndEscapeCallback {

    // Throw anything in here if it should be turned into a date when the value is a long.
    // It will theoretically be a O(1) check since we're throwing them into a hash.
    private static final String[] JSON_STRS_ARR = new String[]{"oldEntity", "newEntity"};

    /*
     * NOTE: we could use a case insensitive set here, but I'm not sure that gains us anything.
     * TreeMap is already written for us, but lookup time will probably be log(n) rather than 1.
     * Not that we've got enough elements for it to make any difference...
     */
    private static final Set<String> JSONSTRFIELDS = new HashSet<String>(Arrays.asList(JSON_STRS_ARR));

    private DateAndEscapeCallback dateEscapeCb;

    public EventCallback() {
        this(true);
    }

    public EventCallback(boolean write) {
        super(write);
    }

    @Override
    public BSONObject create() {
        return new Event();
    }

    @Override
    public void gotString(String name, String val) {
        if (!StringUtils.isBlank(val) && JSONSTRFIELDS.contains(name)) {
            _put(name, JSON.parse(val, getDateEscapeCallback()));
        }
        else {
            super.gotString(name, val);
        }
    }

    private DateAndEscapeCallback getDateEscapeCallback() {
        if (dateEscapeCb == null) {
            dateEscapeCb = new DateAndEscapeCallback(write);
        }
        return dateEscapeCb;
    }
}

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
package org.candlepin.gutterball.eventhandler;

import org.candlepin.gutterball.bsoncallback.DateAndEscapeCallback;
import org.candlepin.gutterball.curator.EntitlementCurator;
import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import org.apache.commons.lang.StringUtils;
import org.bson.BSONCallback;

/**
 * GuestIdHandler class to deal with guestId events
 *
 * TODO: This looks almost exactly like ConsumerHandler, unless
 * they diverge, we should use an abstract superclass.
 *
 * I am hesitant to pull code into a superclass until we know exactly
 * what this class is going to do, otherwise we might tie our hands
 * behind our backs.
 */
public class EntitlementHandler implements EventHandler {

    public static final String TARGET = "ENTITLEMENT";

    protected EntitlementCurator curator;
    protected BSONCallback callback;

    @Inject
    public EntitlementHandler(EntitlementCurator curator) {
        this.curator = curator;
        callback = new DateAndEscapeCallback();
    }

    public void handleEvent(Event event) {
        String newEntityJson = (String) event.get("newEntity");
        if (!StringUtils.isBlank(newEntityJson)) {
            DBObject newEntity = (DBObject) JSON.parse(newEntityJson, callback);
            // TODO: we should have a record that we maintain with
            // created/deleted/owner info to narrow our queries.
            // Each consumer record could also store that records id in some
            // sort of _master_id field.
            curator.insert(newEntity);
        }
    }

    public String getTarget() {
        return TARGET;
    }

    @Override
    public void handleCreated(Event event) {
        handleEvent(event);
    }

    @Override
    public void handleUpdated(Event event) {
        handleEvent(event);
    }

    @Override
    public void handleDeleted(Event event) {
        // Eventually we'll need to update some sort of consumer master record
    }
}

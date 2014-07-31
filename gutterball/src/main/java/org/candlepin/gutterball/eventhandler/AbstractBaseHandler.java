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
import org.candlepin.gutterball.curator.MongoDBCurator;
import org.candlepin.gutterball.model.AbstractEvent;
import org.candlepin.gutterball.model.Event;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import org.apache.commons.lang.StringUtils;
import org.bson.BSONCallback;

/**
 * Abstract base handler for now, since all of the handlers look identical.
 * We'll probably want to get rid of this at some point
 *
 * @param <M> Model that the curator is mapped to
 * @param <C> Main curator that this handler wraps
 */
public abstract class AbstractBaseHandler<M extends AbstractEvent<?, ?>,
        C extends MongoDBCurator<M>> implements EventHandler {

    protected C curator;
    protected BSONCallback callback;
    private static String[] stringToDBObjectKeys =
            new String[]{Event.NEW_ENTITY, Event.OLD_ENTITY, Event.PRINCIPAL_STORE};

    protected AbstractBaseHandler(C curator) {
        this.curator = curator;
        callback = new DateAndEscapeCallback();
    }

    public void handleEvent(Event event) {
        for (String attribute : stringToDBObjectKeys) {
            String json = event.getString(attribute);
            DBObject entity = null;
            if (!StringUtils.isBlank(json)) {
                entity = (DBObject) JSON.parse(json, callback);
            }
            // Remove the original string, we've already stored it
            event.remove(attribute);
            event.put(attribute, entity);
        }

        // Remove event Id to avoid conflicts, add it as the source event ID in case
        // we ever need to reference the message
        event.put(Event.SOURCE_EVENT, event.remove("_id"));
        curator.insert(event);
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
        handleEvent(event);
    }
}

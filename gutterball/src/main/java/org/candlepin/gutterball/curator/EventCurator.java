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

package org.candlepin.gutterball.curator;

import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.mongodb.MongoConnection;

import com.google.inject.Inject;

/**
 * A curator that manages DB operations on the 'events' collection.
 */
public class EventCurator extends MongoDBCurator<Event> {
    public static final String COLLECTION = "events";

    @Inject
    public EventCurator(MongoConnection mongo) {
        super(Event.class, mongo);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

}

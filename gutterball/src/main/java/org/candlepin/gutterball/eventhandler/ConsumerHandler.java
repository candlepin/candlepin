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

import org.apache.commons.lang.StringUtils;
import org.candlepin.gutterball.curator.ConsumerCurator;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.util.BsonEscapeCallback;

import com.google.inject.Inject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

/**
 * ConsumerHandler to properly update the database when
 * a consumer based event is received
 */
public class ConsumerHandler implements EventHandler {

    public static final String TARGET = "CONSUMER";

    protected ConsumerCurator consumerCurator;

    @Inject
    public ConsumerHandler(ConsumerCurator consumerCurator) {
        this.consumerCurator = consumerCurator;
    }

    public void handleEvent(Event event) {
        String newEntityJson = (String) event.get("newEntity");
        if (!StringUtils.isBlank(newEntityJson)) {
            DBObject newEntity = (DBObject) JSON.parse(newEntityJson, new BsonEscapeCallback());
            // TODO: we should have a consumer record that we maintain with
            // created/deleted/owner info to narrow our queries.
            // Each consumer record could also store that records id in some
            // sort of _master_id field.
            consumerCurator.insert(newEntity);
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

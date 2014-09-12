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

import org.candlepin.gutterball.curator.ConsumerCurator;
import org.candlepin.gutterball.curator.jpa.ConsumerStateCurator;
import org.candlepin.gutterball.model.Consumer;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.jpa.ConsumerState;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * ConsumerHandler to properly update the database when
 * a consumer based event is received
 */
@HandlerTarget("CONSUMER")
public class ConsumerHandler implements EventHandler {

    protected ConsumerCurator consumerCurator;
    protected ConsumerStateCurator consumerStateCurator;

    @Inject
    public ConsumerHandler(ConsumerCurator consumerCurator, ConsumerStateCurator stateCurator) {
        this.consumerCurator = consumerCurator;
        this.consumerStateCurator = stateCurator;
    }

    @Override
    public void handleCreated(Event event) {
        BasicDBObject newConsumer = (BasicDBObject) event.getNewEntity();
        BasicDBObject owner = (BasicDBObject) newConsumer.get("owner");

        // JPA Insertion
        ConsumerState consumerState = new ConsumerState(newConsumer.getString("uuid"),
                owner.getString("key"), newConsumer.getDate("created"));
        consumerStateCurator.create(consumerState);

        // Mongo Insertion
        Consumer toAdd = new Consumer(newConsumer.getString("uuid"),
                newConsumer.getDate("created"),
                (DBObject) newConsumer.get("owner"));
        consumerCurator.insert(toAdd);
    }

    @Override
    public void handleUpdated(Event event) {
        // NO-OP
    }

    @Override
    public void handleDeleted(Event event) {
        BasicDBObject targetConsumer = (BasicDBObject) event.getOldEntity();
        consumerCurator.setConsumerDeleted(targetConsumer.getString("uuid"), event.getTimestamp());
        consumerStateCurator.setConsumerDeleted(targetConsumer.getString("uuid"), event.getTimestamp());
    }
}

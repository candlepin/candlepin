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

import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import java.io.IOException;

/**
 * ConsumerHandler to properly update the database when
 * a consumer based event is received
 */
@HandlerTarget("CONSUMER")
public class ConsumerHandler implements EventHandler {

    protected ConsumerStateCurator consumerStateCurator;
    private ObjectMapper mapper;

    @Inject
    public ConsumerHandler(ObjectMapper mapper, ConsumerStateCurator stateCurator) {
        this.consumerStateCurator = stateCurator;
        this.mapper = mapper;
    }

    @Override
    public void handleCreated(Event event) {
        String newConsumerJson = event.getNewEntity();

        // JPA Insertion
        try {
            ConsumerState consumerState = mapper.readValue(newConsumerJson, ConsumerState.class);
            consumerStateCurator.create(consumerState);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to deserialize Consumer Created Event.", e);
        }
    }

    @Override
    public void handleUpdated(Event event) {
        // NO-OP
    }

    @Override
    public void handleDeleted(Event event) {
        try {
            ConsumerState consumerState = mapper.readValue(event.getOldEntity(), ConsumerState.class);
            // consumerState is considered a new record here as it is parsed from CP json.
            // We just want to extract the UUID from the event.
            consumerStateCurator.setConsumerDeleted(consumerState.getUuid(), event.getTimestamp());
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to deserialize Consumer Deleted Event.", e);
        }
    }
}

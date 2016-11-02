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
package org.candlepin.audit;

import org.candlepin.util.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An EventListener that publishes events to an AMQP bus (qpid).
 */
public class AMQPBusPublisher implements EventListener {
    private static Logger log = LoggerFactory.getLogger(AMQPBusPublisher.class);
    private QpidConnection sender;
    private ObjectMapper mapper;

    @Inject
    public AMQPBusPublisher(ObjectMapper omapper, QpidConnection sender) {
        this.sender = sender;
        this.mapper = omapper;
    }

    @Override
    public void onEvent(Event e) {
        try {
            sender.sendTextMessage(e.getTarget(), e.getType(), this.apply(e));
        }
        catch (Exception ex) {
            throw new RuntimeException("Error sending event to message bus", ex);
        }
    }

    public void close() {
        Util.closeSafely(sender, "QpidConnection");
    }

    public String apply(Event event) throws JsonProcessingException {
        return mapper.writeValueAsString(event);
    }
}

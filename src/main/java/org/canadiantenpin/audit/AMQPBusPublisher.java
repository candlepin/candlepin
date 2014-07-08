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
package org.canadianTenPin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.canadianTenPin.audit.Event.Target;
import org.canadianTenPin.audit.Event.Type;
import org.canadianTenPin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;

/**
 * An EventListener that publishes events to an AMQP bus (qpid).
 */
public class AMQPBusPublisher implements EventListener {
    private static Logger log = LoggerFactory.getLogger(AMQPBusPublisher.class);
    private TopicSession session;
    private Map<Target, Map<Type, TopicPublisher>> producerMap;
    private ObjectMapper mapper;

    @Inject
    public AMQPBusPublisher(TopicSession session,
            Map<Target, Map<Type, TopicPublisher>> producerMap, ObjectMapper omapper) {
        this.session = session;
        this.producerMap = producerMap;
        this.mapper = omapper;
    }

    @Override
    public void onEvent(Event e) {
        try {
            Map<Type, TopicPublisher> m = this.producerMap.get(e.getTarget());
            if (m != null) {
                TopicPublisher tp = m.get(e.getType());
                if (tp != null) {
                    log.debug("Sending event to tp");
                    tp.send(session.createTextMessage(this.apply(e)));
                }
                else {
                    log.warn("TopicPublisher is NULL!");
                }
            }
        }
        catch (JMSException ex) {
            log.warn("Unable to send event :" + e + " via AMQPBus", ex);
        }
        catch (JsonProcessingException jpe) {
            log.warn("Unable to send event :" + e + " via AMQPBus", jpe);
        }
    }

    public void close() {
        // Why this big loop? To log in case, we failed to close any publishers.
        for (Entry<Target, Map<Type, TopicPublisher>> entry : this.producerMap
            .entrySet()) {
            for (Entry<Type, TopicPublisher> tpMap : entry.getValue()
                .entrySet()) {
                Util.closeSafely(tpMap.getValue(), String.format(
                    "TopicPublisherOf[%s, %s]", entry.getKey(), tpMap.getKey()));
            }
        }
    }

    public String apply(Event event) throws JsonProcessingException {
        return mapper.writeValueAsString(event);
    }
}

/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.audit;

import java.util.Map;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;

import org.fedoraproject.candlepin.audit.Event.Target;
import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 *
 * @author ajay
 */
public class AMQPBusPublisher implements EventListener{
    private static Logger log = LoggerFactory.getLogger(AMQPBusPublisher.class);
    private Function<Event, String> adapter;
    private TopicSession session;
    private Map<Target, Map<Type, TopicPublisher>> producerMap;

    @Inject
    public AMQPBusPublisher(TopicSession session, 
            @Named("eventToQpidAdapter")Function<Event, String> amqpbea,
            Map<Target, Map<Type, TopicPublisher>> producerMap) {
        this.session = session;
        this.producerMap = producerMap;
        this.adapter = amqpbea;
    }


    @Override
    public void onEvent(Event e) {
        try {
            Map<Type, TopicPublisher> m = this.producerMap.get(e.getTarget());
            if (m != null) {
                TopicPublisher tp = m.get(e.getType());
                if (tp != null) {
                    log.debug("Sending event to tp");
                    tp.send(session.createTextMessage(adapter.apply(e)));
                }
                else {
                    log.warn("TopicPublisher is NULL!");
                }
            }
        }
        catch (JMSException ex) {
            log.warn("Unable to send event :" + e + " via AMQPBus", ex);
            // TODO Try recovering session?
        }
    }

    public void close() {
        /*Why this big loop? To log in case, we failed to close any publishers.*/
        for (Entry<Target, Map<Type, TopicPublisher>> entry : this.producerMap
            .entrySet()) {
            for (Entry<Type, TopicPublisher> tpMap : entry.getValue()
                .entrySet()) {
                Util.closeSafely(tpMap.getValue(), String.format(
                    "TopicPublisherOf[%s, %s]", entry.getKey(), tpMap.getKey()));
            }
        }
    }
}

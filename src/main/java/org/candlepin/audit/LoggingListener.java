/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import ch.qos.logback.classic.Logger;

import org.candlepin.messaging.CPMConsumer;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSession;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

/**
 * LoggingListener
 *
 * Since we are actually adjusting logging configuration, we have to access the
 * underlying logger implementation instead of going through slf4j.
 *
 * See http://slf4j.org/faq.html#when
 */
public class LoggingListener implements CPMMessageListener {
    private static org.slf4j.Logger LOG = LoggerFactory.getLogger(LoggingListener.class);

    private static Logger auditLog;

    @Inject
    public LoggingListener() {
        auditLog = (Logger) LoggerFactory
            .getLogger(LoggingListener.class.getCanonicalName() + ".AuditLog");
    }

    @Override
    public void handleMessage(CPMSession session, CPMConsumer consumer, CPMMessage message) {
        // We shouldn't do this in practice, just testing here
        ObjectMapper mapper = new ObjectMapper();
        Event event = null;
        try {
            event = mapper.readValue(message.getBody(), Event.class);
        }
        catch (JsonProcessingException  e) {
            LOG.error("Unable to deserialize", e);
        }

        String logMessage = "principalType={} principal={} target={} entityId={} type={} owner={} " +
            "anonymousOwner={} eventData={}\n";
        auditLog.info(logMessage,
            event.getPrincipalData().getType(),
            event.getPrincipalData().getName(),
            event.getTarget(),
            event.getEntityId(),
            event.getType(),
            event.getOwnerKey(),
            event.isOwnerAnonymous(),
            event.getEventData());
    }
}

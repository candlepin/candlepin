/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.messaging.CPMConsumer;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSession;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

/**
 * ActivationListener
 */
public class ActivationListener implements CPMMessageListener {
    private static Logger log = LoggerFactory.getLogger(ActivationListener.class);
    private SubscriptionServiceAdapter subscriptionService;

    @Inject
    public ActivationListener(SubscriptionServiceAdapter subService) {
        this.subscriptionService = subService;
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
            log.error("Unable to deserialize", e);
        }

        if (event.getType().equals(Event.Type.CREATED) &&
            event.getTarget().equals(Event.Target.POOL)) {
            Object subscriptionId = null;
            if (event.getEventData() != null) {
                subscriptionId = event.getEventData().get("subscriptionId");
            }

            if (subscriptionId != null) {
                subscriptionService.sendActivationEmail(String.valueOf(subscriptionId));
            }
        }
    }

}

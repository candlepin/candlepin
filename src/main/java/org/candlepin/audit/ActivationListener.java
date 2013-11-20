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

import org.candlepin.model.Pool;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * ActivationListener
 */
public class ActivationListener implements EventListener {
    private static Logger log = LoggerFactory.getLogger(ActivationListener.class);
    private SubscriptionServiceAdapter subscriptionService;
    private ObjectMapper mapper;

    @Inject
    public ActivationListener(SubscriptionServiceAdapter subService) {
        this.subscriptionService = subService;
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(pair);
    }

    @Override
    public void onEvent(Event e) {
        if (e.getType().equals(Event.Type.CREATED) &&
                e.getTarget().equals(Event.Target.POOL)) {
            String poolJson = e.getNewEntity();
            Reader reader = new StringReader(poolJson);
            try {
                Pool pool = mapper.readValue(reader, Pool.class);
                subscriptionService.sendActivationEmail(pool.getSubscriptionId());
            }
            catch (JsonMappingException ex) {
                logError(e);
            }
            catch (JsonParseException ex) {
                logError(e);
            }
            catch (IOException ex) {
                logError(e);
            }
        }
    }

    private void logError(Event e) {
        log.debug("Invalid JSON for pool : " + e.getEntityId());
    }
}

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

import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.model.EventCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseListener
 */
public class DatabaseListener implements EventListener {

    private EventCurator eventCurator;
    private static Logger log = LoggerFactory.getLogger(DatabaseListener.class);

    @Inject
    public DatabaseListener(EventCurator eventCurator) {
        this.eventCurator = eventCurator;
    }

    @Override
    public void onEvent(Event event) {
        // We're outside of a web request here, need to create this event and satisfy the
        // access control interceptor.
        Principal systemPrincipal = new SystemPrincipal();
        ResteasyProviderFactory.pushContext(Principal.class, systemPrincipal);
        if (log.isDebugEnabled()) {
            log.debug("Received event: " + event);
        }

        if (event != null) {
            eventCurator.create(event);
        }
    }
}

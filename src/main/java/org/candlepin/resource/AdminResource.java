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
package org.candlepin.resource;

import org.candlepin.audit.EventSink;
import org.candlepin.auth.SecurityHole;
import org.candlepin.dto.api.server.v1.QueueStatus;
import org.candlepin.resource.server.v1.AdminApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Candlepin server administration REST calls.
 */
public class AdminResource implements AdminApi {
    private static final Logger log = LoggerFactory.getLogger(AdminResource.class);

    private final EventSink sink;

    @Inject
    public AdminResource(EventSink dispatcher) {
        this.sink = Objects.requireNonNull(dispatcher);
    }

    /**
     * Endpoint originally intended to complete a Candlepin deployment by initializing any of its
     * services. As of 4.3.2, this endpoint should no longer be used as it no longer performs any
     * actions and will eventually be removed.
     *
     * @deprecated
     *  As of Candlepin 4.3.2, this endpoint is no longer used, and will be removed in a future
     *  release
     */
    @Override
    @SecurityHole(noAuth = true)
    @Deprecated
    public String initialize() {
        log.warn("The init endpoint has been deprecated and will be removed in a future Candlepin release");
        return "Already initialized.";
    }

    @Override
    public List<QueueStatus> getQueueStats() {
        return sink.getQueueInfo();
    }
}

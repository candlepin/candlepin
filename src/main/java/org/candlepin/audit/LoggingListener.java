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

import ch.qos.logback.classic.Logger;

import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * LoggingListener
 *
 * Since we are actually adjusting logging configuration, we have to access the
 * underlying logger implementation instead of going through slf4j.
 *
 * See http://slf4j.org/faq.html#when
 */
public class LoggingListener implements EventListener {

    private static Logger auditLog;

    @Inject
    public LoggingListener() {
        auditLog = (Logger) LoggerFactory
            .getLogger(LoggingListener.class.getCanonicalName() + ".AuditLog");
    }

    @Override
    public void onEvent(Event e) {
        auditLog.info(
            "principalType={} principal={} target={} entityId={} type={} owner={} eventData={}\n",
            e.getPrincipalData().getType(),
            e.getPrincipalData().getName(),
            e.getTarget(),
            e.getEntityId(),
            e.getType(),
            e.getOwnerKey(),
            e.getEventData());
    }
}

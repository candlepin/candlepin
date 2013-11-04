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

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * LoggingListener
 *
 * Since we are actually adjusting logging configuration, we have to access the
 * underlying logger implementation instead of going through slf4j.
 *
 * See http://slf4j.org/faq.html#when
 */
public class LoggingListener implements EventListener {
    private static Logger auditLog =
        Logger.getLogger(LoggingListener.class.getCanonicalName() + ".AuditLog");

    private boolean verbose;

    private final DateFormat df;

    public LoggingListener() throws IOException {
        Config config = new Config();

        auditLog.addAppender(new FileAppender(new PatternLayout("%m"),
            config.getString(ConfigProperties.AUDIT_LOG_FILE)));
        // Keep these messages in audit.log only
        auditLog.setAdditivity(false);

        verbose = config.getBoolean(ConfigProperties.AUDIT_LOG_VERBOSE);
        TimeZone tz = TimeZone.getDefault();
        // Format for ISO8601 to match our other logging:
        df = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ssZ");
        df.setTimeZone(tz);
    }

    @Override
    public void onEvent(Event e) {
        auditLog.info(String.format(
            "%s principalType=%s principal=%s target=%s entityId=%s type=%s owner=%s\n",
            df.format(e.getTimestamp()),
            e.getPrincipal().getType(),
            e.getPrincipal().getName(),
            e.getTarget(),
            e.getEntityId(),
            e.getType(),
            e.getOwnerId()));
        if (verbose) {
            auditLog.info(String.format("==OLD==\n%s\n==NEW==\n%s\n\n", e.getOldEntity(),
                e.getNewEntity()));
        }
    }

}

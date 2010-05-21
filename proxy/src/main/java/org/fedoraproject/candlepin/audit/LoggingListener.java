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

import java.io.IOException;

import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;

/**
 * LoggingListener
 */
public class LoggingListener implements EventListener {
    private static Logger auditLog =
        Logger.getLogger(LoggingListener.class.getCanonicalName() + ".AuditLog");
    
    public LoggingListener() throws IOException {
        auditLog.addAppender(new FileAppender(new PatternLayout("%m"),
            new Config().getString(ConfigProperties.AUDIT_LOG_FILE)));
        // Keep these messages in audit.log only
        auditLog.setAdditivity(false);
    }
    
    @Override
    public void onEvent(Event e) {
        auditLog.info(String.format(
            "%s - %d - %s on %d\nperformed by %s\n==OLD==\n%s\n==NEW==\n%s\n",
            e.getTimestamp(), e.getId(), e.getType(), e.getEntityId(), e.getPrincipal(),
            e.getOldEntity(), e.getNewEntity()));
    }

}

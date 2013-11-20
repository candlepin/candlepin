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
package org.candlepin.logging;

import ch.qos.logback.classic.LoggerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In order to release the resources used by logback-classic, it is always a
 * good idea to stop the logback context. Stopping the context will close all
 * appenders attached to loggers defined by the context and stop any active threads.
 *
 * See
 * <a href="http://logback.qos.ch/manual/jmxConfig.html#leak">
 * http://logback.qos.ch/manual/jmxConfig.html#leak
 * </a>
 */
public class LoggerContextListener {
    public void contextDestroyed() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.info("Stopping logger context");
        context.stop();
    }
}

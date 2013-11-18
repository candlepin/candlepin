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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.MatchingFilter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.OptionHelper;

import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * This filter accepts events based on whether the log event is greater than
 * or equal to the level of the key parameter in the MDC and on whether the
 * originating logger is a child of the topLogger parameter.
 */
public class LoggerAndMDCFilter extends MatchingFilter {
    private String key;
    private String topLogger;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTopLogger() {
        return topLogger;
    }

    public void setTopLogger(String topLogger) {
        this.topLogger = topLogger;
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
        String s, Object[] objects, Throwable throwable) {

        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        String mdcLevel = MDC.get(key);

        if (mdcLevel == null) {
            return FilterReply.NEUTRAL;
        }

        /* Logger children do not actually have to start with the prefix of their
         * parent, but it's a de facto convention and logback doesn't surface any
         * other way to access child loggers.
         */
        boolean isChild = (Logger.ROOT_LOGGER_NAME.equals(topLogger)) ? true :
            logger.getName().startsWith(topLogger);

        // If for some reason someone puts in a non-parseable value for the level in
        // the MDC, then we will default to INFO.
        if (isChild && level.isGreaterOrEqual(Level.toLevel(mdcLevel, Level.INFO))) {
            return onMatch;
        }
        return onMismatch;
    }

    @Override
    public void start() {
        int errors = 0;

        if (OptionHelper.isEmpty(topLogger)) {
            addWarn("The \"topLogger\" property is not set. Default to ROOT logger.");
            topLogger = Logger.ROOT_LOGGER_NAME;
        }

        if (OptionHelper.isEmpty(key)) {
            addError("The \"key\" property must be set");
            errors++;
        }

        if (errors == 0) {
            super.start();
        }
    }
}

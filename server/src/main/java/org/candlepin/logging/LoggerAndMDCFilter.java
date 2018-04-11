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

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.classic.turbo.MatchingFilter;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.OptionHelper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This filter accepts events based on whether the log event is greater than
 * or equal to the level of the key parameter in the MDC and on whether the
 * originating logger is a child of the topLogger parameter.
 */
public class LoggerAndMDCFilter extends MatchingFilter {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(LoggerAndMDCFilter.class);

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
        boolean isChild =
            Logger.ROOT_LOGGER_NAME.equals(topLogger) || logger.getName().startsWith(topLogger);

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

    /**
     * Inserts an instance of this TurboFilter into the logging context if it is not already present.  While
     * they are fast (< 0.1ms), TurboFilters still result in some overhead.  Previously we enabled the
     * LoggerAndMDCFilter in the logback configuration file, but in production instances, the filter's decide
     * method will be called millions of times just to support a feature that is used very rarely.  This
     * method and it's counterpart, removeFilter, can be used to only insert the filter when it is needed.
     */
    public static void insertFilter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilterList filterList = context.getTurboFilterList();

        // Add the LoggerAndMDCFilter if it isn't already enabled
        if (filterList.stream().noneMatch(f -> f instanceof LoggerAndMDCFilter)) {
            log.info("Enabling LoggerAndMDCFilter TurboFilter");
            LoggerAndMDCFilter filter = new LoggerAndMDCFilter();
            filter.setKey("orgLogLevel");
            filter.setTopLogger("org.candlepin");
            filter.setOnMatch("ACCEPT");
            filter.setName("LoggerAndMDCFilter");
            context.addTurboFilter(filter);
        }
    }

    /**
     * Remove any instances of this TurboFilter from the logging context.
     */
    public static void removeFilter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilterList filterList = context.getTurboFilterList();
        List<TurboFilter> deadFilters = filterList.stream()
            .filter(LoggerAndMDCFilter.class::isInstance)
            .collect(Collectors.toList());
        filterList.removeAll(deadFilters);
        if (!deadFilters.isEmpty()) {
            log.info("No one else using org-level logging. Removed TurboFilters: {}",
                deadFilters.stream().map(TurboFilter::getName).collect(Collectors.toList()));
        }
    }
}

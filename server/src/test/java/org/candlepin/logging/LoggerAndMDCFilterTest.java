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

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.core.spi.FilterReply;

/**
 * LoggerAndMDCFilterTest
 */
public class LoggerAndMDCFilterTest {

    private LoggerAndMDCFilter filter;
    private LoggerContext context;

    @Before
    public void setUp() throws Exception {
        context = new LoggerContext();
        context.start();
        filter = new LoggerAndMDCFilter();
        filter.setContext(context);

        filter.setKey("foo");
        filter.setTopLogger("org.candlepin");

        filter.setOnMatch(FilterReply.ACCEPT.toString());
        filter.setOnMismatch(FilterReply.DENY.toString());

        filter.start();
    }

    @After
    public void tearDown() throws Exception {
        MDC.clear();
    }

    @Test
    public void testDecideWithUnStartedFilter() {
        filter = new LoggerAndMDCFilter();
        filter.setContext(context);
        Logger logger = context.getLogger("org.candlepin.foo");
        assertEquals(FilterReply.NEUTRAL,
            filter.decide(null, logger, Level.DEBUG, null, null, null));
    }

    @Test
    public void testDecideWithNoKey() {
        Logger logger = context.getLogger("org.candlepin.foo");

        // The MDC doesn't have the key, so we'll return neutral
        assertEquals(FilterReply.NEUTRAL,
            filter.decide(null, logger, Level.DEBUG, null, null, null));
    }

    @Test
    public void testDecideWithKey() {
        Logger logger = context.getLogger("org.candlepin.foo");
        MDC.put("foo", "ALL");

        assertEquals(FilterReply.ACCEPT,
            filter.decide(null, logger, Level.DEBUG, null, null, null));
    }

    @Test
    public void testDecideWithLevelTooHigh() {
        Logger logger = context.getLogger("org.candlepin.foo");
        MDC.put("foo", "NONE");

        assertEquals(FilterReply.DENY,
            filter.decide(null, logger, Level.DEBUG, null, null, null));
    }

    @Test
    public void testDecideWithWrongTopLogger() {
        Logger logger = context.getLogger("org.someoneelse.foo");
        MDC.put("foo", "ALL");

        assertEquals(FilterReply.DENY,
            filter.decide(null, logger, Level.DEBUG, null, null, null));
    }

    @Test
    public void testInsertFilter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilterList filterList = context.getTurboFilterList();

        assertTrue(filterList.stream().noneMatch(f -> f instanceof LoggerAndMDCFilter));

        LoggerAndMDCFilter.insertFilter();
        assertTrue(filterList.stream().anyMatch(f -> f instanceof LoggerAndMDCFilter));

        int length = filterList.size();

        LoggerAndMDCFilter.insertFilter();
        assertEquals(length, filterList.size());
    }

    @Test
    public void testRemoveFilter() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilterList filterList = context.getTurboFilterList();

        LoggerAndMDCFilter instance1 = new LoggerAndMDCFilter();
        instance1.setName("instance1");

        LoggerAndMDCFilter instance2 = new LoggerAndMDCFilter();
        instance2.setName("instance2");

        filterList.add(instance1);
        filterList.add(instance2);

        int originalLength = filterList.size();
        LoggerAndMDCFilter.removeFilter();
        assertEquals(originalLength - 2, filterList.size());
        assertTrue(filterList.stream().noneMatch(f -> f instanceof LoggerAndMDCFilter));
    }

    @Test
    public void testStart() {
        filter = new LoggerAndMDCFilter();
        filter.setContext(context);
        filter.start();
        assertFalse(filter.isStarted());

        filter.setTopLogger("org.candlepin");
        filter.start();
        assertFalse(filter.isStarted());

        filter.setKey("foo");
        filter.start();
        assertTrue(filter.isStarted());
    }
}

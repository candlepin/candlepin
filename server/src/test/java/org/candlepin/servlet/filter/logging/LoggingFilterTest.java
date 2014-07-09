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
package org.candlepin.servlet.filter.logging;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.FilterChain;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * LoggingFilterTest
 */
public class LoggingFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private LoggingFilter filter;
    private Logger filterlogger;
    private Enumeration headernames;
    private Appender mockapp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        filter = new LoggingFilter();

        // prepare logger
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        filterlogger = lc.getLogger(LoggingFilter.class);
        mockapp = mock(Appender.class);
        filterlogger.addAppender(mockapp);
        filterlogger.setLevel(Level.DEBUG);

        headernames = mock(Enumeration.class);
        when(headernames.hasMoreElements()).thenReturn(Boolean.FALSE);
        when(request.getHeaderNames()).thenReturn(headernames);
    }

    @Test
    public void testDoFilterDebugOff() throws Exception {
        filterlogger.setLevel(Level.WARN);

        filter.doFilter(request, response, chain);

        verify(mockapp, never()).doAppend(null);
    }

    @Test
    public void testWithHeaders() throws Exception {
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);
        when(headernames.hasMoreElements()).thenReturn(Boolean.TRUE)
                                           .thenReturn(Boolean.FALSE);
        when(headernames.nextElement()).thenReturn("Accept");
        when(request.getHeaderNames()).thenReturn(headernames);
        when(request.getHeader("Accept")).thenReturn("NoSubstitutes");

        filter.doFilter(request, response, chain);

        verify(mockapp, atLeastOnce()).doAppend(message.capture());
    }

    @Test
    public void testWithQueryString() throws Exception {
        when(request.getQueryString()).thenReturn("foo=bar");
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        filter.doFilter(request, response, chain);

        verify(mockapp, atLeastOnce()).doAppend(message.capture());
    }

    @Test
    public void testDoFilter() throws Exception {
        filterlogger.setLevel(Level.DEBUG);

        final ByteArrayInputStream bais =
            new ByteArrayInputStream("this is my body".getBytes());
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            public int read() throws IOException {
                return bais.read();
            }
        });

        when(request.getRequestURL()).thenReturn(new StringBuffer("/some/url"));
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        // DO FILTER!
        filter.doFilter(request, response, chain);

        // VERIFY
        verify(mockapp, atLeastOnce()).doAppend(message.capture());

    }

}

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
package org.candlepin.common.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * LoggingFilterTest
 */
@ExtendWith(MockitoExtension.class)
public class LoggingFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private LoggingFilter filter;
    private Logger filterlogger;

    @Mock
    private Enumeration<String> headernames;

    @Mock
    private Appender<ILoggingEvent> mockapp;

    @BeforeEach
    public void setUp() {
        filter = new LoggingFilter();

        // prepare logger
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        filterlogger = lc.getLogger(LoggingFilter.class);
        filterlogger.addAppender(mockapp);
        filterlogger.setLevel(Level.DEBUG);

        when(headernames.hasMoreElements()).thenReturn(Boolean.FALSE);
        when(request.getHeaderNames()).thenReturn(headernames);
    }

    @Test
    public void testSetHeader() throws Exception {
        FilterConfig config = mock(FilterConfig.class);
        String header = "x-blorp";
        when(config.getInitParameter("header.name")).thenReturn(header);

        filter.init(config);
        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(headerName.capture(), anyString());
        assertEquals(header, headerName.getValue());
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
            @Override
            public int read() throws IOException {
                return bais.read();
            }
        });

        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        // DO FILTER!
        filter.doFilter(request, response, chain);

        // VERIFY
        verify(mockapp, atLeastOnce()).doAppend(message.capture());
    }
}

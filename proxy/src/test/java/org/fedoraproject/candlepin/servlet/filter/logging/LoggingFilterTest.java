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
package org.fedoraproject.candlepin.servlet.filter.logging;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

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
        filterlogger = Logger.getLogger(LoggingFilter.class);
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

        assertList(message.getAllValues(), new LinkedList<String>() {
            {
                add("Request: 'null null'");
                add("====RequestBody====");
                add("");
                add("====Headers====");
                add("Accept:  NoSubstitutes");
                add("====Response====");
                add("");
            }
        });

    }

    @Test
    public void testWithQueryString() throws Exception {
        when(request.getQueryString()).thenReturn("foo=bar");
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        filter.doFilter(request, response, chain);

        verify(mockapp, atLeastOnce()).doAppend(message.capture());

        assertList(message.getAllValues(), new LinkedList<String>() {
            {
                add("Request: 'null null?foo=bar'");
                add("====RequestBody====");
                add("");
                add("====Headers====");
                add("====Response====");
                add("");
            }
        });
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

        assertList(message.getAllValues(), new LinkedList<String>() {
            {
                add("Request: 'null /some/url'");
                add("====RequestBody====");
                add("this is my body");
                add("====Headers====");
                add("====Response====");
                add("");
            }
        });
    }

    private void assertList(List<LoggingEvent> evts, List<String> expmessages) {
        assertEquals("lists do not match", evts.size(), expmessages.size());
        for (int i = 0; i < evts.size(); i++) {
            assertEquals(expmessages.get(i), evts.get(i).getMessage());
        }
    }

}

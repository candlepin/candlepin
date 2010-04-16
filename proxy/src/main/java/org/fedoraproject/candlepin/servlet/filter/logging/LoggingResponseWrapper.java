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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * LoggingResponseWrapper
 */
public class LoggingResponseWrapper extends HttpServletResponseWrapper {

    protected StringBuffer buffer = new StringBuffer();
    protected HttpServletResponse realResponse;

    public LoggingResponseWrapper(HttpServletResponse resp) throws IOException {
        super(resp);
        realResponse = resp;
    }

    public ServletOutputStream getOutputStream() throws java.io.IOException {
        ServletOutputStream stream = new ServletOutputStream() {

            @Override
            public void write(int b) throws IOException {
                buffer.append((char) b);
                realResponse.getOutputStream().write(b);
            }
        };

        return stream;
    }

    public PrintWriter getWriter() throws java.io.IOException {
        return new PrintWriter(getOutputStream());
    }

    public String getResponseBody() {
        return buffer.toString();
    }

}

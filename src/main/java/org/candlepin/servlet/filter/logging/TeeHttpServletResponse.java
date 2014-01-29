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

import org.candlepin.util.Util;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Heavily borrowed from the logback-access package.
 */
public class TeeHttpServletResponse extends HttpServletResponseWrapper
    implements BodyLogger {

    protected TeeServletOutputStream teeServletOutputStream;
    protected PrintWriter teeWriter;
    protected Map<String, List<String>> headers = new HashMap<String, List<String>>();
    protected int status;

    public TeeHttpServletResponse(HttpServletResponse httpServletResponse) {
        super(httpServletResponse);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (teeServletOutputStream == null) {
            teeServletOutputStream = new TeeServletOutputStream(
                this.getResponse());
        }
        return teeServletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (this.teeWriter == null) {
            this.teeWriter = new PrintWriter(new OutputStreamWriter(
                getOutputStream()), true);
        }
        return this.teeWriter;
    }

    @Override
    public void flushBuffer() {
        if (this.teeWriter != null) {
            this.teeWriter.flush();
        }
    }

    public byte[] getOutputBuffer() {
        // teeServletOutputStream can be null if the getOutputStream method is
        // never called.
        if (teeServletOutputStream != null) {
            return teeServletOutputStream.getOutputStreamAsByteArray();
        }
        else {
            return null;
        }
    }

    public void finish() throws IOException {
        if (this.teeWriter != null) {
            this.teeWriter.close();
        }
        if (this.teeServletOutputStream != null) {
            this.teeServletOutputStream.close();
        }
    }

    @Override
    public String getBody() {
        byte[] buff = getOutputBuffer();

        if (buff != null) {
            if (ServletLogger.showAsText(getContentType())) {
                return new String(buff);
            }
            return StringUtils.abbreviate(Util.toBase64(buff), 100);
        }

        return "";
    }

    @Override
    public void setHeader(String name, String value) {
        List<String> values = new ArrayList<String>(1);
        values.add(value);
        headers.put(name, values);
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<String>();
            headers.put(name, values);
        }
        values.add(value);
        super.addHeader(name, value);
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setStatus(int status) {
        super.setStatus(status);
        this.status = status;
    }

    public void setStatus(int status, String sm) {
        super.setStatus(status, sm);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

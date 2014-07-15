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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;

/**
 * Heavily borrowed from the logback-access package.
 */
public class TeeServletOutputStream extends ServletOutputStream {

    protected final ServletOutputStream underlyingStream;
    protected final ByteArrayOutputStream baosCopy;

    TeeServletOutputStream(ServletResponse httpServletResponse) throws IOException {
        this.underlyingStream = httpServletResponse.getOutputStream();
        baosCopy = new ByteArrayOutputStream();
    }

    byte[] getOutputStreamAsByteArray() {
        return baosCopy.toByteArray();
    }

    @Override
    public void write(int val) throws IOException {
        if (underlyingStream != null) {
            underlyingStream.write(val);
            baosCopy.write(val);
        }
    }

    @Override
    public void write(byte[] byteArray) throws IOException {
        if (underlyingStream == null) {
            return;
        }
        write(byteArray, 0, byteArray.length);
    }

    @Override
    public void write(byte[] byteArray, int offset, int length) throws IOException {
        if (underlyingStream == null) {
            return;
        }

        underlyingStream.write(byteArray, offset, length);
        baosCopy.write(byteArray, offset, length);
    }

    @Override
    public void close() throws IOException {
        // If the servlet accessing the stream is using a writer instead of
        // an OutputStream, it will probably call os.close() before calling
        // writer.close. Thus, the underlying output stream will be called
        // before the data sent to the writer could be flushed.
    }

    @Override
    public void flush() throws IOException {
        if (underlyingStream == null) {
            return;
        }
        underlyingStream.flush();
        baosCopy.flush();
    }
}

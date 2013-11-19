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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;


/**
 * TeeHttpServletRequestTest
 */
public class TeeHttpServletRequestTest {
    @Mock private HttpServletRequest request;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCtor() throws IOException {
        final ByteArrayInputStream bais =
            new ByteArrayInputStream("this is my body".getBytes());
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            public int read() throws IOException {
                return bais.read();
            }
        });

        TeeHttpServletRequest tee = new TeeHttpServletRequest(request);
        assertNotNull(tee);
        assertEquals(Util.toBase64("this is my body".getBytes()), tee.getBody());
        assertNotNull(tee.getInputStream());
        assertEquals("this is my body", readData(tee.getInputStream()));
        assertEquals("this is my body", readData(tee.getReader()));

    }

    private String readData(InputStream is) throws IOException {
        return readData(new InputStreamReader(is));
    }

    private String readData(Reader rdr) throws IOException {
        StringBuffer buf = new StringBuffer();
        if (rdr != null) {
            BufferedReader br = new BufferedReader(rdr);
            buf.append(br.readLine());
        }
        return buf.toString();
    }
}

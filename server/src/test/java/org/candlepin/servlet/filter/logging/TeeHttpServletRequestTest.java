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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;


/**
 * TeeHttpServletRequestTest
 */
public class TeeHttpServletRequestTest {
    @Mock private HttpServletRequest request;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        final ByteArrayInputStream bais =
            new ByteArrayInputStream("this is my body".getBytes());
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            public int read() throws IOException {
                return bais.read();
            }
        });
    }

    @Test
    public void testCtor() throws IOException {
        TeeHttpServletRequest tee = new TeeHttpServletRequest(request);
        assertNotNull(tee);
        assertNotNull(tee.getInputStream());
        assertEquals("this is my body", readData(tee.getInputStream()));
        assertEquals("this is my body", readData(tee.getReader()));
    }

    @Test
    public void getBodyTest() throws IOException {
        TeeHttpServletRequest tee = new TeeHttpServletRequest(request);

        // Map content types to whether they should be logged as text or base64 encoded
        Map<String, Boolean> types = new HashMap<String, Boolean>();
        types.put(MediaType.APPLICATION_JSON, true);
        types.put(MediaType.APPLICATION_ATOM_XML, true);
        types.put(MediaType.TEXT_PLAIN, true);
        types.put(MediaType.TEXT_HTML, true);
        types.put(MediaType.TEXT_XML, true);
        types.put(MediaType.APPLICATION_FORM_URLENCODED, true);
        types.put(MediaType.APPLICATION_OCTET_STREAM, false);
        types.put("multipart/form-data", false);
        types.put("application/zip", false);

        for (String type : types.keySet()) {
            when(request.getContentType()).thenReturn(type);
            if (types.get(type)) {
                assertEquals(type + " failed!", "this is my body", tee.getBody());
            }
            else {
                assertEquals(type + " failed!", Util.toBase64("this is my body".getBytes()),
                    tee.getBody());
            }
        }
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

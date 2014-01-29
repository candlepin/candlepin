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
import static org.mockito.Mockito.when;

import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;


/**
 * TeeHttpServletResponseTest
 */
public class TeeHttpServletResponseTest {

    @Mock private HttpServletResponse resp;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        final StringWriter sw = new StringWriter();
        when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
            public void write(int b) throws IOException {
                sw.write(b);
            }
        });
    }

    @Test
    public void getBodyTest() throws IOException {
        TeeHttpServletResponse tee = new TeeHttpServletResponse(resp);
        tee.getOutputStream().write("this is my body".getBytes());

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
            when(resp.getContentType()).thenReturn(type);
            if (types.get(type)) {
                assertEquals(type + " failed!", "this is my body", tee.getBody());
            }
            else {
                assertEquals(type + " failed!", Util.toBase64("this is my body".getBytes()),
                    tee.getBody());
            }
        }
    }
}

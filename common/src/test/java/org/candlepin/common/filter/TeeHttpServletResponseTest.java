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

import org.candlepin.common.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@ExtendWith(MockitoExtension.class)
public class TeeHttpServletResponseTest {

    @Mock private HttpServletResponse resp;

    @BeforeEach
    public void setUp() throws IOException {
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
        Map<String, Boolean> types = new HashMap<>();
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
                assertEquals("this is my body", tee.getBody());
            }
            else {
                assertEquals(Util.toBase64("this is my body".getBytes()),
                    tee.getBody());
            }
        }
    }
}

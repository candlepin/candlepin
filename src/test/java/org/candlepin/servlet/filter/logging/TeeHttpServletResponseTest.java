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
import static org.mockito.Mockito.doReturn;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;


/**
 * TeeHttpServletResponseTest
 */
public class TeeHttpServletResponseTest {

    @Mock private HttpServletResponse resp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLoggin() throws IOException {

        final StringWriter sw = new StringWriter();

        doReturn(new ServletOutputStream() {
            public void write(int b) throws IOException {
                sw.write(b);
            }
        }).when(resp).getOutputStream();

        TeeHttpServletResponse tee = new TeeHttpServletResponse(resp);
        assertNotNull(tee);

        assertNotNull(tee.getOutputStream());
        tee.getOutputStream().write("this is my body".getBytes());

        assertEquals("this is my body", tee.getBody());
        assertEquals("this is my body", sw.getBuffer().toString());

        assertNotNull(tee.getWriter());
    }
}

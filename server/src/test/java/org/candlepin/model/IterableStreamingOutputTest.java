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
package org.candlepin.resteasy;

import static org.mockito.AdditionalAnswers.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.candlepin.model.Owner;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;



public class IterableStreamingOutputTest {

    protected JsonProvider mockJsonProvider;
    protected JsonFactory mockJsonFactory;
    protected JsonGenerator mockJsonGenerator;
    protected ObjectMapper mockObjectMapper;
    protected OutputStream mockOutputStream;
    protected IteratorTransformer mockTransformer;

    @Before
    public void setup() throws Exception {
        this.mockJsonProvider = mock(JsonProvider.class);
        this.mockJsonFactory = mock(JsonFactory.class);
        this.mockJsonGenerator = mock(JsonGenerator.class);
        this.mockObjectMapper = mock(ObjectMapper.class);
        this.mockOutputStream = mock(OutputStream.class);
        this.mockTransformer = mock(IteratorTransformer.class);

        when(this.mockJsonProvider.locateMapper(any(Class.class), any(MediaType.class)))
            .thenReturn(this.mockObjectMapper);

        when(this.mockObjectMapper.getJsonFactory()).thenReturn(this.mockJsonFactory);

        when(this.mockJsonFactory.createGenerator(eq(this.mockOutputStream)))
            .thenReturn(this.mockJsonGenerator);

        doAnswer(returnsFirstArg()).when(this.mockTransformer).transform(any(Object.class));
    }

    @Test
    public void testWriteWithoutTransform() throws IOException, WebApplicationException {
        List<Owner> owners = new LinkedList<Owner>(Arrays.asList(
            TestUtil.createOwner(),
            TestUtil.createOwner(),
            TestUtil.createOwner()
        ));

        IterableStreamingOutput<Owner> iso = new IterableStreamingOutput<Owner>(
            this.mockJsonProvider, owners
        );

        assertNull(iso.getTransformer());
        iso.write(this.mockOutputStream);

        verify(this.mockJsonGenerator, times(1)).writeStartArray();
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(0)));
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(1)));
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(2)));
        verify(this.mockJsonGenerator, times(1)).writeEndArray();
    }

    @Test
    public void testWriteWithTransform() throws IOException, WebApplicationException {
        List<Owner> owners = new LinkedList<Owner>(Arrays.asList(
            TestUtil.createOwner(),
            TestUtil.createOwner(),
            TestUtil.createOwner()
        ));

        IterableStreamingOutput<Owner> iso = new IterableStreamingOutput<Owner>(
            this.mockJsonProvider, owners
        );
        iso.setTransformer(this.mockTransformer);

        assertEquals(iso.getTransformer(), this.mockTransformer);
        iso.write(this.mockOutputStream);

        verify(this.mockJsonGenerator, times(1)).writeStartArray();
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(0)));
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(1)));
        verify(this.mockObjectMapper, times(1)).writeValue(eq(this.mockJsonGenerator), eq(owners.get(2)));
        verify(this.mockTransformer, times(1)).transform(eq(owners.get(0)));
        verify(this.mockTransformer, times(1)).transform(eq(owners.get(1)));
        verify(this.mockTransformer, times(1)).transform(eq(owners.get(2)));
        verify(this.mockJsonGenerator, times(1)).writeEndArray();
    }

}

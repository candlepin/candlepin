/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.jackson;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.ser.PropertyWriter;

import java.util.Arrays;


@ExtendWith(MockitoExtension.class)
public class DynamicPropertyFilterTest {

    @Mock
    private DynamicFilterData dynamicFilterData;

    @Mock
    private PropertyWriter writer;

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private TokenStreamContext context;

    @BeforeEach
    public void prepareMocks() {

    }

    @Test
    public void emptyFilterData() {
        ResteasyContext.pushContext(DynamicFilterData.class, null);
        DynamicPropertyFilter propertyFilter = new DynamicPropertyFilter();
        assertTrue(propertyFilter.isSerializable(null, jsonGenerator, null, writer));
        verifyNoInteractions(jsonGenerator, writer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nonEmptyIsSerializable() {
        //Implicitly return true
        when(dynamicFilterData.isAttributeExcluded(anyList()))
            .thenReturn(true);
        when(dynamicFilterData.isAttributeExcluded(Arrays.asList("CONTEXT_NAME_1", "WRITER_NAME")))
            .thenReturn(false);
        when(jsonGenerator.streamWriteContext()).thenReturn(context);
        when(context.getParent()).thenReturn(context).thenReturn(null);
        when(context.currentName()).thenReturn("CONTEXT_NAME_1");
        when(writer.getName()).thenReturn("WRITER_NAME");
        ResteasyContext.pushContext(DynamicFilterData.class, dynamicFilterData);
        DynamicPropertyFilter propertyFilter = new DynamicPropertyFilter();
        assertTrue(propertyFilter.isSerializable(null, jsonGenerator, null, writer));
        verify(jsonGenerator).streamWriteContext();
        verify(context, times(2)).getParent();
        verify(context).currentName();
    }

    @Test
    public void nonEmptyIsNotSerializable() {
        when(dynamicFilterData.isAttributeExcluded(Arrays.asList("CONTEXT_NAME_1", "WRITER_NAME")))
            .thenReturn(true);
        when(jsonGenerator.streamWriteContext()).thenReturn(context);
        when(context.getParent()).thenReturn(context).thenReturn(null);
        when(context.currentName()).thenReturn("CONTEXT_NAME_1");
        when(writer.getName()).thenReturn("WRITER_NAME");
        ResteasyContext.pushContext(DynamicFilterData.class, dynamicFilterData);
        DynamicPropertyFilter propertyFilter = new DynamicPropertyFilter();
        assertFalse(propertyFilter.isSerializable(null, jsonGenerator, null, writer));
        verify(jsonGenerator).streamWriteContext();
        verify(context, times(2)).getParent();
        verify(context).currentName();
    }

}

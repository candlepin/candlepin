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
package org.candlepin.jackson;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyFilter;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * CheckableBeanPropertyFilter
 *
 * A class used to make sure filters can be checked, so that
 * we can combine them properly, and change the behavior of
 * multiple stacked filters.
 */
public abstract class CheckableBeanPropertyFilter implements BeanPropertyFilter {

    /**
     * Lets us know if the filter can allow the current attribute
     * to be serialized.  It takes the same input args as serializeAsField
     * but should NEVER actually do serialization.  This allows us to use
     * multiple CheckableBeanPropertyFilter objects.
     *
     * @param obj from serializeAsField
     * @param jsonGenerator from serializeAsField
     * @param serializerProvider from serializeAsField
     * @param writer from serializeAsField
     * @return whether or not the object can be serialized
     */
    public abstract boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer);

    @Override
    public void serializeAsField(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) throws Exception {
        if (isSerializable(obj, jsonGenerator, serializerProvider, writer)) {
            writer.serializeAsField(obj, jsonGenerator, serializerProvider);
        }
    }
}

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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;


/**
 * CheckableBeanPropertyFilter
 *
 * A class used to make sure filters can be checked, so that
 * we can combine them properly, and change the behavior of
 * multiple stacked filters.
 */
public abstract class CheckableBeanPropertyFilter extends SimpleBeanPropertyFilter {

    /**
     * Lets us know if the filter can allow the current attribute
     * to be serialized.  It takes the same input args as serializeAsProperty/serializeAsElement
     * but should NEVER actually do serialization.  This allows us to use
     * multiple CheckableBeanPropertyFilter objects.
     *
     * @param obj from serializeAsProperty/serializeAsElement
     * @param jsonGenerator from serializeAsProperty/serializeAsElement
     * @param serializationContext from serializeAsProperty/serializeAsElement
     * @param writer from serializeAsProperty/serializeAsElement
     * @return whether or not the object can be serialized
     */
    public abstract boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializationContext serializationContext, PropertyWriter writer);

    @Override
    public void serializeAsProperty(Object obj, JsonGenerator jsonGenerator,
        SerializationContext serializationContext, PropertyWriter writer) throws Exception {
        if (isSerializable(obj, jsonGenerator, serializationContext, writer)) {
            writer.serializeAsProperty(obj, jsonGenerator, serializationContext);
        }
    }

    @Override
    public void serializeAsElement(Object obj, JsonGenerator jsonGenerator,
        SerializationContext serializationContext, PropertyWriter writer) throws Exception {
        if (isSerializable(obj, jsonGenerator, serializationContext, writer)) {
            writer.serializeAsElement(obj, jsonGenerator, serializationContext);
        }
    }

    @Override
    protected boolean include(PropertyWriter writer) {
        return true;
    }

    @Override
    protected boolean include(BeanPropertyWriter writer) {
        return true;
    }
}

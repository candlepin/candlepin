/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import java.lang.reflect.Method;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyFilter;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * HateoasBeanPropertyFilter: This is a Jackson filter which first checks if we
 * are serializing a nested object, and if so switches to HATEOAS style
 * serialization. Only properties whose getters have the HateoasField annotation
 * will be included in the resulting JSON. Otherwise we will serialize the
 * object normally.
 */
public class HateoasBeanPropertyFilter implements BeanPropertyFilter {

    private static Logger log = Logger.getLogger(HateoasBeanPropertyFilter.class);

    @Override
    public void serializeAsField(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) throws Exception {
        JsonStreamContext context = jsonGenerator.getOutputContext();

        if ((context.getParent() == null) || (!context.getParent().inObject())) {
            // Not serializing a nested object, so we'll write normally:
            writer.serializeAsField(obj, jsonGenerator, serializerProvider);
        }
        else {
            // We are doing HATEOAS serialization at this point, check if the getter
            // for this property has the annotation, serialize if so, skip it if not:
            if (hateoasAnnotationPresent(obj, writer.getName())) {
                writer.serializeAsField(obj, jsonGenerator, serializerProvider);
            }
        }
    }

    private Boolean hateoasAnnotationPresent(Object obj, String propertyName) {
        String methodName = "get" + propertyName.substring(0, 1).toUpperCase() +
            propertyName.substring(1);
        try {
            Method getter = obj.getClass().getMethod(methodName);
            HateoasField a = getter.getAnnotation(HateoasField.class);
            if (a != null) {
                return true;
            }
        }
        catch (NoSuchMethodException e) {
            log.warn("Unable to serialize property '" + propertyName +
                " without getter: " + methodName);
            return false;
        }
        return false;
    }

}

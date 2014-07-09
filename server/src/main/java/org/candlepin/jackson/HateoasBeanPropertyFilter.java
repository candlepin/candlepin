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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

/**
 * HateoasBeanPropertyFilter: This is a Jackson filter which first checks if we
 * are serializing a nested object, and if so switches to HATEOAS style
 * serialization. Only properties whose getters have the HateoasField annotation
 * will be included in the resulting JSON. Otherwise we will serialize the
 * object normally.
 */
public class HateoasBeanPropertyFilter extends JsonBeanPropertyFilter {

    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, PropertyWriter writer) {
        JsonStreamContext context = jsonGenerator.getOutputContext();

        if ((context.getParent() != null) && (context.getParent().inArray())) {
            // skip annotated fields if within array:
            if (!annotationPresent(obj, writer.getName(), HateoasArrayExclude.class)) {
                return true;
            }
        }
        // Check if we should trigger reduced HATEOAS serialization for a nested object by
        // looking for the annotation on the fields getter:
        else if ((context.getParent() != null) && (context.getParent().inObject())) {
            if (annotationPresent(obj, writer.getName(), HateoasInclude.class)) {
                return true;
            }
        }
        else {
            // Normal serialization:
            return true;
        }
        return false;
    }
}

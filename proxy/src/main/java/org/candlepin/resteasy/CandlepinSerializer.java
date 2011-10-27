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
package org.candlepin.resteasy;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.candlepin.model.Linkable;

/**
 * CandlepinSerializerFactory
 */
public class CandlepinSerializer extends JsonSerializer<Linkable> {

    @Override
    public void serialize(Linkable obj, JsonGenerator jg,
        SerializerProvider sp) throws IOException, JsonProcessingException {
        JsonStreamContext context = jg.getOutputContext();

        // If serializing a nested object, we just want to serialize ID and href, if the
        // object supports it.
        if (context.inObject()) {
            // Assuming here the getHref method exists, as this serializer is only
            // called for classes we register:
            try {
                jg.writeStartObject();

                // These two properties are standard.
                jg.writeStringField("href", obj.getHref());
                // IDs can be strings on some objects. :O
                jg.writeStringField("id", obj.getId().toString());

                for (Method method : obj.getClass().getMethods()) {
                    InfoProperty info = method.getAnnotation(InfoProperty.class);

                    if (info != null) {
                        try {
                            jg.writeStringField(info.value(),
                                    method.invoke(obj).toString());
                        }
                        catch (InvocationTargetException e) {
                            // just skip over it if there is a problem
                        }
                    }
                }

                jg.writeEndObject();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Otherwise, serialize normally:
        else {
            // Assume we only configure this serializer on a mapper that's using
            // our csp.
            CandlepinSerializerProvider csp = (CandlepinSerializerProvider) sp;
            JsonSerializer<Object> ser = csp.createSerializerSkipCustom(obj.getClass());
            ser.serialize(obj, jg, csp);
        }
    }
}

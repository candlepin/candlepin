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
package org.fedoraproject.candlepin.resteasy;

import java.io.IOException;
import java.lang.reflect.Method;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.fedoraproject.candlepin.model.Linkable;

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
        if (context.inObject() || context.inArray()) {
            Class<Linkable> c = (Class<Linkable>) obj.getClass();
            // Assuming here the getHref method exists, as this serializer is only
            // called for classes we register:
            try {
                Method getHref = c.getMethod("getHref", new Class []{});
                String s = (String) getHref.invoke(obj, new Object []{});
                jg.writeStartObject();
                jg.writeStringField("href", s);
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

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
package org.candlepin.util.apicrawl;

import java.lang.reflect.Type;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanSerializer;
import org.codehaus.jackson.schema.JsonSchema;

/**
 * NonRecursiveBeanSerializer
 */
public class NonRecursiveBeanSerializer extends BeanSerializer {

    private BeanSerializer wrapped;
    private Set<Type> seenClasses;

    NonRecursiveBeanSerializer(BeanSerializer wrapped, Set<Type> seenClasses) {
        super(BeanSerializer.createDummy(wrapped.handledType()));
        this.wrapped = wrapped;
        this.seenClasses = seenClasses;
    }

    @Override
    public JsonNode getSchema(SerializerProvider arg0, Type arg1)
        throws JsonMappingException {
        if (!seenClasses.contains(arg1)) {
            seenClasses.add(arg1);
            return wrapped.getSchema(arg0, arg1);
        }
        return JsonSchema.getDefaultSchemaNode();
    }
}

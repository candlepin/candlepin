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
package org.candlepin.policy.js;

import java.util.Map.Entry;

import org.candlepin.exceptions.IseException;
import org.candlepin.jackson.ExportBeanPropertyFilter;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.map.ser.impl.SimpleFilterProvider;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.mozilla.javascript.Scriptable;

/**
 * JsonContext
 *
 * A javascript context which provides each of its context args as
 * a single JSON string.
 */
public class JsonJsContext extends JsContext {

    private ObjectMapper mapper;
    private ArgumentJsContext nonSerializableContext;

    public JsonJsContext() {
        mapper = new ObjectMapper();

        // Since each class can only have one @JsonFilter annotation, and most have
        // ApiHateoas, We just default here to using the Export filter.
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new ExportBeanPropertyFilter());
        mapper.setFilters(filterProvider);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);

        nonSerializableContext = new ArgumentJsContext();
    }

    @Override
    public void applyTo(Scriptable scope) {
        scope.put("json_context", scope, this.buildJsonContext());
        nonSerializableContext.applyTo(scope);
    }

    public void put(String contextKey, Object contextVal, boolean serializable) {
        if (!serializable) {
            nonSerializableContext.put(contextKey, contextVal);
        }
        else {
            this.put(contextKey, contextVal);
        }
    }

    private String buildJsonContext() {
        ObjectNode mainNode = mapper.createObjectNode();
        for (Entry<String, Object> entry : this.contextArgs.entrySet()) {
            mainNode.putPOJO(entry.getKey(), entry.getValue());
        }
        try {
            return '(' + mapper.writeValueAsString(mainNode) + ')';
        }
        catch (Exception e) {
            throw new IseException("Failed to serialize", e);
        }
    }
}

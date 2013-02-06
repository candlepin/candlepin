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

import java.util.Map;
import java.util.Map.Entry;

import org.candlepin.exceptions.IseException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.map.ser.impl.SimpleBeanPropertyFilter;
import org.codehaus.jackson.map.ser.impl.SimpleFilterProvider;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;

/**
 * RulesObjectMapper
 */
public class RulesObjectMapper {

    private static class InstanceHolder {
        public static final RulesObjectMapper INSTANCE = new RulesObjectMapper();
    }

    private ObjectMapper mapper;

    private RulesObjectMapper() {
        this.mapper = new ObjectMapper();

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new RulesBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("ProductAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "productId"));
        filterProvider = filterProvider.addFilter("PoolAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated"));
        this.mapper.setFilters(filterProvider);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        this.mapper.setAnnotationIntrospector(pair);
    }

    public static RulesObjectMapper instance() {
        return InstanceHolder.INSTANCE;
    }

    public String toJsonString(Map<String, Object> toSerialize) {
        ObjectNode mainNode = this.mapper.createObjectNode();
        for (Entry<String, Object> entry : toSerialize.entrySet()) {
            mainNode.putPOJO(entry.getKey(), entry.getValue());
        }

        try {
            // Brackets required by JS when processing JSON.
            return '(' + this.mapper.writeValueAsString(mainNode) + ')';
        }
        catch (Exception e) {
            throw new IseException("Unable to serialize objects to JSON.", e);
        }
    }

    public <T extends Object> T toObject(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        }
        catch (Exception e) {
            throw new IseException("Unable to build object from JSON.", e);
        }
    }

}

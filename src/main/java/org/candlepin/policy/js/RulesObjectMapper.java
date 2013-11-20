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

import org.candlepin.exceptions.IseException;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.map.ser.impl.SimpleBeanPropertyFilter;
import org.codehaus.jackson.map.ser.impl.SimpleFilterProvider;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

/**
 * RulesObjectMapper
 *
 * A wrapper around a jackson ObjectMapper that is used to
 * serialize our model objects into JSON and to recreate them
 * from a JSON string.
 *
 * The mapper is configured with filters to filter out any info
 * on our model objects that is not useful to the rules.
 *
 * This class is implemented as a singleton because it is very
 * expensive to create a jackson ObjectMapper and it is preferred
 * to have it instantiated once.
 *
 */
public class RulesObjectMapper {

    private static Logger log = LoggerFactory.getLogger(RulesObjectMapper.class);

    private static class InstanceHolder {
        public static final RulesObjectMapper INSTANCE = new RulesObjectMapper();
    }

    private ObjectMapper mapper;

    private RulesObjectMapper() {
        this.mapper = new ObjectMapper();

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);
        filterProvider = filterProvider.addFilter("PoolAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "id"));
        filterProvider = filterProvider.addFilter("ProductPoolAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "productId",
                "id"));
        filterProvider = filterProvider.addFilter("ProvidedProductFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated"));
        filterProvider = filterProvider.addFilter("ConsumerFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("idCert"));
        filterProvider = filterProvider.addFilter("EntitlementFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("certificates", "consumer"));
        filterProvider = filterProvider.addFilter("OwnerFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("parentOwner", "consumers",
                "activationKeys", "environments", "pools"));
        this.mapper.setFilters(filterProvider);

        // Very important for deployments so new rules files can return additional
        // properties that this current server doesn't know how to serialize, but still
        // shouldn't fail on.
        this.mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);

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
            return this.mapper.writeValueAsString(mainNode);
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
            log.error("Error parsing JSON from rules into: " + clazz.getName(), e);
            log.error(json);
            throw new IseException("Unable to build object from JSON.", e);
        }
    }

}

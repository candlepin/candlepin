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

import org.candlepin.common.exceptions.IseException;
import org.candlepin.jackson.ProductCachedSerializationModule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Inject;

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

    private ObjectMapper mapper;

    @Inject
    @SuppressWarnings("checkstyle:indentation")
    public RulesObjectMapper(ProductCachedSerializationModule poolCachedSerializationModule) {
        this.mapper = new ObjectMapper();

        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
            .setFailOnUnknownId(false)
            .addFilter("PoolAttributeFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "id"))
            .addFilter("ProductAttributeFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "id", "product"))
            .addFilter("ProvidedProductFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("created", "updated"))
            .addFilter("ConsumerFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("idCert"))
            .addFilter("EntitlementFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("certificates", "consumer"))
            .addFilter("OwnerFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("parentOwner", "consumers", "activationKeys",
                    "environments", "pools"));

        this.mapper.setFilterProvider(filterProvider);

        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);

        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(hbm);
        mapper.registerModule(poolCachedSerializationModule);

        // Very important for deployments so new rules files can return additional
        // properties that this current server doesn't know how to serialize, but still
        // shouldn't fail on.
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
        this.mapper.setAnnotationIntrospector(pair);
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
            log.error("Unable to serialize objects to JSON.", e);
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

    public <T extends Object> T toObject(String json, TypeReference<T> typeref) {
        try {
            return mapper.readValue(json, typeref);
        }
        catch (Exception e) {
            log.error("Error parsing JSON from rules", e);
            log.error(json);
            throw new IseException("Unable to build object from JSON.", e);
        }
    }

    public String toJsonString(Object entity) throws JsonProcessingException {
        return mapper.writeValueAsString(entity);
    }
}

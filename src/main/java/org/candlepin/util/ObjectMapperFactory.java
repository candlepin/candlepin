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
package org.candlepin.util;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.policy.js.RulesObjectMapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Provider;



/**
 * The ObjectMapperFactory provides methods for generating ObjectMappers with a standardized base
 * configuration needed by Candlepin. The factory may be used directly via static factory method,
 * or injected as a standard provider.
 */
public class ObjectMapperFactory implements Provider<ObjectMapper> {

    @Override
    public ObjectMapper get() {
        return getObjectMapper();
    }

    /**
     * Creates a new ObjectMapper instance with the standard Candlepin configuration.
     *
     * @return
     *  a new ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
        mapper.registerModule(hbm);
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();

        // We're not going to want any of the JSON filters like DynamicPropertyFilter that we apply elsewhere
        filterProvider.setFailOnUnknownId(false);
        mapper.setFilterProvider(filterProvider);

        return mapper;
    }

    public static ObjectMapper getX509V3ExtensionUtilObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }

    public static ObjectMapper getHypervisorUpdateJobObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static ObjectMapper getSyncObjectMapper(Configuration config) {
        ObjectMapper mapper = getObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            config.getBoolean(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES));
        return mapper;
    }

    public static RulesObjectMapper getRulesObjectMapper() {
        return new RulesObjectMapper(getObjectMapper());
    }
}

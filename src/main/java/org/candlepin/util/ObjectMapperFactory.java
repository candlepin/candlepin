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
import com.google.inject.Provider;

import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.SimpleFilterProvider;
import tools.jackson.datatype.hibernate5.Hibernate5Module;
import tools.jackson.module.jaxb.JaxbAnnotationIntrospector;


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
        // Note: Jdk8Module and JavaTimeModule are no longer needed in Jackson 3.x
        // as their functionality is built into jackson-databind

        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        // We're not going to want any of the JSON filters like DynamicPropertyFilter that we apply elsewhere
        filterProvider.setFailOnUnknownId(false);

        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(hbm)
            .annotationIntrospector(pair)
            .filterProvider(filterProvider)
            .build();
    }

    public static ObjectMapper getX509V3ExtensionUtilObjectMapper() {
        return JsonMapper.builder()
            .changeDefaultVisibility(vc -> vc.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    }

    public static ObjectMapper getHypervisorUpdateJobObjectMapper() {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    public static ObjectMapper getSyncObjectMapper(Configuration config) {
        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);

        MapperBuilder<?, ?> builder = JsonMapper.builder()
            .addModule(hbm)
            .annotationIntrospector(pair)
            .filterProvider(filterProvider);

        if (config.getBoolean(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES)) {
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        else {
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        return builder.build();
    }

    public static RulesObjectMapper getRulesObjectMapper() {
        return new RulesObjectMapper(getObjectMapper());
    }
}

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
package org.candlepin.resteasy;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.PoolDTO;
import org.candlepin.dto.api.server.v1.PoolQuantityDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.ReleaseVerDTO;
import org.candlepin.jackson.AsyncJobStatusAnnotationMixin;
import org.candlepin.jackson.ConsumerTypeDeserializer;
import org.candlepin.jackson.DateSerializer;
import org.candlepin.jackson.DynamicPropertyFilter;
import org.candlepin.jackson.DynamicPropertyFilterMixIn;
import org.candlepin.jackson.GuestIdDeserializer;
import org.candlepin.jackson.OffsetDateTimeDeserializer;
import org.candlepin.jackson.OffsetDateTimeSerializer;
import org.candlepin.jackson.PoolAnnotationMixIn;
import org.candlepin.jackson.ProductAttributesMixIn;
import org.candlepin.jackson.ReleaseVersionWrapDeserializer;

import tools.jackson.core.Version;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.SimpleFilterProvider;
import tools.jackson.datatype.hibernate5.Hibernate5Module;
import tools.jackson.jaxrs.json.JacksonJsonProvider;
import tools.jackson.module.jaxb.JaxbAnnotationIntrospector;

import java.time.OffsetDateTime;
import java.util.Date;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.Provider;


/**
 * JsonProvider
 * <p/>
 * Our own json provider for jax-rs, allowing us to configure jackson as we see fit
 * and deal with input validation.
 */
@Provider
@Produces({"application/*+json", "text/json"})
@Consumes({"application/*+json", "text/json"})
public class JsonProvider extends JacksonJsonProvider {

    @Inject
    public JsonProvider(Configuration config) {
        this(config.getBoolean(ConfigProperties.PRETTY_PRINT));
    }

    public JsonProvider(boolean indentJson) {
        // Prefer jackson annotations, but use jaxb if no jackson.
        // Note: In Jackson 3.0, Annotations enum was removed; annotation introspectors
        // are now configured via the ObjectMapper builder
        super();

        // Note: Jdk8Module and JavaTimeModule are no longer needed in Jackson 3.x
        // as their functionality is built into jackson-databind

        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);

        SimpleModule customModule = new SimpleModule("CustomModule", new Version(1, 0, 0, null, null,
            null));
        // Ensure our DateSerializer is used for all Date objects
        customModule.addSerializer(Date.class, new DateSerializer());
        // Add custom de/serializers for OffsetDateTime
        customModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
        customModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        // Ensure we handle releaseVer fields properly
        customModule.addDeserializer(ReleaseVerDTO.class, new ReleaseVersionWrapDeserializer());
        customModule.addDeserializer(ConsumerTypeDTO.class, new ConsumerTypeDeserializer());

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider = filterProvider.addFilter("DTOFilter", new DynamicPropertyFilter());
        filterProvider.setDefaultFilter(new DynamicPropertyFilter());
        filterProvider.setFailOnUnknownId(false);

        // Build a mapper WITHOUT GuestIdDeserializer for use by GuestIdDeserializer itself
        // This avoids infinite recursion
        ObjectMapper mapperWithoutGuestIdDeserializer = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(hbm)
            .addModule(customModule)
            .annotationIntrospector(pair)
            .filterProvider(filterProvider)
            .configure(SerializationFeature.INDENT_OUTPUT, indentJson)
            .build();

        // Now add GuestIdDeserializer to a separate module, passing the mapper without it
        SimpleModule guestIdModule = new SimpleModule("GuestIdModule", new Version(1, 0, 0, null, null,
            null));
        guestIdModule.addDeserializer(GuestIdDTO.class,
            new GuestIdDeserializer(mapperWithoutGuestIdDeserializer));

        // Build the final mapper with all modules including GuestIdDeserializer
        JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(hbm)
            .addModule(customModule)
            .addModule(guestIdModule)
            .annotationIntrospector(pair)
            .filterProvider(filterProvider)
            .configure(SerializationFeature.INDENT_OUTPUT, indentJson)
            .addMixIn(Object.class, DynamicPropertyFilterMixIn.class)
            .addMixIn(AsyncJobStatusDTO.class, AsyncJobStatusAnnotationMixin.class)
            .addMixIn(PoolDTO.class, PoolAnnotationMixIn.class)
            .addMixIn(PoolQuantityDTO.class, PoolAnnotationMixIn.class)
            .addMixIn(ProductDTO.class, ProductAttributesMixIn.class)
            .build();

        setMapper(jsonMapper);
    }
}

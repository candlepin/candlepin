///**
// * Copyright (c) 2009 - 2012 Red Hat, Inc.
// *
// * This software is licensed to you under the GNU General Public License,
// * version 2 (GPLv2). There is NO WARRANTY for this software, express or
// * implied, including the implied warranties of MERCHANTABILITY or FITNESS
// * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
// * along with this software; if not, see
// * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
// *
// * Red Hat trademarks are not licensed under GPLv2. No permission is
// * granted to use or replicate Red Hat trademarks that are incorporated
// * in this software or its documentation.
// */
//package org.candlepin.resteasy;
//
//import org.candlepin.common.config.Configuration;
//import org.candlepin.common.jackson.DynamicPropertyFilter;
//import org.candlepin.common.jackson.HateoasBeanPropertyFilter;
//import org.candlepin.common.jackson.MultiFilter;
//import org.candlepin.config.ConfigProperties;
//import org.candlepin.jackson.DateSerializer;
//import org.candlepin.jackson.ProductCachedSerializationModule;
//
//import com.fasterxml.jackson.core.Version;
//import com.fasterxml.jackson.databind.AnnotationIntrospector;
//import com.fasterxml.jackson.databind.DeserializationFeature;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
//import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
//import com.fasterxml.jackson.databind.module.SimpleModule;
//import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
//import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
//import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
//import com.fasterxml.jackson.jaxrs.cfg.Annotations;
//import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
//import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.util.Date;
//
//import javax.ws.rs.Consumes;
//import javax.ws.rs.Produces;
//import javax.ws.rs.ext.Provider;
//
///**
// * JsonProvider
// *
// * Our own json provider for jax-rs, allowing us to configure jackson as we see fit
// * and deal with input validation.
// */
//@Component
//@Provider
//@Produces({"application/json", "application/*+json", "text/json"})
//@Consumes({"application/json", "application/*+json", "text/json"})
//public class JsonProvider extends JacksonJsonProvider {
//
//    //@Inject
//    @Autowired
//    public JsonProvider(Configuration config, ProductCachedSerializationModule productCachedModules) {
//        this(config.getBoolean(ConfigProperties.PRETTY_PRINT), productCachedModules);
//    }
//
//    public JsonProvider(boolean indentJson, ProductCachedSerializationModule productCachedModules) {
//        // Prefer jackson annotations, but use jaxb if no jackson.
//        super(Annotations.JACKSON, Annotations.JAXB);
//
//        ObjectMapper mapper = _mapperConfig.getDefaultMapper();
//
//        // Add the JDK8 module to support new goodies, like streams
//        mapper.registerModule(new Jdk8Module());
//
//        Hibernate5Module hbm = new Hibernate5Module();
//        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
//        mapper.registerModule(hbm);
//
//        SimpleModule dateModule = new SimpleModule("DateModule", new Version(1, 0, 0, null, null, null));
//        // Ensure our DateSerializer is used for all Date objects
//        dateModule.addSerializer(Date.class, new DateSerializer());
//        mapper.registerModule(dateModule);
//        mapper.registerModule(productCachedModules);
//        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        configureHateoasObjectMapper(mapper, indentJson);
//        setMapper(mapper);
//    }
//
//    private void configureHateoasObjectMapper(ObjectMapper mapper, boolean indentJson) {
//        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
//
//        if (indentJson) {
//            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
//        }
//
//        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
//        filterProvider = filterProvider.addFilter("ConsumerFilter",
//            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
//        filterProvider = filterProvider.addFilter("EntitlementFilter",
//            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
//        filterProvider = filterProvider.addFilter("OwnerFilter",
//            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
//        filterProvider = filterProvider.addFilter("GuestFilter",
//            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
//        filterProvider.setDefaultFilter(new DynamicPropertyFilter());
//        filterProvider.setFailOnUnknownId(false);
//        mapper.setFilterProvider(filterProvider);
//
//        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
//        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
//        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
//        mapper.setAnnotationIntrospector(pair);
//    }
//}
//

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
package org.candlepin.resteasy;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.jackson.DynamicPropertyFilter;
import org.candlepin.common.jackson.DynamicPropertyFilterMixIn;
import org.candlepin.common.jackson.HateoasBeanPropertyFilter;
import org.candlepin.common.jackson.MultiFilter;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.v1.DistributorVersionDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.dto.api.v1.NestedConsumerDTO;
import org.candlepin.dto.api.v1.NestedEntitlementDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductCertificateDTO;
import org.candlepin.dto.api.v1.ProductContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProvidedProductDTO;
import org.candlepin.dto.api.v1.ReleaseVerDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.dto.api.v1.SubscriptionDTO;
import org.candlepin.dto.api.v1.TimestampedEntity;
import org.candlepin.dto.api.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.jackson.DateSerializer;
import org.candlepin.jackson.OffsetDateTimeDeserializer;
import org.candlepin.jackson.OffsetDateTimeSerializer;
import org.candlepin.jackson.PoolAnnotationMixIn;
import org.candlepin.jackson.ProductAttributesMixIn;
import org.candlepin.jackson.ReleaseVersionWrapDeserializer;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Inject;

import java.time.OffsetDateTime;
import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.Provider;

/**
 * JsonProvider
 *
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
        super(Annotations.JACKSON, Annotations.JAXB);

        ObjectMapper mapper = _mapperConfig.getDefaultMapper();

        // Add the JDK8 module to support new goodies, like streams
        mapper.registerModule(new Jdk8Module());

        // Add the new JDK8 date/time module, with custom de/serializers
        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
        timeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        mapper.registerModule(timeModule);

        Hibernate5Module hbm = new Hibernate5Module();
        hbm.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
        mapper.registerModule(hbm);

        SimpleModule customModule = new SimpleModule("CustomModule", new Version(1, 0, 0, null, null,
            null));
        // Ensure our DateSerializer is used for all Date objects
        customModule.addSerializer(Date.class, new DateSerializer());
        // Ensure we handle releaseVer fields properly
        customModule.addDeserializer(ReleaseVerDTO.class, new ReleaseVersionWrapDeserializer());
        mapper.registerModule(customModule);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        configureHateoasObjectMapper(mapper, indentJson);
        setMapper(mapper);
    }

    private void configureHateoasObjectMapper(ObjectMapper mapper, boolean indentJson) {
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        if (indentJson) {
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        }

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider = filterProvider.addFilter("DTOFilter", new DynamicPropertyFilter());

        // TODO: Once all DTOs are ported to the openapi spec, stop adding the following
        //  filters: LegacyDTOFilter, ConsumerFilter, EntitlementFilter, OwnerFilter, GuestFilter.
        filterProvider = filterProvider.addFilter("LegacyDTOFilter", new DynamicPropertyFilter());
        filterProvider = filterProvider.addFilter("ConsumerFilter",
            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
        filterProvider = filterProvider.addFilter("EntitlementFilter",
            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
        filterProvider = filterProvider.addFilter("OwnerFilter",
            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));
        filterProvider = filterProvider.addFilter("GuestFilter",
            new MultiFilter(new HateoasBeanPropertyFilter(), new DynamicPropertyFilter()));

        filterProvider.setDefaultFilter(new DynamicPropertyFilter());
        filterProvider.setFailOnUnknownId(false);
        mapper.setFilterProvider(filterProvider);
        addMixInAnnotationsForDTOs(mapper);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);
    }

    /**
     * Allows us to add annotations to the DTO classes whose source code we don't control (autogenerated from
     * openapi spec).
     */
    private void addMixInAnnotationsForDTOs(ObjectMapper mapper) {
        mapper.addMixIn(ActivationKeyDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(ActivationKeyPoolDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(AsyncJobStatusDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(AttributeDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(BrandingDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(CdnDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(CertificateDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(CertificateSerialDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(ConsumerTypeDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(ContentOverrideDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(DeletedConsumerDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(DistributorVersionCapabilityDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(DistributorVersionDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(EntitlementDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(GuestIdDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(GuestIdDTOArrayElement.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(HypervisorConsumerDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(HypervisorUpdateResultDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(NestedConsumerDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(NestedEntitlementDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(NestedOwnerDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(OwnerDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(PermissionBlueprintDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(PoolDTO.class, PoolAnnotationMixIn.class);
        mapper.addMixIn(ProductCertificateDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(ProductContentDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(ProductDTO.class, ProductAttributesMixIn.class);
        mapper.addMixIn(ProvidedProductDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(RoleDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(StatusDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(TimestampedEntity.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(UserDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(SubscriptionDTO.class, DynamicPropertyFilterMixIn.class);
        mapper.addMixIn(UpstreamConsumerDTO.class, DynamicPropertyFilterMixIn.class);
        //TODO: Add more mix-ins here as more DTOs are ported to openapi spec
    }
}

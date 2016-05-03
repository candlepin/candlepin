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
package org.candlepin.swagger;

import org.candlepin.common.jackson.HateoasInclude;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.TypeNameResolver;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.Xml;
import io.swagger.models.properties.AbstractNumericProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.models.properties.UUIDProperty;
import io.swagger.util.AllowableValues;
import io.swagger.util.AllowableValuesUtils;
import io.swagger.util.PrimitiveType;

/**
 * This custom model converter is adapted version of standard swagger
 * ModelResolver.
 *
 * The ModelResolver in Swagger has responsibility to introspect Models (in our
 * case Entities) and decide which fields to include in swagger.json. The
 * ModelResolver tries to account for various standard exlusion annotations such
 * as JsonIgnore and XmlTransient, however Candlepin has much more complex rules
 * for serialization that cannot bea easily captured just by adding new
 * ModelConverter into the chain of Converters. Thats why this ModelResolver is
 * being modified. The new capabilities of this ModelResolver are: Detection of
 * nested Model properties, Hateoas Serialization.
 *
 * <h2>Detection of nested Model properties</h2> Candlepin serialization is
 * configured in JsonProvider class. This class defines several JSON filters
 * that enable Hateoas Serialization for nested fields. That means that an
 * entity such as Owner will be serialized differently based on whether its
 * top-level entity returned as Response or nested property e.g. parentOwner
 * property inside an Owner. To allow for such behavior, this modified converter
 * is detecting such nested property and uses inheritance hack (see java doc of
 * NestedComplexType class) to make sure that a new model with prefix 'Nested'
 * (e.g. NestedOwner) is created for each such nested type. It is important to
 * note that not all entities have it's nested counterparts. Only those that
 * have JsonFilter annotation with appropriate filter (list of t hose is in
 * JsonProvider) should have the nested counterpart.
 *
 * <h2>Hateoas Serializatoin</h2> See JavaDoc of HateoasInclude annotation for
 * more details of how the serialization works. This converter make sure that
 * when a model is nested (e.g. NestedOwner) it will include only those fields
 * that are annotated for HateoasInclude
 *
 *
 * @author fnguyen
 *
 */
public class CandlepinSwaggerModelConverter extends AbstractModelConverter implements ModelConverter {
    private static final Logger log = LoggerFactory.getLogger(CandlepinSwaggerModelConverter.class);
    private Map<JavaType, NestedComplexType> nestedJavaTypes = new HashMap<JavaType, NestedComplexType>();

    @Inject
    public CandlepinSwaggerModelConverter(ObjectMapper mapper) {
        super(mapper);
    }

    public ObjectMapper objectMapper() {
        return pMapper;
    }

    protected boolean shouldIgnoreClass(Type type) {
        if (type instanceof Class) {
            Class<?> cls = (Class<?>) type;
            if (cls.getName().equals("javax.ws.rs.Response")) {
                return true;
            }
        }
        else {
            if (type instanceof com.fasterxml.jackson.core.type.ResolvedType) {
                com.fasterxml.jackson.core.type.ResolvedType rt =
                    (com.fasterxml.jackson.core.type.ResolvedType) type;
                log.debug("Can't check class {}, {}", type, rt.getRawClass().getName());
                if (rt.getRawClass().equals(Class.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Property resolveProperty(Type type, ModelConverterContext context, Annotation[] annotations,
        Iterator<ModelConverter> next) {
        JavaType propType = null;

        /**
         * See java doc of NestedComplexType. This unwrapping makes sure that a
         * real type of field is passed to _mapper
         */
        if (type instanceof NestedComplexType) {
            propType = pMapper.constructType(((NestedComplexType) type).getOriginalType());
        }
        else {
            propType = pMapper.constructType(type);
        }

        log.debug("resolveProperty {}", propType);

        Property property = null;
        if (propType.isContainerType()) {
            JavaType keyType = propType.getKeyType();
            JavaType valueType = propType.getContentType();
            if (keyType != null && valueType != null) {
                property = new MapProperty()
                    .additionalProperties(context.resolveProperty(valueType, new Annotation[] {}));
            }
            else if (valueType != null) {
                ArrayProperty arrayProperty = new ArrayProperty()
                    .items(context.resolveProperty(valueType, new Annotation[] {}));
                if (pIsSetType(propType.getRawClass())) {
                    arrayProperty.setUniqueItems(true);
                }
                property = arrayProperty;
            }
        }
        else {
            property = PrimitiveType.createProperty(propType);
        }

        if (property == null) {
            if (propType.isEnumType()) {
                property = new StringProperty();
                pAddEnumProps(propType.getRawClass(), property);
            }
            else if (pIsOptionalType(propType)) {
                property = context.resolveProperty(propType.containedType(0), null);
            }
            else {
                // complex type
                Model innerModel = context.resolve(type);
                if (innerModel instanceof ModelImpl) {
                    ModelImpl mi = (ModelImpl) innerModel;
                    property = new RefProperty(
                        StringUtils.isNotEmpty(mi.getReference()) ? mi.getReference() : mi.getName());
                }
            }
        }

        return property;
    }

    private boolean pIsOptionalType(JavaType propType) {
        return Arrays.asList("com.google.common.base.Optional", "java.util.Optional")
            .contains(propType.getRawClass().getCanonicalName());
    }

    protected void pAddEnumProps(Class<?> propClass, Property property) {
        final boolean useIndex = pMapper.isEnabled(SerializationFeature.WRITE_ENUMS_USING_INDEX);
        final boolean useToString = pMapper.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);

        @SuppressWarnings("unchecked")
        Class<Enum<?>> enumClass = (Class<Enum<?>>) propClass;
        for (Enum<?> en : enumClass.getEnumConstants()) {
            String n;
            if (useIndex) {
                n = String.valueOf(en.ordinal());
            }
            else if (useToString) {
                n = en.toString();
            }
            else {
                n = pIntr.findEnumValue(en);
            }
            if (property instanceof StringProperty) {
                StringProperty sp = (StringProperty) property;
                sp._enum(n);
            }
        }
    }

    public Model resolve(Type rawType, ModelConverterContext context, Iterator<ModelConverter> next) {
        if (this.shouldIgnoreClass(rawType)) {
            return null;
        }

        /**
         * See java doc of NestedComplexType. This unwrapping makes sure that a
         * real type of field used throughout the method. At the same time flag
         * 'isNested' helps to indicate later in the method that this type may
         * be introspected as Hateoas enabled field
         */
        boolean isNested = false;
        if (rawType instanceof NestedComplexType) {
            isNested = true;
            NestedComplexType nested = (NestedComplexType) rawType;
            rawType = nested.getOriginalType();
        }
        JavaType type = pMapper.constructType(rawType);

        if (type.isEnumType() || PrimitiveType.fromType(type) != null) {
            // We don't build models for primitive types
            return null;
        }

        final BeanDescription beanDesc = pMapper.getSerializationConfig().introspect(type);
        // Couple of possibilities for defining
        String name = isNested ? "Nested" : "";
        name += pTypeName(type, beanDesc);

        if ("Object".equals(name)) {
            return new ModelImpl();
        }

        final ModelImpl model = new ModelImpl().type(ModelImpl.OBJECT).name(name)
            .description(pDescription(beanDesc.getClassInfo()));

        if (!type.isContainerType()) {
            // define the model here to support self/cyclic referencing of
            // models
            context.defineModel(name, model, type, null);
        }

        if (type.isContainerType()) {
            // We treat collections as primitive types, just need to add models
            // for values (if any)
            context.resolve(type.getContentType());
            return null;
        }
        // if XmlRootElement annotation, construct an Xml object and attach it
        // to the model
        XmlRootElement rootAnnotation = beanDesc.getClassAnnotations().get(XmlRootElement.class);
        if (rootAnnotation != null && !"".equals(rootAnnotation.name()) &&
            !"##default".equals(rootAnnotation.name())) {
            log.debug(rootAnnotation.toString());
            Xml xml = new Xml().name(rootAnnotation.name());
            if (rootAnnotation.namespace() != null && !"".equals(rootAnnotation.namespace()) &&
                !"##default".equals(rootAnnotation.namespace())) {
                xml.namespace(rootAnnotation.namespace());
            }
            model.xml(xml);
        }

        // see if @JsonIgnoreProperties exist
        Set<String> propertiesToIgnore = new HashSet<String>();
        JsonIgnoreProperties ignoreProperties = beanDesc.getClassAnnotations()
            .get(JsonIgnoreProperties.class);
        if (ignoreProperties != null) {
            propertiesToIgnore.addAll(Arrays.asList(ignoreProperties.value()));
        }

        final ApiModel apiModel = beanDesc.getClassAnnotations().get(ApiModel.class);
        String disc = (apiModel == null) ? "" : apiModel.discriminator();

        if (apiModel != null && StringUtils.isNotEmpty(apiModel.reference())) {
            model.setReference(apiModel.reference());
        }

        if (disc.isEmpty()) {
            // longer method would involve
            // AnnotationIntrospector.findTypeResolver(...) but:
            JsonTypeInfo typeInfo = beanDesc.getClassAnnotations().get(JsonTypeInfo.class);
            if (typeInfo != null) {
                disc = typeInfo.property();
            }
        }
        if (!disc.isEmpty()) {
            model.setDiscriminator(disc);
        }

        List<Property> props = new ArrayList<Property>();
        for (BeanPropertyDefinition propDef : beanDesc.findProperties()) {
            parseProperty(context, isNested, beanDesc, propertiesToIgnore, props, propDef);
        }

        Collections.sort(props, getPropertyComparator());

        Map<String, Property> modelProps = new LinkedHashMap<String, Property>();
        for (Property prop : props) {
            modelProps.put(prop.getName(), prop);
        }
        model.setProperties(modelProps);

        /**
         * This must be done after model.setProperties so that the model's set
         * of properties is available to filter from any subtypes
         **/
        if (!resolveSubtypes(model, beanDesc, context)) {
            model.setDiscriminator(null);
        }

        return model;
    }

    private void parseProperty(ModelConverterContext context, boolean isNested,
        final BeanDescription beanDesc, Set<String> propertiesToIgnore, List<Property> props,
        BeanPropertyDefinition propDef) {
        Property property = null;
        String propName = propDef.getName();
        Annotation[] annotations = null;

        propName = getPropName(propDef, propName);

        PropertyMetadata md = propDef.getMetadata();

        boolean hasSetter = false, hasGetter = false;
        if (propDef.getSetter() == null) {
            hasSetter = false;
        }
        else {
            hasSetter = true;
        }
        if (propDef.getGetter() != null) {
            JsonProperty pd = propDef.getGetter().getAnnotation(JsonProperty.class);
            if (pd != null) {
                hasGetter = true;
            }
        }
        Boolean isReadOnly = null;
        if (!hasSetter & hasGetter) {
            isReadOnly = Boolean.TRUE;
        }
        else {
            isReadOnly = Boolean.FALSE;
        }

        final AnnotatedMember member = propDef.getPrimaryMember();

        if (member != null && !propertiesToIgnore.contains(propName) &&
            /**
             * If the owning type is nested than we should include only those
             * fields that have the Hateoas annotation.
             */
            !(isNested && !member.hasAnnotation(HateoasInclude.class))) {

            List<Annotation> annotationList = new ArrayList<Annotation>();
            for (Annotation a : member.annotations()) {
                annotationList.add(a);
            }

            annotations = annotationList.toArray(new Annotation[annotationList.size()]);

            ApiModelProperty mp = member.getAnnotation(ApiModelProperty.class);

            if (mp != null && mp.readOnly()) {
                isReadOnly = mp.readOnly();
            }

            Type nested = null;
            JavaType propType = member.getType(beanDesc.bindingsForBeanType());
            JsonFilter jsonFilter = propType.getRawClass().getAnnotation(JsonFilter.class);

            /**
             * At this point the propType is a type of some nested field of the
             * type that is being processed. The condition checks if this
             * particular type should have Hateoas serialization enabled. In
             * other words, if we should create a new Nested* model.
             */
            if (jsonFilter != null && (jsonFilter.value().equals("ConsumerFilter") ||
                jsonFilter.value().equals("EntitlementFilter") || jsonFilter.value().equals("OwnerFilter") ||
                jsonFilter.value().equals("GuestFilter"))) {
                if (!nestedJavaTypes.containsKey(propType)) {
                    nestedJavaTypes.put(propType, new NestedComplexType(propType));
                }
                nested = nestedJavaTypes.get(propType);
            }
            else {
                nested = propType;
            }

            // allow override of name from annotation
            if (mp != null && !mp.name().isEmpty()) {
                propName = mp.name();
            }

            if (mp != null && !mp.dataType().isEmpty()) {
                property = resolveApiAnnotated(context, property, annotations, mp, propType);
            }

            // no property from override, construct from propType
            if (property == null) {
                if (mp != null && StringUtils.isNotEmpty(mp.reference())) {
                    property = new RefProperty(mp.reference());
                }
                else if (member.getAnnotation(JsonIdentityInfo.class) != null) {
                    property = GeneratorWrapper.processJsonIdentity(propType, context, pMapper,
                        member.getAnnotation(JsonIdentityInfo.class),
                        member.getAnnotation(JsonIdentityReference.class));
                }
                if (property == null) {
                    property = context.resolveProperty(nested, annotations);
                }
            }

            if (property != null) {
                addMetadataToProperty(property, propName, md, isReadOnly, member, mp);
                applyBeanValidatorAnnotations(property, annotations);
                props.add(property);
            }
        }
    }

    private String getPropName(BeanPropertyDefinition propDef, String propName) {
        // hack to avoid clobbering properties with get/is names
        // it's ugly but gets around
        // https://github.com/swagger-api/swagger-core/issues/415
        if (propDef.getPrimaryMember() != null) {
            java.lang.reflect.Member member = propDef.getPrimaryMember().getMember();
            if (member != null) {
                String altName = member.getName();
                if (altName != null) {
                    final int length = altName.length();
                    for (String prefix : Arrays.asList("get", "is")) {
                        final int offset = prefix.length();
                        if (altName.startsWith(prefix) && length > offset &&
                            !Character.isUpperCase(altName.charAt(offset))) {
                            propName = altName;
                            break;
                        }
                    }
                }
            }
        }
        return propName;
    }

    private void addMetadataToProperty(Property property, String propName, PropertyMetadata md,
        Boolean isReadOnly, final AnnotatedMember member, ApiModelProperty mp) {
        property.setName(propName);

        if (mp != null && !mp.access().isEmpty()) {
            property.setAccess(mp.access());
        }

        Boolean required = md.getRequired();
        if (required != null) {
            property.setRequired(required);
        }

        String description = pIntr.findPropertyDescription(member);
        if (description != null && !"".equals(description)) {
            property.setDescription(description);
        }

        Integer index = pIntr.findPropertyIndex(member);
        if (index != null) {
            property.setPosition(index);
        }
        property.setDefault(pFindDefaultValue(member));
        property.setExample(pFindExampleValue(member));
        property.setReadOnly(pFindReadOnly(member));

        if (property.getReadOnly() == null) {
            if (isReadOnly) {
                property.setReadOnly(isReadOnly);
            }
        }
        if (mp != null) {
            final AllowableValues allowableValues = AllowableValuesUtils.create(mp.allowableValues());
            if (allowableValues != null) {
                final Map<PropertyBuilder.PropertyId, Object> args = allowableValues.asPropertyArguments();
                PropertyBuilder.merge(property, args);
            }
        }
        JAXBAnnotationsHelper.apply(member, property);
    }

    private Property resolveApiAnnotated(ModelConverterContext context, Property property,
        Annotation[] annotations, ApiModelProperty mp, JavaType propType) {
        String or = mp.dataType();

        JavaType innerJavaType = null;
        log.debug("overriding datatype from {} to {}", propType, or);

        if (or.toLowerCase().startsWith("list[")) {
            String innerType = or.substring(5, or.length() - 1);
            ArrayProperty p = new ArrayProperty();
            Property primitiveProperty = PrimitiveType.createProperty(innerType);
            if (primitiveProperty != null) {
                p.setItems(primitiveProperty);
            }
            else {
                innerJavaType = getInnerType(innerType);
                p.setItems(context.resolveProperty(innerJavaType, annotations));
            }
            property = p;
        }
        else if (or.toLowerCase().startsWith("map[")) {
            int pos = or.indexOf(",");
            if (pos > 0) {
                String innerType = or.substring(pos + 1, or.length() - 1);
                MapProperty p = new MapProperty();
                Property primitiveProperty = PrimitiveType.createProperty(innerType);
                if (primitiveProperty != null) {
                    p.setAdditionalProperties(primitiveProperty);
                }
                else {
                    innerJavaType = getInnerType(innerType);
                    p.setAdditionalProperties(context.resolveProperty(innerJavaType, annotations));
                }
                property = p;
            }
        }
        else {
            Property primitiveProperty = PrimitiveType.createProperty(or);
            if (primitiveProperty != null) {
                property = primitiveProperty;
            }
            else {
                innerJavaType = getInnerType(or);
                property = context.resolveProperty(innerJavaType, annotations);
            }
        }
        if (innerJavaType != null) {
            context.resolve(innerJavaType);
        }
        return property;
    }

    private enum GeneratorWrapper {
        PROPERTY(ObjectIdGenerators.PropertyGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                ModelConverterContext context, ObjectMapper mapper) {
                /*
                 * When generator = ObjectIdGenerators.PropertyGenerator.class
                 * and
                 *
                 * @JsonIdentityReference(alwaysAsId = false) then property is
                 * serialized in the same way it is done
                 * without @JsonIdentityInfo annotation.
                 */
                return null;
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type, ModelConverterContext context,
                ObjectMapper mapper) {
                final BeanDescription beanDesc = mapper.getSerializationConfig().introspect(type);
                for (BeanPropertyDefinition def : beanDesc.findProperties()) {
                    final String name = def.getName();
                    if (name != null && name.equals(propertyName)) {
                        final AnnotatedMember propMember = def.getPrimaryMember();
                        final JavaType propType = propMember.getType(beanDesc.bindingsForBeanType());
                        if (PrimitiveType.fromType(propType) != null) {
                            return PrimitiveType.createProperty(propType);
                        }
                        else {
                            return context.resolveProperty(propType,
                                Iterables.toArray(propMember.annotations(), Annotation.class));
                        }
                    }
                }
                return null;
            }
        },
        INT(ObjectIdGenerators.IntSequenceGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                ModelConverterContext context, ObjectMapper mapper) {
                Property id = new IntegerProperty();
                return process(id, propertyName, type, context);
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type, ModelConverterContext context,
                ObjectMapper mapper) {
                return new IntegerProperty();
            }
        },
        UUID(ObjectIdGenerators.UUIDGenerator.class) {
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                ModelConverterContext context, ObjectMapper mapper) {
                Property id = new UUIDProperty();
                return process(id, propertyName, type, context);
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type, ModelConverterContext context,
                ObjectMapper mapper) {
                return new UUIDProperty();
            }
        },
        NONE(ObjectIdGenerators.None.class) {
            // When generator = ObjectIdGenerators.None.class property should be
            // processed as normal property.
            @Override
            protected Property processAsProperty(String propertyName, JavaType type,
                ModelConverterContext context, ObjectMapper mapper) {
                return null;
            }

            @Override
            protected Property processAsId(String propertyName, JavaType type, ModelConverterContext context,
                ObjectMapper mapper) {
                return null;
            }
        };

        private final Class<? extends ObjectIdGenerator> generator;

        GeneratorWrapper(Class<? extends ObjectIdGenerator> generator) {
            this.generator = generator;
        }

        protected abstract Property processAsProperty(String propertyName, JavaType type,
            ModelConverterContext context, ObjectMapper mapper);

        protected abstract Property processAsId(String propertyName, JavaType type,
            ModelConverterContext context, ObjectMapper mapper);

        public static Property processJsonIdentity(JavaType type, ModelConverterContext context,
            ObjectMapper mapper, JsonIdentityInfo identityInfo, JsonIdentityReference identityReference) {
            final GeneratorWrapper wrapper = identityInfo != null ?
                getWrapper(identityInfo.generator()) : null;
            if (wrapper == null) {
                return null;
            }
            if (identityReference != null && identityReference.alwaysAsId()) {
                return wrapper.processAsId(identityInfo.property(), type, context, mapper);
            }
            else {
                return wrapper.processAsProperty(identityInfo.property(), type, context, mapper);
            }
        }

        private static GeneratorWrapper getWrapper(Class<?> generator) {
            for (GeneratorWrapper value : GeneratorWrapper.values()) {
                if (value.generator.isAssignableFrom(generator)) {
                    return value;
                }
            }
            return null;
        }

        private static Property process(Property id, String propertyName, JavaType type,
            ModelConverterContext context) {
            id.setName(propertyName);
            final Model model = context.resolve(type);
            if (model instanceof ModelImpl) {
                ModelImpl mi = (ModelImpl) model;
                mi.getProperties().put(propertyName, id);
                return new RefProperty(
                    StringUtils.isNotEmpty(mi.getReference()) ? mi.getReference() : mi.getName());
            }
            return null;
        }
    }

    protected void applyBeanValidatorAnnotations(Property property, Annotation[] annotations) {
        Map<String, Annotation> annos = new HashMap<String, Annotation>();
        if (annotations != null) {
            for (Annotation anno : annotations) {
                annos.put(anno.annotationType().getName(), anno);
            }
        }
        if (annos.containsKey("javax.validation.constraints.NotNull")) {
            property.setRequired(true);
        }
        if (annos.containsKey("javax.validation.constraints.Min")) {
            if (property instanceof AbstractNumericProperty) {
                Min min = (Min) annos.get("javax.validation.constraints.Min");
                AbstractNumericProperty ap = (AbstractNumericProperty) property;
                ap.setMinimum(new Double(min.value()));
            }
        }
        if (annos.containsKey("javax.validation.constraints.Max")) {
            if (property instanceof AbstractNumericProperty) {
                Max max = (Max) annos.get("javax.validation.constraints.Max");
                AbstractNumericProperty ap = (AbstractNumericProperty) property;
                ap.setMaximum(new Double(max.value()));
            }
        }
        if (annos.containsKey("javax.validation.constraints.Size")) {
            Size size = (Size) annos.get("javax.validation.constraints.Size");
            if (property instanceof AbstractNumericProperty) {
                AbstractNumericProperty ap = (AbstractNumericProperty) property;
                ap.setMinimum(new Double(size.min()));
                ap.setMaximum(new Double(size.max()));
            }
            else if (property instanceof StringProperty) {
                StringProperty sp = (StringProperty) property;
                sp.minLength(new Integer(size.min()));
                sp.maxLength(new Integer(size.max()));
            }
            else if (property instanceof ArrayProperty) {
                ArrayProperty sp = (ArrayProperty) property;
                sp.setMinItems(size.min());
                sp.setMaxItems(size.max());
            }
        }
        if (annos.containsKey("javax.validation.constraints.DecimalMin")) {
            DecimalMin min = (DecimalMin) annos.get("javax.validation.constraints.DecimalMin");
            if (property instanceof AbstractNumericProperty) {
                AbstractNumericProperty ap = (AbstractNumericProperty) property;
                ap.setMinimum(new Double(min.value()));
            }
        }
        if (annos.containsKey("javax.validation.constraints.DecimalMax")) {
            DecimalMax max = (DecimalMax) annos.get("javax.validation.constraints.DecimalMax");
            if (property instanceof AbstractNumericProperty) {
                AbstractNumericProperty ap = (AbstractNumericProperty) property;
                ap.setMaximum(new Double(max.value()));

            }
        }
        if (annos.containsKey("javax.validation.constraints.Pattern")) {
            Pattern pattern = (Pattern) annos.get("javax.validation.constraints.Pattern");
            if (property instanceof StringProperty) {
                StringProperty ap = (StringProperty) property;
                ap.setPattern(pattern.regexp());
            }
        }
    }

    protected JavaType getInnerType(String innerType) {
        try {
            Class<?> innerClass = Class.forName(innerType);
            if (innerClass != null) {
                TypeFactory tf = pMapper.getTypeFactory();
                return tf.constructType(innerClass);
            }
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean resolveSubtypes(ModelImpl model, BeanDescription bean, ModelConverterContext context) {
        final List<NamedType> types = pIntr.findSubtypes(bean.getClassInfo());
        if (types == null) {
            return false;
        }
        int count = 0;
        final Class<?> beanClass = bean.getClassInfo().getAnnotated();
        for (NamedType subtype : types) {
            final Class<?> subtypeType = subtype.getType();
            if (!beanClass.isAssignableFrom(subtypeType)) {
                continue;
            }

            final Model subtypeModel = context.resolve(subtypeType);

            if (subtypeModel instanceof ModelImpl) {
                final ModelImpl impl = (ModelImpl) subtypeModel;

                // check if model name was inherited
                if (impl.getName().equals(model.getName())) {
                    impl.setName(pTypeNameResolver.nameForType(pMapper.constructType(subtypeType),
                        TypeNameResolver.Options.SKIP_API_MODEL));
                }

                // remove shared properties defined in the parent
                final Map<String, Property> baseProps = model.getProperties();
                final Map<String, Property> subtypeProps = impl.getProperties();
                if (baseProps != null && subtypeProps != null) {
                    for (Map.Entry<String, Property> entry : baseProps.entrySet()) {
                        if (entry.getValue().equals(subtypeProps.get(entry.getKey()))) {
                            subtypeProps.remove(entry.getKey());
                        }
                    }
                }

                impl.setDiscriminator(null);
                ComposedModel child = new ComposedModel().parent(new RefModel(model.getName())).child(impl);
                context.defineModel(impl.getName(), child);
                ++count;
            }
        }
        return count != 0;
    }
}

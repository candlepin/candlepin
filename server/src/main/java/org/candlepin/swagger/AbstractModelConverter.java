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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.annotation.XmlElement;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.jackson.SwaggerAnnotationIntrospector;
import io.swagger.jackson.TypeNameResolver;
import io.swagger.models.Model;
import io.swagger.models.properties.Property;

/**
 * Class adopted from https://github.com/swagger-api. Converters are Swagger
 * extension points.
 *
 * @author fnguyen
 *
 */
public abstract class AbstractModelConverter implements ModelConverter {
    protected final ObjectMapper pMapper;
    protected final AnnotationIntrospector pIntr;
    protected final TypeNameResolver pTypeNameResolver = TypeNameResolver.std;
    /**
     * Minor optimization: no need to keep on resolving same types over and over
     * again.
     */
    protected Map<JavaType, String> pResolvedTypeNames = new ConcurrentHashMap<JavaType, String>();

    protected AbstractModelConverter(ObjectMapper mapper) {
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);

        mapper.registerModule(new SimpleModule("swagger", Version.unknownVersion()) {
            @Override
            public void setupModule(SetupContext context) {
                context.insertAnnotationIntrospector(new SwaggerAnnotationIntrospector());
            }
        });
        pMapper = mapper;
        pIntr = mapper.getSerializationConfig().getAnnotationIntrospector();

    }

    protected static Comparator<Property> getPropertyComparator() {
        return new Comparator<Property>() {
            @Override
            public int compare(Property one, Property two) {
                if (one.getPosition() == null && two.getPosition() == null) {
                    return 0;
                }
                if (one.getPosition() == null) {
                    return -1;
                }
                if (two.getPosition() == null) {
                    return 1;
                }
                return one.getPosition().compareTo(two.getPosition());
            }
        };
    }

    @Override
    public Property resolveProperty(Type type, ModelConverterContext context, Annotation[] annotations,
        Iterator<ModelConverter> chain) {
        if (chain.hasNext()) {
            return chain.next().resolveProperty(type, context, annotations, chain);
        }
        else {
            return null;
        }
    }

    protected String pDescription(Annotated ann) {
        // while name suggests it's only for properties, should work for any
        // Annotated thing.
        // also; with Swagger introspector's help, should get it from
        // ApiModel/ApiModelProperty
        return pIntr.findPropertyDescription(ann);
    }

    protected String pTypeName(JavaType type) {
        return pTypeName(type, null);
    }

    protected String pTypeName(JavaType type, BeanDescription beanDesc) {
        String name = pResolvedTypeNames.get(type);
        if (name != null) {
            return name;
        }
        name = pFindTypeName(type, beanDesc);
        pResolvedTypeNames.put(type, name);
        return name;
    }

    protected String pFindTypeName(JavaType type, BeanDescription beanDesc) {
        // First, handle container types; they require recursion
        if (type.isArrayType()) {
            return "Array";
        }

        if (type.isMapLikeType()) {
            return "Map";
        }

        if (type.isContainerType()) {
            if (Set.class.isAssignableFrom(type.getRawClass())) {
                return "Set";
            }
            return "List";
        }
        if (beanDesc == null) {
            beanDesc = pMapper.getSerializationConfig().introspectClassAnnotations(type);
        }

        PropertyName rootName = pIntr.findRootName(beanDesc.getClassInfo());
        if (rootName != null && rootName.hasSimpleName()) {
            return rootName.getSimpleName();
        }
        return pTypeNameResolver.nameForType(type);
    }

    protected String pTypeQName(JavaType type) {
        return type.getRawClass().getName();
    }

    protected String pSubTypeName(NamedType type) {
        // !!! TODO: should this use 'name' instead?
        return type.getType().getName();
    }

    protected String pFindDefaultValue(Annotated a) {
        XmlElement elem = a.getAnnotation(XmlElement.class);
        if (elem != null) {
            if (!elem.defaultValue().isEmpty() && !"\u0000".equals(elem.defaultValue())) {
                return elem.defaultValue();
            }
        }
        return null;
    }

    protected String pFindExampleValue(Annotated a) {
        ApiModelProperty prop = a.getAnnotation(ApiModelProperty.class);
        if (prop != null) {
            if (!prop.example().isEmpty()) {
                return prop.example();
            }
        }
        return null;
    }

    protected Boolean pFindReadOnly(Annotated a) {
        ApiModelProperty prop = a.getAnnotation(ApiModelProperty.class);
        if (prop != null) {
            return prop.readOnly();
        }
        return null;
    }

    protected boolean pIsSetType(Class<?> cls) {
        if (cls != null) {

            if (java.util.Set.class.equals(cls)) {
                return true;
            }
            else {
                for (Class<?> a : cls.getInterfaces()) {
                    // this is dirty and ugly and needs to be extended
                    // into a scala model converter. But to avoid bringing in
                    // scala runtime...
                    if (java.util.Set.class.equals(a) ||
                        "interface scala.collection.Set".equals(a.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Model resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (chain.hasNext()) {
            return chain.next().resolve(type, context, chain);
        }
        else {
            return null;
        }
    }
}

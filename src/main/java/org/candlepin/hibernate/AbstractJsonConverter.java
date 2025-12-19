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
package org.candlepin.hibernate;

import org.candlepin.util.ObjectMapperFactory;

import tools.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;


/**
 * The AbstractJsonConverter implements the standard functionality for converting entities into
 * JSON strings using the JPA convert interface.
 *
 * To use this class, it must first be subclassed and given a default constructor which passes
 * the target attribute class to the parent class's constructor:
 *
 * <pre>
 * {@code
 * public class MyTypeJsonConverter extends AbstractJsonConverter<MyType> {
 *   public MyTypeJsonConverter() {
 *     super(MyType.class);
 *   }
 * }
 * }
 * </pre>
 *
 * With the converter class created, the <tt>@Convert</tt> annotation needs to be added where
 * appropriate. The simplest approach is to apply it to the attribute field within the entity:
 *
 * {@code
 * public class MyEntity {
 *   ...
 *
 *   @Column(name = "column_name")
 *   @Convert(converter = MyTypeJsonConverter.class)
 *   private MyType myType;
 *
 *   ...
 * }
 * }
 *
 * See the documentation on the <tt>@Convert</tt> annotation for other ways to apply the it.
 *
 * @param <T>
 *  The type to be handled by this JSON converter
 */
public abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {

    /** A shared ObjectMapper to be used by all JSON-serialized types */
    private static ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();

    private Class<T> type;

    /**
     * Creates a new JsonConverter that converts attributes of the specified type to JSON strings.
     *
     * @param type
     *  the type to convert
     *
     * @throws IllegalArgumentException
     *  if type is null
     */
    public AbstractJsonConverter(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }

        this.type = type;
    }

    /**
     * Fetches the ObjectMapper instance to use for serializing and deserializing data for this
     * type.
     *
     * @return
     *  the ObjectMapper instance to use for serialization and deserialization
     */
    protected ObjectMapper getObjectMapper() {
        return mapper;
    }

    /**
     * Fetches the entity type this JSON converter handles.
     *
     * @return
     *  the entity type handled by this JSON converter
     */
    protected Class<T> getEntityType() {
        return this.type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String convertToDatabaseColumn(T entity) {
        try {
            ObjectMapper mapper = this.getObjectMapper();
            return entity != null ? mapper.writeValueAsString(entity) : null;
        }
        catch (Exception e) {
            throw new TypeConversionException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T convertToEntityAttribute(String data) {
        try {
            ObjectMapper mapper = this.getObjectMapper();
            return data != null ? mapper.readValue(data, this.getEntityType()) : null;
        }
        catch (Exception e) {
            throw new TypeConversionException(e);
        }
    }

}

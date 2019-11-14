/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import com.fasterxml.jackson.core.JsonFactory;

import javax.persistence.AttributeConverter;



/**
 * The AbstractJsonConverter implements the standard functionality for converting entities into
 * JSON strings using the JPA convert interface.
 *
 * To use this class, it must first be subclassed and given a default constructor which passes
 * the target attribute class to the parent class's constructor. The subclass must also implement
 * the serialize and deserialize methods:
 *
 * <pre>
 * public class MyTypeJsonConverter extends AbstractJsonConverter<MyType> {
 *   public MyTypeJsonConverter() {
 *     super(MyType.class);
 *   }
 *
 *   protected String serialize(JsonFactory factory, T entity) {
 *     ...
 *   }
 *
 *   protected T deserialize(JsonFactory factory, String json) {
 *     ...
 *   }
 * }
 * </pre>
 *
 * With the converter class created, the <tt>@Convert</tt> annotation needs to be added where
 * appropriate. The simplest approach is to apply it to the attribute field within the entity:
 *
 * <pre>
 * public class MyEntity {
 *   ...
 *
 *   @Column(name = "column_name")
 *   @Convert(converter = MyTypeJsonConverter.class)
 *   private MyType myType;
 *
 *   ...
 * }
 * </pre>
 *
 * See the documentation on the <tt>@Convert</tt> annotation for other ways to apply the it.
 *
 * @param <T>
 *  The type to be handled by this JSON converter
 */
public abstract class AbstractJsonConverter<T> implements AttributeConverter<T, String> {

    private Class<T> type;
    private JsonFactory jsonFactory;

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
        this.jsonFactory = new JsonFactory();
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
     * Performs the actual serialization of the specified entity. The JsonFactory provided can be
     * used to create a JsonGenerator or ObjectMapper as necessary for the entity being serialized.
     *
     * @param factory
     *  A JsonFactory instance that can be used to create a JsonGenerator or ObjectMapper for entity
     *  serialization
     *
     * @param entity
     *  The entity to serialize
     *
     * @return
     *  JSON representing the serialized entity
     */
    protected abstract String serialize(JsonFactory factory, T entity);

    /**
     * Deserializes the provided JSON into an entity.
     *
     *
     */
    protected abstract T deserialize(JsonFactory factory, String json);

    /**
     * {@inheritDoc}
     */
    @Override
    public String convertToDatabaseColumn(T entity) {
        try {
            return this.serialize(this.jsonFactory, entity);
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
            return this.deserialize(this.jsonFactory, data);
        }
        catch (Exception e) {
            throw new TypeConversionException(e);
        }
    }

}

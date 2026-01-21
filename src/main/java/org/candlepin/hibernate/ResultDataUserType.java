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

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Properties;


/**
 * ResultDataUserType handles writing objects that are job results to the resultData column in cp_job.
 * Initially Candlepin stored serialized Java objects into this column, but later revisions stored JSON
 * instead.  This class takes care of reading in the data irrespective of the storage format.
 *
 * It also takes care of casting the data to the correct Java type.  Hibernate provides us with the type of
 * the annotated field via elements of the DynamicParameterizedType class which we can then use to cast the
 * object we've read back.  The JobStatus class that uses this UserType ultimately stores the result data
 * as an Object but at least readers of the data can downcast correctly later if they know what type the
 * job is storing.
 */
public class ResultDataUserType implements UserType, DynamicParameterizedType {
    private static final Logger log = LoggerFactory.getLogger(ResultDataUserType.class);
    public static final String JSON_CLASS = "jsonClass";

    private static ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();

    private Class<?> jsonClass;

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.VARBINARY };
    }

    @Override
    public Class returnedClass() {
        return jsonClass;
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        return Objects.hashCode(x);
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names,
        SharedSessionContractImplementor session, Object owner)
        throws HibernateException, SQLException {

        byte[] data = StandardBasicTypes.BINARY.nullSafeGet(rs, names[0], session);
        return deserialize(data);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,
        SharedSessionContractImplementor session)
        throws HibernateException, SQLException {

        StandardBasicTypes.BINARY.nullSafeSet(st, serializeJson(value), index, session);
    }

    private Object deserialize(byte[] data) {
        if (data == null) {
            return null;
        }

        Object result;
        try {
            result = deserializeJson(data);
        }
        catch (StreamReadException e) {
            /* If we can't deserialize the result data, try to deserialize it as a Java object since that
             * is the legacy format.
             */
            log.debug("Could not read result data as JSON. Trying as Java object.", e);
            result = deserializeJava(data);
        }
        catch (Exception e) {
            // This catch will also catch IOException which readValue also throws but for low level IO errors
            // that we likely wouldn't want to send on to deserializeJava
            log.warn("Could not read result data", e);
            throw new RuntimeException(e);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeJson(byte[] data) throws IOException {
        try (JsonParser parser = mapper.tokenStreamFactory().createParser(data)) {
            try {
                return (T) mapper.readValue(parser, jsonClass);
            }
            catch (DatabindException e) {
                log.warn("Could not deserialize into {}. Trying Object.", jsonClass.getName(), e);
                return (T) mapper.readValue(parser, Object.class);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeJava(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] serializeJson(Object value) {
        byte[] data;

        if (value == null) {
            data = null;
        }
        else {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                JsonGenerator generator = mapper.tokenStreamFactory()
                    .createGenerator(baos, JsonEncoding.UTF8)) {

                mapper.writeValue(generator, value);
                data = baos.toByteArray();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return data;
    }

    private byte[] serializeJava(Object value) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(value);
            return baos.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not serialize Java object", e);
        }
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        // We serialize and then deserialize the object.  This is slow but we know nothing about the object
        // type.  Hibernate actually does something similar for mutable objects in its internal types.
        return deserializeJava(serializeJava(value));
    }

    @Override
    public boolean isMutable() {
        // We don't strictly know if the type is mutable or not, but it's prudent to assume it is
        return true;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        return (byte[]) deepCopy(value);
    }

    @Override
    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return deepCopy(cached);
    }

    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        return deepCopy(original);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setParameterValues(Properties parameters) {
        ParameterType reader = (ParameterType) parameters.get(PARAMETER_TYPE);

        if (reader != null) {
            jsonClass = reader.getReturnedClass();
        }
        else {
            // The else is encountered if the entity is configured via XML
            String jsonClassName = (String) parameters.get(JSON_CLASS);
            try {
                jsonClass = classForName(jsonClassName, this.getClass());
            }
            catch (ClassNotFoundException e) {
                throw new HibernateException("Class not found: " + jsonClass, e);
            }
        }
    }

    /**
     * Load a Class based on a fully-qualified name.  Borrowed from an internal Hibernate utility class.
     *
     * @param name the name of the class to find
     * @param caller the class of the caller
     * @return a Class object corresponding to the name given
     * @throws ClassNotFoundException if the class is not found
     */
    private Class<?> classForName(String name, Class caller) throws ClassNotFoundException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return classLoader.loadClass(name);
            }
        }
        catch (Throwable ignore) {
            // Intentionally left empty
        }

        return Class.forName(name, true, caller.getClassLoader());
    }
}

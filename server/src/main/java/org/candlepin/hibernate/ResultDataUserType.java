/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.usertype.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * ResultDataUserType handles writing objects that are job results to the resultData column in cp_job.
 * Initially Candlepin stored serialized Java objects into this column, but later revisions stored JSON
 * instead.  This class takes care of reading in the data irrespective of the storage format.
 */
public class ResultDataUserType implements UserType {
    private static final Logger log = LoggerFactory.getLogger(ResultDataUserType.class);

    @Override
    public int[] sqlTypes() {
        return new int[] { Types.VARBINARY };
    }

    @Override
    public Class returnedClass() {
        return Object.class;
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
    public Object nullSafeGet(ResultSet rs, String[] names, SessionImplementor session, Object owner)
        throws HibernateException, SQLException {
        byte[] data = StandardBasicTypes.BINARY.nullSafeGet(rs, names[0], session);
        return deserialize(data);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SessionImplementor session)
        throws HibernateException, SQLException {
        StandardBasicTypes.BINARY.nullSafeSet(st, serialize(value), index, session);
    }

    private byte[] serialize(Object value) {
        byte[] data;
        if (value == null) {
            data = null;
        }
        else {
            try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
            ) {
                oos.writeObject(value);
                data = baos.toByteArray();
            }
            catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return data;
    }

    private Object deserialize(byte[] data) {
        if (data == null) {
            return null;
        }
        else {
            Object result;

            try (
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bais);
            ) {
                result = ois.readObject();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            return result;
        }
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        // We serialize and then deserialize the object.  This is slow but we know nothing about the object
        // type.  Hibernate actually does something similar for mutable objects in its internal types.
        return deserialize(serialize(value));
    }

    @Override
    public boolean isMutable() {
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
}

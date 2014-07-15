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
package org.candlepin.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.usertype.UserType;

/**
 * This type is meant to convert a null into an empty string on reads.  Mainly
 * it is for use with the serviceLevel field on Consumer which for legacy
 * reasons uses the empty string to represent no service level.  Since Oracle
 * stores empty strings as nulls, we need to convert the null back to an empty
 * string when we load the service level from the database.
 */
public class EmptyStringUserType implements UserType {
    @Override
    public int[] sqlTypes() {
        return new int[] { Types.VARCHAR };
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    /**
     * Changes an empty string to be equal to a null.
     *
     * @return whether or not x == y
     * @throws HibernateException if something goes horribly wrong.
     */
    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        if (x == y) {
            return true;
        }

        if (x == null) {
            return ((String) y).length() == 0;
        }
        else if (y == null) {
            return ((String) x).length() == 0;
        }

        return x.equals(y);
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SessionImplementor si,
        Object owner)
        throws HibernateException, SQLException {
        String value = (String) StandardBasicTypes.STRING.nullSafeGet(rs, names[0], si);
        return (value == null) ? "" : value;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,
        SessionImplementor si)
        throws HibernateException, SQLException {
        StandardBasicTypes.STRING.nullSafeSet(st, value, index, si);
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, Object owner)
        throws HibernateException {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, Object owner)
        throws HibernateException {
        return original;
    }
}

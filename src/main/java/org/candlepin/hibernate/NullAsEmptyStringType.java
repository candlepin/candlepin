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

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;



/**
 * A user type which stores null values as empty strings in the database, and converts empty strings
 * back to nulls in code. This is used primarily to avoid needing additional complexity at the
 * database level to properly implement a unique constraint with empty and null values; but may have
 * other uses.
 */
public class NullAsEmptyStringType implements UserType {

    /**
     * {@inheritDoc}
     */
    @Override
    public String assemble(Serializable cached, Object owner) {
        if (cached != null && !(cached instanceof String)) {
            throw new IllegalStateException("cached value is not a string: " + cached);
        }

        return (String) cached;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object deepCopy(Object value) {
        // From the Hibernate docs: "It is not necessary to copy immutable objects, or null values,
        // in which case it is safe to simply return the argument."

        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable disassemble(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalStateException("value is not a string: " + value);
        }

        return (Serializable) value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        if (x == y) {
            return true;
        }

        // We can't compare non-Strings
        if ((x != null && !(x instanceof String)) || (y != null && !(y instanceof String))) {
            return false;
        }

        String xstr = x != null ? (String) x : "";
        String ystr = y != null ? (String) y : "";

        return xstr.equals(ystr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode(Object x) throws HibernateException {
        return x != null ? x.hashCode() : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMutable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object nullSafeGet(ResultSet resultSet, String[] names,
        SharedSessionContractImplementor sscImplementor, Object owner) throws SQLException {

        // Convert empty strings back to nulls
        String value = StandardBasicTypes.STRING.nullSafeGet(resultSet, names[0], sscImplementor);
        return value != null && !value.isEmpty() ? value : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nullSafeSet(PreparedStatement statement, Object value, int index,
        SharedSessionContractImplementor sscImplementor)
        throws HibernateException, SQLException {

        // Convert null values to empty strings; ignore everything else
        StandardBasicTypes.STRING.nullSafeSet(statement, (value != null ? value : ""), index, sscImplementor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        // From the Hibernate docs: "For immutable objects, or null values, it is safe to simply
        // return the first parameter."

        return original;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] sqlTypes() {
        return new int[] { Types.VARCHAR };
    }

}

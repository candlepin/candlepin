/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
public class NullAsEmptyStringType implements UserType<String> {

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
    public String deepCopy(String value) {
        // From the Hibernate docs: "It is not necessary to copy immutable objects, or null values,
        // in which case it is safe to simply return the argument."

        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(String x, String y) throws HibernateException {
        if (x == y) {
            return true;
        }

        String xstr = x != null ? x : "";
        String ystr = y != null ? y : "";

        return xstr.equals(ystr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode(String x) throws HibernateException {
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
    public String nullSafeGet(ResultSet resultSet, int position,
        SharedSessionContractImplementor session, Object owner) throws SQLException {

        String value = resultSet.getString(position);

        // Convert empty strings back to nulls
        return value != null && !value.isEmpty() ? value : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nullSafeSet(PreparedStatement statement, String value, int index,
        SharedSessionContractImplementor session) throws SQLException {

        statement.setString(index, value != null ? value : "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String replace(String original, String target, Object owner) throws HibernateException {
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
    public int getSqlType() {
        return Types.VARCHAR;
    }

}

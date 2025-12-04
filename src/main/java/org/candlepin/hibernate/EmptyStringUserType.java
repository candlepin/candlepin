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

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * This type is meant to convert a null into an empty string on reads.  Mainly
 * it is for use with the serviceLevel field on Consumer which for legacy
 * reasons uses the empty string to represent no service level.  Since Oracle
 * stores empty strings as nulls, we need to convert the null back to an empty
 * string when we load the service level from the database.
 *
 * For a time we were attempting to future-proof by remaining compatible with (if
 * not fully supporting) Oracle.  As of August 2018, the mandate to remain Oracle compatible
 * is gone, but I am electing to keep this class so that our data storage strategy remains
 * consistent across Candlepin versions.  I don't want the situation where Candlepin X does store
 * string but Candlepin X+1 does not.
 */
public class EmptyStringUserType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.VARCHAR;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    /**
     * Changes an empty string to be equal to a null.
     *
     * @return whether or not x == y
     */
    @Override
    public boolean equals(String x, String y) {
        if (x == y) {
            return true;
        }

        if (x == null) {
            return y != null && y.length() == 0;
        }
        else if (y == null) {
            return x.length() == 0;
        }

        return x.equals(y);
    }

    @Override
    public int hashCode(String x) {
        return x != null ? x.hashCode() : 0;
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session,
        Object owner) throws SQLException {
        String value = rs.getString(position);
        return (value == null) ? "" : value;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index,
        SharedSessionContractImplementor session) throws SQLException {
        st.setString(index, value);
    }

    @Override
    public String deepCopy(String value) {
        // Strings are immutable
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }

    @Override
    public String replace(String detached, String managed, Object owner) {
        // Strings are immutable
        return detached;
    }
}

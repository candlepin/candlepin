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
 * A user type which stores null values as empty strings in the database, and converts empty strings
 * back to nulls in code. This is used primarily to avoid needing additional complexity at the
 * database level to properly implement a unique constraint with empty and null values; but may have
 * other uses.
 */
public class NullAsEmptyStringType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.VARCHAR;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        if (x == y) {
            return true;
        }

        String xstr = x != null ? x : "";
        String ystr = y != null ? y : "";

        return xstr.equals(ystr);
    }

    @Override
    public int hashCode(String x) {
        return x != null ? x.hashCode() : 0;
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session,
        Object owner) throws SQLException {
        // Convert empty strings back to nulls
        String value = rs.getString(position);
        return (value != null && !value.isEmpty()) ? value : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index,
        SharedSessionContractImplementor session) throws SQLException {
        // Convert null values to empty strings
        st.setString(index, value != null ? value : "");
    }

    @Override
    public String deepCopy(String value) {
        // Strings are immutable, so just return the value
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

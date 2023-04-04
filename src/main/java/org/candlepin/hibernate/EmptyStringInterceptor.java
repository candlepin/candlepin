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

import org.hibernate.EmptyInterceptor;
import org.hibernate.type.StringType;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * This interceptor changes empty strings to null before they are written to the
 * database.  This change is necessary because Oracle treats the empty string
 * as null and we want to mimic the same behavior on PostgreSQL.
 *
 * For a time we were attempting to future-proof by remaining compatible with (if
 * not fully supporting) Oracle.  As of August 2018, the mandate to remain Oracle compatible
 * is gone, but I am electing to keep this interceptor so that our data storage strategy remains
 * consistent across Candlepin versions.  I don't want the situation where Candlepin X does store
 * string but Candlepin X+1 does not.
 */
public class EmptyStringInterceptor extends EmptyInterceptor {
    private static Logger log = LoggerFactory.getLogger(EmptyStringInterceptor.class);

    @Override
    public boolean onFlushDirty(Object entity, Serializable id,
        Object[] currentState, Object[] previousState, String[] propertyNames,
        Type[] types) {

        return convertEmptyStringToNull(currentState, propertyNames, types);
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state,
        String[] propertyNames, Type[] types) {

        return convertEmptyStringToNull(state, propertyNames, types);
    }

    private boolean convertEmptyStringToNull(Object[] state, String[] propertyNames, Type[] types) {
        boolean modified = false;
        for (int i = 0; i < types.length; i++) {
            if (types[i] instanceof StringType && "".equals(state[i])) {
                log.debug("Attempting to write an empty string to the database for field \"{}\"; " +
                    "Substituting null instead", propertyNames[i]);

                state[i] = null;
                modified = true;
            }
        }

        return modified;
    }
}

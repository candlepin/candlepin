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

    private boolean convertEmptyStringToNull(Object[] state,
        String[] propertyNames, Type[] types) {
        boolean modified = false;
        for (int i = 0; i < types.length; i++) {
            if (types[i] instanceof StringType && "".equals(state[i])) {
                log.debug("Attempting to write an empty string to the database" +
                    " for " + propertyNames[i] + ".  Substituting null instead.");
                state[i] = null;
                modified = true;
            }
        }

        return modified;
    }
}

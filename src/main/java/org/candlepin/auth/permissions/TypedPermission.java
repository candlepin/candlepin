/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;

import java.io.Serializable;

/**
 * A specific {@link Permission} that deals with a single target class.
 *
 * @param <T> The type of target that this permission addresses.
 */
public abstract class TypedPermission<T> implements Permission, Serializable {

    public abstract Class<T> getTargetType();

    public abstract boolean canAccessTarget(T target, Access action);

    @Override
    public boolean canAccess(Object target, Access access) {
        if (this.getTargetType().isInstance(target)) {
            return canAccessTarget((T) target, access);
        }

        return false;
    }


}

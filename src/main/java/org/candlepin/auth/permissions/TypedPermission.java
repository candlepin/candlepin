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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;

import java.io.Serializable;

/**
 * A specific {@link Permission} that deals with a single target class.
 *
 * @param <T> The type of target that this permission addresses.
 */
public abstract class TypedPermission<T> implements Permission, Serializable {

    protected Access access;

    public abstract Class<T> getTargetType();

    public abstract boolean canAccessTarget(T target, SubResource subResource,
        Access action);

    @Override
    public boolean canAccess(Object target, SubResource subResource, Access required) {
        if (this.getTargetType().isInstance(target)) {
            return canAccessTarget((T) target, subResource, required);
        }

        return false;
    }

}

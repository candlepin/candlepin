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
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * A permission granting an authenticated user permission to view itself.
 */
public class UserUserPermission extends TypedPermission<User> {

    private final String username;

    public UserUserPermission(String username) {
        this.username = username;
    }

    @Override
    public Class<User> getTargetType() {
        return User.class;
    }

    @Override
    public boolean canAccessTarget(User target, SubResource subResource,
        Access required) {
        return target.getUsername().equals(username);
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(User.class)) {
            return Restrictions.eq("username", username);
        }
        return null;
    }

    @Override
    public Owner getOwner() {
        // This permission is not specific to any owner.
        return null;
    }

    public String getUsername() {
        return username;
    }

}

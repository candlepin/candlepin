/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.service.model;

import java.util.Collection;
import java.util.Date;



/**
 * The UserInfo represents a minimal set of owner/organization information used by the service
 * adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface UserInfo extends ServiceAdapterModel {

    // /**
    //  * Fetches the ID of this user. If the ID has not yet been set, this method returns null.
    //  *
    //  * @return
    //  *  The ID of this user, or null if the ID has not been set
    //  */
    // String getId();

    /**
     * Fetches the date this user was created. If the creation date has not been set, this method
     * returns null.
     *
     * @return
     *  the creation date for this user, or null if the creation date has not been set
     */
    Date getCreated();

    /**
     * Fetches the date this user was last updated. If the update date has not been set, this method
     * returns null.
     *
     * @return
     *  the last update date for this user, or null if the last update date has not been set
     */
    Date getUpdated();

    /**
     * Fetches the username of this user. If the username has not yet been set, this method returns
     * null.
     *
     * @return
     *  The username of this user, or null if the username has not been set
     */
    String getUsername();

    /**
     * Fetches the hashed password for this user. If the password has not yet been set, this method
     * returns null.
     *
     * @return
     *  The hashed password for this user, or null if the password has not been set
     */
    String getHashedPassword();

    /**
     * Checks if this user is a super admin. If the super admin flag has not yet been set, this
     * method returns null. If the user is not a super admin, this method returns false.
     *
     * @return
     *  The super admin flag for this user, or null if the super admin flag has not been set
     */
    Boolean isSuperAdmin();

    /**
     * Fetches a collection of roles for this user. If the roles have not yet been set, this method
     * returns null. If this user does not have any roles, this method returns an empty collection.
     *
     * @return
     *  A collection of roles for this user, or null if the roles have not been set
     */
    Collection<? extends RoleInfo> getRoles();

    // /**
    //  * Fetches a collection of permissions for this user, including permissions provided by roles
    //  * assigned to this user. If the permissions have not yet been set, and this user does not have
    //  * any roles, this method returns null. If the user does not have any permissions, nor any
    //  * role-provided permissions, this method returns an empty collection.
    //  *
    //  * @deprecated
    //  *  UserInfo returned by the service adapters should use roles instead of raw permissions and
    //  *  let Candlepin build its permission objects internally. This method may be removed or
    //  *  refactored in the future.
    //  *
    //  * @return
    //  *  A collection of permissions for this user, or null if the permissions and roles have not
    //  *  been set
    //  */
    // @Deprecated
    // Collection<Permission> getPermissions();

}

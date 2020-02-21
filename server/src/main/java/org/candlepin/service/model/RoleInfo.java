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
 * The RoleInfo represents a minimal set of role information used by the service adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface RoleInfo extends ServiceAdapterModel {

    // /**
    //  * Fetches the ID of this role. If the ID has not yet been set, this method returns null.
    //  *
    //  * @return
    //  *  The ID of this role, or null if the ID has not been set
    //  */
    // String getId();

    /**
     * Fetches the date this role was created. If the creation date has not been set, this method
     * returns null.
     *
     * @return
     *  the creation date for this role, or null if the creation date has not been set
     */
    Date getCreated();

    /**
     * Fetches the date this role was last updated. If the update date has not been set, this method
     * returns null.
     *
     * @return
     *  the last update date for this role, or null if the last update date has not been set
     */
    Date getUpdated();

    /**
     * Fetches the name of this role. If the name has not yet been set, this method returns null.
     *
     * @return
     *  The name of this role, or null if the name has not been set
     */
    String getName();

    /**
     * Fetches the users currently assigned to this role. If the users have not yet been set, this
     * method returns null. If this role is not currently assigned to any users, this method returns
     * an empty collection.
     *
     * @return
     *  The users assigned to this role, or null if the users have not been set
     */
    Collection<? extends UserInfo> getUsers();

    /**
     * Fetches the permission blueprints currently provided by this role. If the blueprints have not
     * yet been set, this method returns null. If this role currently does not provide any
     * permission blueprints, this method returns an empty collection.
     *
     * @return
     *  The permission blueprints provided by this role, or null if the blueprints have not been set
     */
    Collection<? extends PermissionBlueprintInfo> getPermissions();

}

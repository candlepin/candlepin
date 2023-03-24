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



/**
 * The PermissionBlueprintInfo represents a minimal set of permission blueprint information used by
 * the service adapters and UAC infrastructure to build proper permissions on demand.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface PermissionBlueprintInfo extends ServiceAdapterModel {

    // /**
    //  * Fetches the ID of this role. If the ID has not yet been set, this method returns null.
    //  *
    //  * @return
    //  *  The ID of this role, or null if the ID has not been set
    //  */
    // String getId();

    /**
     * Fetches the owner for which this permission applies. If the owner has not yet been set, this
     * method returns null.
     *
     * @return
     *  The owner for this permission, or null if the owner has not been set
     */
    OwnerInfo getOwner();

    /**
     * Fetches the permission's type as a string. If the type has not yet been defined, this method
     * returns null.
     *
     * @return
     *  The permission's type, or null if the type has not been set
     */
    String getTypeName();

    /**
     * Fetches the access provided by this permission. If the access has not yet been set, this
     * method returns null.
     *
     * @return
     *  The access provided by this permission, or null if the access has not been set
     */
    String getAccessLevel();

}

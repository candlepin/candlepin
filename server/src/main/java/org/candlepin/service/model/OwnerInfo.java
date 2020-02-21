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

import java.util.Date;



/**
 * The OwnerInfo represents a minimal set of owner/organization information used by the service
 * adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface OwnerInfo extends ServiceAdapterModel {

    /**
     * Fetches the key of this owner. If the key has not been set, this method returns null.
     *
     * @return
     *  The key of the owner, or null if the key has not been set
     */
    String getKey();

    /**
     * Fetches the date this owner was created. If the creation date has not been set, this method
     * returns null.
     *
     * @return
     *  the creation date for this owner, or null if the creation date has not been set
     */
    Date getCreated();

    /**
     * Fetches the date this owner was last updated. If the update date has not been set, this
     * method returns null.
     *
     * @return
     *  the last update date for this owner, or null if the last update date has not been set
     */
    Date getUpdated();

}

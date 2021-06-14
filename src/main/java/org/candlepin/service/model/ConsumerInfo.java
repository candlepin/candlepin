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

import java.util.Map;



/**
 * The ConsumerInfo represents a minimal set of consumer information used by the service adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface ConsumerInfo extends ServiceAdapterModel {

    // /**
    //  * Fetches the ID of this consumer. If the ID has not yet been generated or set, this method
    //  * returns null.
    //  *
    //  * @return
    //  *  The ID of this consumer, or null if the ID has not been set
    //  */
    // public String getId();

    /**
     * Fetches the UUID of this consumer. If the UUID has not yet been set, this method returns
     * null.
     *
     * @return
     *  The UUID of this consumer, or null if the UUID has not been set
     */
    String getUuid();

    /**
     * Fetches the username of this consumer. If the username has not yet been set, this method
     * returns null.
     *
     * @return
     *  The username of this consumer, or null if the username has not been set
     */
    String getUsername();

    /**
     * Fetches the owner of this consumer. If the owner has not been set, this method returns
     * null.
     *
     * @return
     *  The owner of this consumer, or null if the owner has not been set
     */
    OwnerInfo getOwner();

    /**
     * Fetches this consumer's facts. If the facts have not been set, this method returns null. If
     * the consumer has no facts, this method returns an empty map.
     *
     * @return
     *  The facts for this consumer, or null if the facts have not been set
     */
    Map<String, String> getFacts();

}

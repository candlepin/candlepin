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
package org.candlepin.service;



/**
 * The OwnerServiceAdapter defines an interface for hooking into some owner/org-specific operations
 * for additional or external verification of values, or for providing data from alternate sources.
 */
public interface OwnerServiceAdapter {

    /**
     * Checks that the given key is a valid key to be used during owner creation. The owner key may
     * originate from a separate service outside of Candlepin in some configurations.
     *
     * @param ownerKey
     *  The potential key to be used for a new owner
     *
     * @return
     *  true if the key is valid for a new owner, false otherwise
     */
    boolean isOwnerKeyValidForCreation(String ownerKey);

    /**
     * Retrieves the current content access mode for the owner represented by the given key. If the
     * owner should not be using. The value returned by this method should always be present in the
     * list returned by getContentAccessModeList, or null to represent the owner isn't using any
     * special content access modes.
     *
     * @param ownerKey
     *  A key representing the owner for which to fetch the content access mode
     *
     * @throws IllegalArgumentException
     *  if ownerKey is null, empty or otherwise does not represent a valid owner
     *
     * @return
     *  A string representing the content access mode for the specified owner, or null if the owner
     *  should not be using any special content access mode
     */
    String getContentAccessMode(String ownerKey);

    /**
     * Retrieves a comma-delimited string representing the content access mode list for the owner
     * represented by the given key. This method may return a null or empty string, indicating the
     * owner does not have any special content access modes.
     *
     * @param ownerKey
     *  A key representing the owner for which to fetch the content access mode
     *
     * @throws IllegalArgumentException
     *  if ownerKey is null, empty or otherwise does not represent a valid owner
     *
     * @return
     *  A comma-delimited string representing the content access mode list for the specified owner,
     *  or null if the owner does not have any special content access modes
     */
    String getContentAccessModeList(String ownerKey);

}

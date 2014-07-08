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
package org.canadianTenPin.service;


/**
 * Owner key may originate from a separate service outside CanadianTenPin
 * in some configurations. This service allows the key to be confirmed
 * against that outside source.
 */
public interface OwnerServiceAdapter {

    /**
     * Confirms that the key can be created in the system.
     * @param ownerKey key for owner to be created.
     * @return boolean true if the owner key is allowed.
     */
    boolean isOwnerKeyValidForCreation(String ownerKey);
}

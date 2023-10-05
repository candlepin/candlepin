/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

}

/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin.spec.bootstrap.data;

import org.candlepin.dto.api.v1.OwnerDTO;

/**
 * A collection of factory methods focused on the owner creation
 */
public final class Owners {

    /**
     * Creates a simple {@link OwnerDTO} populated by randomized data.
     *
     * @return an owner
     */
    public static OwnerDTO simple() {
        return new OwnerBuilder().build();
    }

    /**
     * Creates an {@link OwnerDTO} enabled for SCA and populated by randomized data.
     *
     * @return an owner
     */
    public static OwnerDTO scaEnabled() {
        return new OwnerBuilder()
            .withContentAccess("org_environment")
            .withContentAccessList("org_environment,entitlement")
            .build();
    }

    /**
     * Returns an instance of owner builder. It helps with cases where factory
     * methods are not sufficient and the test requires more control while
     * helping to maintain the constraint to randomize data to avoid conflicts.
     *
     * @return an owner builder
     */
    public static OwnerBuilder builder() {
        return new OwnerBuilder();
    }

}

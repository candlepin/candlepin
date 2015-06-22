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

package org.candlepin.model;

/**
 * A filter builder that allows filtering Entitlements based on it's pools product
 * attributes. When using this builder, a criteria is expected to have the Pool
 * class aliased as 'p'.
 */
public class EntitlementFilterBuilder extends PoolFilterBuilder {

    public EntitlementFilterBuilder() {
        // Requires that the parent criteria has an alias set on the Pool class.
        super("p");
    }

}

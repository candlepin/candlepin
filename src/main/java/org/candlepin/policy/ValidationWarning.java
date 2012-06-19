/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.policy;

/**
 * Represents a validation warning
 */
public class ValidationWarning {
    private String resourceKey;

    /**
     * ctor
     * @param resourceKey resource key
     */
    public ValidationWarning(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    /**
     * Return the resource key
     * @return the resource key
     */
    public String getResourceKey() {
        return resourceKey;
    }

    /**
     * String representation.
     * @return String representation.
     */
    public String toString() {
        return resourceKey;
    }
}

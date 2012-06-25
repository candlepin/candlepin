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
package org.candlepin.client.model;

import org.apache.commons.lang.math.NumberUtils;

/**
 * Role
 */
public class Role extends Entitlement {

    private Extensions extensions;
    private String hash;

    public Role(Extensions extensions, String hash) {
        this.extensions = extensions;
        this.hash = hash;
    }

    public Role() {

    }

    public String getName() {
        return extensions.getValue("1");
    }

    public String getLabel() {
        return extensions.getValue("2");
    }

    public int getQuantity() {
        return NumberUtils.toInt(extensions.getValue("3"), -1);
    }

    public String getHash() {
        return this.hash;
    }
}

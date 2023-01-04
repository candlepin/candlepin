/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.assertions;

import java.util.Map;

public enum ReasonAttributes {
    Covered("covered"),
    EntitlementId("entitlement_id"),
    Has("has"),
    Name("name"),
    ProductId("product_id"),
    StackId("stack_id");

    private final String key;

    ReasonAttributes(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public Map.Entry<String, String> withValue(String value) {
        return Map.entry(this.key, value);
    }
}

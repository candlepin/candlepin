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
package org.candlepin.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An enumeration representing the various types of system purpose attributes.
 */
public enum SystemPurposeAttributeType {
    ROLES(Product.Attributes.ROLES),
    ADDONS(Product.Attributes.ADDONS),
    SERVICE_LEVEL(Product.Attributes.SUPPORT_LEVEL),
    USAGE(Product.Attributes.USAGE),
    SERVICE_TYPE(Product.Attributes.SUPPORT_TYPE);

    private String name;
    private static final Map<String, SystemPurposeAttributeType> ENUM_MAP;

    // See Bloch's Effective Java item 30 or https://stackoverflow.com/a/37841094/6124862
    static {
        Map<String, SystemPurposeAttributeType> map = new HashMap<>();
        for (SystemPurposeAttributeType type : values()) {
            map.put(type.toString(), type);
        }
        // Create an immutable version of the map we just made
        ENUM_MAP = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    SystemPurposeAttributeType(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }

    public static SystemPurposeAttributeType fromString(String s) {
        return ENUM_MAP.get(s);
    }
}

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

package org.candlepin.auth;


import java.util.HashMap;
import java.util.Map;

public enum AuthenticationMethod {
    BASIC("User"),
    ACTIVATION_KEY("ActivationKey"),
    ANONYMOUS_CLOUD("AnonCloudAutoReg"),
    CLOUD("CloudAutoReg"),
    CONSUMER("Consumer"),
    KEYCLOAK("KeyCloak"),
    NO_AUTH("NoAuth"),
    SYSTEM("System"),
    TRUSTED_USER("TrustedUser");

    private String description;
    AuthenticationMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    private static final Map<String, AuthenticationMethod> LOOKUP = new HashMap<>();

    static {
        for (AuthenticationMethod am : AuthenticationMethod.values()) {
            LOOKUP.put(am.getDescription(), am);
        }
    }

    public static AuthenticationMethod get(String description) {
        if (description == null) {
            return null;
        }
        return LOOKUP.get(description);
    }
}

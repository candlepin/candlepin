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

/**
 * Represents the different types of cloud authentication tokens
 */
public enum CloudAuthTokenType {

    /**
     * The cloud authentication token type for an owner
     */
    STANDARD("CP-Cloud-Registration"),

    /**
     * The cloud authentication token type for an anonymous cloud consumer
     */
    ANONYMOUS("CP-Anonymous-Cloud-Registration");

    private String type;

    CloudAuthTokenType(String type) {
        this.type = type;
    }

    public boolean equalsType(String otherType) {
        return this.type.equals(otherType);
    }

    @Override
    public String toString() {
        return type;
    }

}

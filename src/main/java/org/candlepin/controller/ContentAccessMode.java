/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.controller;

/**
 * The ContentAccessMode enum specifies the supported content access modes, as well as some
 * utility methods for resolving, matching, and converting to database-compatible values.
 */
public enum ContentAccessMode {
    /** traditional entitlement mode requiring clients to consume pools to access content */
    ENTITLEMENT,

    /** simple content access (SCA) mode; clients have access to all org content by default */
    ORG_ENVIRONMENT;

    /**
     * Returns the a value to represent this ContentAccessMode instance in a database, which
     * can be resolved back to a valid ContentAccessMode.
     *
     * @return
     *  a database value to represent this ContentAccessMode instance
     */
    public String toDatabaseValue() {
        return this.name().toLowerCase();
    }

    /**
     * Checks if the provided content access mode name matches the name of this
     * ContentAccessMode instance.
     *
     * @param name
     *  the content access mode name to check
     *
     * @return
     *  true if the content access mode name matches the name of this ContentAccessMode
     *  instance; false otherwise
     */
    public boolean matches(String name) {
        return this.toDatabaseValue().equals(name);
    }

    /**
     * Fetches the default content access mode
     *
     * @return
     *  the default ContentAccessMode instance
     */
    public static ContentAccessMode getDefault() {
        return ORG_ENVIRONMENT;
    }

    /**
     * Resolves the mode name to a ContentAccessMode enum, using the default mode for empty
     * values. If the content access mode name is null, this function returns null.
     *
     * @param name
     *  the name to resolve
     *
     * @throws IllegalArgumentException
     *  if the name cannot be resolved to a valid content access mode
     *
     * @return
     *  the ContentAccessMode value representing the given mode name, or null if the provided
     *  content access mode name is null
     */
    public static ContentAccessMode resolveModeName(String name) {
        return ContentAccessMode.resolveModeName(name, false);
    }

    /**
     * Resolves the mode name to a ContentAccessMode enum, using the default mode for empty
     * values. If resolveNull is set to true, null values will be converted into the default
     * content access mode as well, otherwise this function will return null.
     *
     * @param name
     *  the name to resolve
     *
     * @param resolveNull
     *  whether or not to treat null values as empty values for resolution
     *
     * @throws IllegalArgumentException
     *  if the name cannot be resolved to a valid content access mode
     *
     * @return
     *  the ContentAccessMode value representing the given mode name, or null if the provided
     *  content access mode name isn null and resolveNull is not set
     */
    public static ContentAccessMode resolveModeName(String name, boolean resolveNull) {
        if (name == null) {
            return resolveNull ? ContentAccessMode.getDefault() : null;
        }

        if (name.isEmpty()) {
            return ContentAccessMode.getDefault();
        }

        for (ContentAccessMode mode : ContentAccessMode.values()) {
            if (mode.matches(name)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Content access mode name cannot be resolved: " + name);
    }
}

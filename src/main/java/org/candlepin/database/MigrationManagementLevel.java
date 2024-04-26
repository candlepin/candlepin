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
package org.candlepin.database;

/**
 * Defines the option on how to handle database migrations.
 */
public enum MigrationManagementLevel {
    /**
     * Do not attempt to run the database migrations
     */
    NONE("NONE"),
    /**
     * Changesets that have not yet been applied should be reported
     */
    REPORT("REPORT"),
    /**
     * Do not proceed with startup if there are changesets that have not been applied
     */
    HALT("HALT"),
    /**
     * Apply the changesets that have not yet been applied
     */
    MANAGE("MANAGE");

    private String name;

    MigrationManagementLevel(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}

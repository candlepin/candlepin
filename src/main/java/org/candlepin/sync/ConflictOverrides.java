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
package org.candlepin.sync;

import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * ConflictOverrides: Manifest conflicts the caller requested we override and continue
 * importing.
 */
public class ConflictOverrides {

    private Set<Importer.Conflict> conflictsToForce;

    public ConflictOverrides() {
        this.conflictsToForce = new HashSet<>();
    }

    public ConflictOverrides(Importer.Conflict ... conflicts) {
        this();

        if (conflicts != null) {
            for (Importer.Conflict conflict : conflicts) {
                this.conflictsToForce.add(conflict);
            }
        }
    }

    public ConflictOverrides(Iterable<String> conflictStrings) {
        this();

        if (conflictStrings != null) {
            for (String conflict : conflictStrings) {
                conflictsToForce.add(Importer.Conflict.valueOf(conflict));
            }
        }
    }

    public ConflictOverrides(String... conflictStrings) {
        this(conflictStrings != null ? List.of(conflictStrings) : List.of());
    }

    public boolean isForced(Importer.Conflict conflict) {
        return this.conflictsToForce.contains(conflict);
    }

    public boolean isEmpty() {
        return this.conflictsToForce.isEmpty();
    }

    public String[] asStringArray() {
        String[] strings = new String[this.conflictsToForce.size()];
        int offset = -1;

        for (Importer.Conflict conflict : this.conflictsToForce) {
            strings[++offset] = conflict.name();
        }

        return strings;
    }
}

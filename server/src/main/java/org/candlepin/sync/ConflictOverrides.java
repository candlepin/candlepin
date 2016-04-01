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
package org.candlepin.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ConflictOverrides: Manifest conflicts the caller requested we override and continue
 * importing.
 */
public class ConflictOverrides {

    private Set<Importer.Conflict> conflictsToForce =
        new HashSet<Importer.Conflict>();

    public ConflictOverrides(String [] conflictStrings) {
        for (String c : conflictStrings) {
            conflictsToForce.add(Importer.Conflict.valueOf(c));
        }
    }

    public ConflictOverrides(Importer.Conflict ... conflicts) {
        for (Importer.Conflict c : conflicts) {
            conflictsToForce.add(c);
        }
    }

    public boolean isForced(Importer.Conflict c) {
        return conflictsToForce.contains(c);
    }

    public boolean isEmpty() {
        return conflictsToForce.size() == 0;
    }

    public String[] asStringArray() {
        List<String> all = new ArrayList<String>();
        for (Importer.Conflict conflict : conflictsToForce) {
            all.add(conflict.name());
        }
        return all.toArray(new String[all.size()]);
    }
}

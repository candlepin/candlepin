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
package org.candlepin.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Arch
 */
public class Arch {

    private static ArrayList<String> x86Labels = new ArrayList<String>() {
        {
            add("i386");
            add("i386");
            add("i586");
            add("i686");
        }
    };

    private static ArrayList<String> ppcLabels = new ArrayList<String>() {
        {
            add("ppc");
            add("ppc64");
        }
    };

    private Arch() {
    }

    /*
     * Returns a Set of the comma separated arch name Strings
     *
     * @return Set of arch names, or an empty set if value is
     *         empty string, or if the 'arch' attribute doesnt
     *         exist
     */
    public static Set<String> parseArches(String arches) {
        Set<String> archesSet = new HashSet<String>();
        if (arches == null || arches.trim().equals("")) {
            return archesSet;
        }
        for (String arch : arches.split(",")) {
            archesSet.add(arch.trim());
        }
        return archesSet;
    }

    /*
     * determine if arch1 is compatible with arch2
     *
     * @param arch1
     * @param arch2
     * @return true if arch1 is compatible with arch2, false
     * otherwise
     */
    public static boolean isCompatible(String arch1, String arch2) {
        boolean compatible = false;
        // FIXME: hardcode exact matches on label
        //        only atm

        // handle "ALL" arch, sigh
        if (arch1.equals("ALL")) {
            compatible = true;
        }
        // Exact arch match
        else if (arch2.equals(arch1)) {
            compatible = true;
        }
        // x86_64 can use content for i386 etc
        else if (arch2.equals("x86_64")) {
            if (x86Labels.contains(arch1)) {
                compatible = true;
            }
        }
        // i686 can run all x86 arches
        else if (arch2.equals("i686")) {
            if (x86Labels.contains(arch1)) {
                compatible = true;
            }
        }
        // ppc64 can run ppc. Mostly...
        else if (arch2.equals("ppc64")) {
            if (ppcLabels.contains(arch1)) {
                compatible = true;
            }
        }

        /* In theory, ia64 can run x86 and x86_64 content.
         * I think s390x can use s390 content as well.
         * ppc only runs ppc
         *
         * But for now, we only except exact matches.
         */

        // FIXME: we may end up needing to compare to "ALL"
        // as well.

        // This could be some fancy db magic if someone were
        // so included, but more than likely will just be
        // some map look ups from a constant map.

        return compatible;
    }
}

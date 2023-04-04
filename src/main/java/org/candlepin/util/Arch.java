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
package org.candlepin.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * Arch
 */
public class Arch {

    private static final List<String> X86_LABELS = Collections.<String>unmodifiableList(Arrays.<String>asList(
        "i386",
        "i486",
        "i586",
        "i686"
    ));

    private Arch() {
        // Intentionally left empty
    }

    /*
     * Returns a Set of the comma separated arch name Strings
     *
     * @return Set of arch names, or an empty set if value is
     *         empty string, or if the 'arch' attribute doesn't
     *         exist
     */
    public static Set<String> parseArches(String arches) {
        Set<String> archesSet = new HashSet<>();

        if (arches == null || arches.trim().equals("")) {
            return archesSet;
        }

        // split on comma, but try to include any whitespace
        // or repeated commas
        for (String arch : Util.toList(arches)) {
            if (!arch.isEmpty()) {
                archesSet.add(arch.trim());
            }
        }

        return archesSet;
    }

    /*
     * determine if contentArch is compatible with consumerArch
     *
     * @param contentArch
     * @param consumerArch
     * @return true if contentArch is compatible with consumerArch, false
     *         otherwise. Note that this is stricter than strict binary
     *         compatibility, and should not be used for that. This is
     *         just to determine appropriate Content set arch matches.
     *         It supports exact match, 'ALL', 'noarch', and the
     *         'x86' alias.
     */
    public static boolean contentForConsumer(String contentArch, String consumerArch) {
        boolean compatible = false;

        // handle "ALL" arch
        if (contentArch.equals("ALL")) {
            compatible = true;
        }
        // exact match
        else if (consumerArch.equals(contentArch)) {
            compatible = true;
        }
        // noarch content can run on any consumer
        else if (contentArch.equals("noarch")) {
            compatible = true;
        }
        // x86 is an alias for anything that
        // could run on an i?86 machine
        else if (contentArch.equals("x86") && X86_LABELS.contains(consumerArch)) {
            compatible = true;
        }

        return compatible;
    }
}

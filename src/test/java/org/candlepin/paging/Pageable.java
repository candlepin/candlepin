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
package org.candlepin.paging;



/**
 * Utility class used by the various paging tests to have a collection of simple objects to page
 */
public class Pageable {
    private final String f1;
    private final String f2;

    public Pageable() {
        this.f1 = null;
        this.f2 = null;
    }

    public Pageable(String f1, String f2) {
        this.f1 = f1;
        this.f2 = f2;
    }

    public String getFieldOne() {
        return this.f1;
    }

    public String getFieldTwo() {
        return this.f2;
    }

    public String toString() {
        return String.format("Pageable [f1: %s, f2: %s]", this.f1, this.f2);
    }
}

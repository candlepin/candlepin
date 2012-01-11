/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * VersionTest
 */
public class VersionTest {

    @Test
    public void testCompareTo() {
        Version v1 = new Version("0.4.0");
        Version v2 = new Version("0.5.1");
        Version v3 = new Version("0.3.99");
        assertEquals(v1.compareTo(v1), 0);
        assertEquals(v1.compareTo(v3), 1);
        assertEquals(v1.compareTo(v2), -1);

    }

}

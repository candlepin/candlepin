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
package org.candlepin.common.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;



/**
 * Test Class for the Util class
 */
public class UtilTest {

    @Test
    public void uniquelong() {
        long[] unique = new long[10000];
        for (int i = 0; i < unique.length; i++) {
            unique[i] = Util.generateUniqueLong();
        }

        Set<Long> nodupes = new HashSet<Long>();
        for (int i = 0; i < unique.length; i++) {
            nodupes.add(unique[i]);
        }

        // if they are truly unique, the original array should
        // not have had any duplicates. Therefore, the Set
        // will have all of the same elements that the original
        // array had.
        assertEquals(unique.length, nodupes.size());
    }

}

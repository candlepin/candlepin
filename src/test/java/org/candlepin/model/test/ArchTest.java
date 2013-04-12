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
package org.candlepin.model.test;

import static org.junit.Assert.*;

import org.candlepin.model.Arch;
import org.junit.Test;

/**
 * ArchTest
 */
public class ArchTest {

    @Test
    public void testArch() {
        Arch arch = new Arch("i386", "i386");
    }

    @Test
    public void testGetId() {
        Arch arch = new Arch("i386", "i386");
        assertEquals("i386", arch.getId());
    }

    @Test
    public void testGetLabel() {
        Arch arch = new Arch("i386", "intel i386");
        assertEquals("intel i386", arch.getLabel());
    }


    @Test
    public void testSetLabel() {
        Arch arch = new Arch("i386", "i386");
        arch.setLabel("intel i386");
        assertEquals("intel i386", arch.getLabel());
    }

}

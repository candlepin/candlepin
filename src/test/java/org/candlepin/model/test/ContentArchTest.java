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
import org.candlepin.model.Content;
import org.candlepin.model.ContentArch;
import org.junit.Test;

/**
 * ContentArchTest
 */
public class ContentArchTest {

    @Test
    public void testContentArch() {
        ContentArch contentArch = new ContentArch();
    }

    /**
     * Test method for {@link org.candlepin.model.ContentArch#ContentArch(org.candlepin.model.Content, org.candlepin.model.Arch)}.
     */
    @Test
    public void testContentArchContentArch() {
        Arch arch = new Arch("i386", "i386");
        Content content = new Content();
        ContentArch contentArch = new ContentArch(content, arch);
    }

    /**
     * Test method for {@link org.candlepin.model.ContentArch#getId()}.
     */
    @Test
    public void testGetId() {
        Arch arch = new Arch("i386", "i386");
        Content content = new Content();
        ContentArch contentArch = new ContentArch(content, arch);
        assertEquals(null, contentArch.getId());
    }

    /**
     * Test method for {@link org.candlepin.model.ContentArch#setId(java.lang.String)}.
     */
    @Test
    public void testSetId() {
        Arch arch = new Arch("i386", "i386");
        Content content = new Content();
        ContentArch contentArch = new ContentArch(content, arch);
        // not sure what to assert here, it doesnt do anything
        contentArch.setId("1");
    }

}

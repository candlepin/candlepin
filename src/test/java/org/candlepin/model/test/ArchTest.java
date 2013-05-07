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

import java.util.List;

import org.candlepin.model.Arch;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

/**
 * ArchTest
 */
public class ArchTest extends DatabaseTestFixture {

    /* FIXME: inherit DatabaseTestFixture and read/write to Arch'es to db */
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

    @Test
    public void testPersistArch() {
        beginTransaction();
        String archHash = String.valueOf(
            Math.abs(Long.valueOf("test-arch-1".hashCode())));
        Arch arch = new Arch(archHash, "test-arch-1");
        entityManager().persist(arch);

        commitTransaction();

        List<?> results = entityManager().createQuery(
                "select arch from Arch as arch").getResultList();
        assertEquals(1, results.size());
    }

    @Test
    public void testUsesContentForExactMatch() {
        Arch consumerArch = new Arch("i386", "i386");
        Arch contentArch = new Arch("i386", "i386");
        assertTrue(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentForWrongArch() {
        Arch consumerArch = new Arch("s390x", "s390x");
        Arch contentArch = new Arch("x86_64", "x86_64");
        assertFalse(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentFori686ForX8664() {
        Arch consumerArch = new Arch("x86_64", "x86_64");
        Arch contentArch = new Arch("i686", "i686");
        assertTrue(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentFori386Fori686() {
        Arch consumerArch = new Arch("i686", "i686");
        Arch contentArch = new Arch("i386", "i386");
        assertTrue(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentFori686Fori586() {
        // an i586 can't use i686 content. Not that
        // RHEL or fedora run on an i586, but...
        Arch consumerArch = new Arch("i586", "i586");
        Arch contentArch = new Arch("i686", "i686");
        assertFalse(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentForx8664ForAll() {
        Arch consumerArch = new Arch("x86_64", "x86_64");
        // the magic "ALL" product arch
        Arch contentArch = new Arch("ALL", "ALL");
        assertTrue(consumerArch.usesContentFor(contentArch));
    }

    @Test
    public void testUsesContentForALL() {
        Arch consumerArch = new Arch("magic", "magic");
        Arch contentArch = new Arch("ALL", "ALL");
        assertTrue(consumerArch.usesContentFor(contentArch));

    }
    /* FIXME: other tests needed
     * - i686 uses content for i386/i486/i586/i686
     * - x86_64 uses content for i386/i486/i586/i686/x86_64
     * - ppc64 uses content for ppc (?)
     * - s390x uses content for s390?
     * - any arch uses noarch content
     *
     * Can we reuse some of the test from say, PreEntitlementRulesTest.java ? They
     * should probably get driven from the same data
     */
}

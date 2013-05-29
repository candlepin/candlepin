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

import org.candlepin.model.Arch;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * ArchCuratorTest
 */
public class ArchCuratorTest extends DatabaseTestFixture {

    @Test
    public void testCreate() {
        Arch testArch = new Arch("123", "z80");
        archCurator.create(testArch);

        Arch lookedup = archCurator.lookupByLabel("z80");
        assertEquals(lookedup.getId(), "123");
        assertEquals(lookedup.getLabel(), "z80");
    }

}

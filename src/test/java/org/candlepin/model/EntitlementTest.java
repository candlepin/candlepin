/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

public class EntitlementTest {

    @Test
    public void testGetOwnerKey() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setId(TestUtil.randomString());
        owner.setKey(ownerKey);
        Entitlement entitlement = new Entitlement();
        entitlement.setOwner(owner);

        assertEquals(ownerKey, entitlement.getOwnerKey());
    }

    @Test
    public void testGetOwnerKeyWithNoOwner() {
        Entitlement entitlement = new Entitlement();

        assertNull(entitlement.getOwnerKey());
    }

}

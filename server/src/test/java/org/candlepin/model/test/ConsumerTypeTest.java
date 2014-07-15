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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.ConsumerType;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.List;

public class ConsumerTypeTest extends DatabaseTestFixture {

    @Test
    public void testSomething() {
        beginTransaction();

        ConsumerType ct = new ConsumerType("standard-system");
        entityManager().persist(ct);

        commitTransaction();

        List<?> results = entityManager().createQuery(
                "select ct from ConsumerType as ct").getResultList();
        assertEquals(1, results.size());
    }

    @Test
    public void testIsType() {
        ConsumerType ct = new ConsumerType("system");

        assertTrue(ct.isType(ConsumerType.ConsumerTypeEnum.SYSTEM));
    }

    @Test
    public void testEquals() {
        ConsumerType ct = new ConsumerType("system");
        ConsumerType ct1 = new ConsumerType("system");
        ConsumerType ct2 = new ConsumerType("fake");

        assertTrue(ct.equals(ct1));
        assertFalse(ct.equals(ct2));
        assertFalse(ct.equals(1));

        ct.setId("10");
        ct1.setId("11");
        assertTrue(ct.equals(ct1));
    }
}

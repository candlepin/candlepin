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
package org.candlepin.model;

import static org.junit.Assert.*;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.InExpression;
import org.hibernate.criterion.LogicalExpression;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;


public class CPRestrictionsTest {

    @Test
    public void testIn() {
        List<String> items = new LinkedList<String>();
        StringBuilder expected = new StringBuilder("taylor in (");

        for (int i = 0; i < AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE * 3; ++i) {
            items.add(String.valueOf(i));

            if (items.size() % AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE == 0) {
                expected.append(i).append(") or taylor in (");
            }
            else {
                expected.append(i).append(", ");
            }
        }
        expected.setLength(expected.length() - 15);

        Criterion crit = CPRestrictions.in("taylor", items);
        LogicalExpression le = (LogicalExpression) crit;
        assertEquals("or", le.getOp());
        assertEquals(expected.toString(), le.toString());
    }

    @Test
    public void testInSimple() {
        List<String> items = new LinkedList<String>();
        String expected = "swift in (";
        int i = 0;

        for (; i < AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE - 1; i++) {
            expected += i + ", ";
            items.add("" + i);
        }

        expected += i + ")";
        items.add("" + i);
        Criterion crit = CPRestrictions.in("swift", items);
        InExpression ie = (InExpression) crit;
        assertEquals(expected, ie.toString());
    }

}


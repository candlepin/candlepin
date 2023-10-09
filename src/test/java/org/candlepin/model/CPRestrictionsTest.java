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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.TestingModules;
import org.candlepin.config.Configuration;
import org.candlepin.config.DatabaseConfigFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.InExpression;
import org.hibernate.criterion.LogicalExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;



public class CPRestrictionsTest {
    private Configuration config;

    @BeforeEach
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest());
        config = injector.getInstance(Configuration.class);
    }

    @Test
    public void testIn() {
        List<String> items = new LinkedList<>();
        StringBuilder expected = new StringBuilder("taylor in (");

        int inBlockSize = config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
        for (int i = 0; i < inBlockSize * 3; ++i) {
            items.add(String.valueOf(i));

            if (items.size() % inBlockSize == 0) {
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
        List<String> items = new LinkedList<>();
        String expected = "swift in (";
        int i = 0;

        int inBlockSize = config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
        for (; i < inBlockSize - 1; i++) {
            expected += i + ", ";
            items.add("" + i);
        }

        expected += i + ")";
        items.add("" + i);
        Criterion crit = CPRestrictions.in("swift", items);
        InExpression ie = (InExpression) crit;
        assertEquals(expected, ie.toString());
    }

    @Test
    public void testInAsArray() {
        List<String> items = new LinkedList<>();
        StringBuilder expected = new StringBuilder("taylor in (");

        int inBlockSize = config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
        for (int i = 0; i < inBlockSize * 3; ++i) {
            items.add(String.valueOf(i));

            if (items.size() % inBlockSize == 0) {
                expected.append(i).append(") or taylor in (");
            }
            else {
                expected.append(i).append(", ");
            }
        }
        expected.setLength(expected.length() - 15);

        Criterion crit = CPRestrictions.in("taylor", items.toArray());
        LogicalExpression le = (LogicalExpression) crit;
        assertEquals("or", le.getOp());
        assertEquals(expected.toString(), le.toString());
    }

    @Test
    public void testInSimpleAsArray() {
        List<String> items = new LinkedList<>();
        String expected = "swift in (";
        int i = 0;

        int inBlockSize = config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
        for (; i < inBlockSize - 1; i++) {
            expected += i + ", ";
            items.add("" + i);
        }

        expected += i + ")";
        items.add("" + i);
        Criterion crit = CPRestrictions.in("swift", items.toArray());
        InExpression ie = (InExpression) crit;
        assertEquals(expected, ie.toString());
    }
}

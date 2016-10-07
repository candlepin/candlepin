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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.*;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



/**
 * AbstractHibernateCuratorTest
 */
@RunWith(JUnitParamsRunner.class)
public class AbstractHibernateCuratorTest extends DatabaseTestFixture {

    /**
     * Test implementation that provides access to some protected methods
     */
    private static class TestHibernateCurator<E extends Persisted> extends AbstractHibernateCurator<E> {
        public TestHibernateCurator(Class entityClass) {
            super(entityClass);
        }

        @Override
        public int bulkSQLUpdate(String table, String column, Map<Object, Object> values,
            Map<String, Object> criteria) {

            return super.bulkSQLUpdate(table, column, values, criteria);
        }
    }


    AbstractHibernateCurator<Content> testContentCurator;

    @Before
    public void setup() {
        this.testContentCurator = new TestHibernateCurator<Content>(Content.class);
        this.injectMembers(this.testContentCurator);
    }

    @Test
    public void testBulkSQLUpdate() throws Exception {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();
        values.put("content 1", "update 1");
        values.put("content 2", "update 2");
        values.put("content ?", "should not exist");

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);

        // Note:
        // This looks like it should be 2, and technically that's what's happening here, but with
        // the way the bulk updater works, even the non-matching columns are getting updated to
        // themselves.
        assertEquals(3, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("update 1", c1.getName());
        assertEquals("update 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }


    @Test
    public void testBulkSQLUpdateSingleUpdate() throws Exception {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();
        values.put("content 1", "update 1");

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);

        assertEquals(1, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("update 1", c1.getName());
        assertEquals("content 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateSingleUpdateNoChange() throws Exception {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();
        values.put("content B", "update 1");

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);

        assertEquals(0, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("content 1", c1.getName());
        assertEquals("content 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithEmptyValues() throws Exception {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);

        assertEquals(0, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("content 1", c1.getName());
        assertEquals("content 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithSingleCriteria() {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();
        values.put("content 1", "update 1");
        values.put("content 2", "update 2");
        values.put("content ?", "should not exist");

        Map<String, Object> criteria = new HashMap<String, Object>();
        criteria.put("name", values.keySet());

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, criteria);

        // Unlike the base test where the result count is 3, this filters by only the values we
        // intend to update, so it should be 2.
        assertEquals(2, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("update 1", c1.getName());
        assertEquals("update 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithMultipleCriteria() {
        Owner owner = this.createOwner();

        Content c1 = this.createContent("c1", "content 1", owner);
        Content c2 = this.createContent("c2", "content 2", owner);
        Content c3 = this.createContent("c3", "content 3", owner);

        Map<Object, Object> values = new HashMap<Object, Object>();
        values.put("content 1", "update 1");
        values.put("content 2", "update 2");
        values.put("content ?", "should not exist");

        Map<String, Object> criteria = new HashMap<String, Object>();
        criteria.put("name", values.keySet());
        criteria.put("content_id", "c2");

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, criteria);

        // Unlike the base test where the result count is 3, this filters by only the values we
        // intend to update, so it should be 1.
        assertEquals(1, result);

        testContentCurator.refresh(c1, c2, c3);

        assertEquals("content 1", c1.getName());
        assertEquals("update 2", c2.getName());
        assertEquals("content 3", c3.getName());
    }

    protected Object[][] largeValueSetSizes() {
        return new Object[][] {
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE + 1), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 1.5), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 1.5 + 1), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2 + 1), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2.5), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2.5 + 1), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3 + 1), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3.5), 0 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3.5 + 1), 0 },

            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE + 1), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 1.5), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 1.5 + 1), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2 + 1), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2.5), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 2.5 + 1), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3 + 1), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3.5), 1 },
            new Object[] { (int) (AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE * 3.5 + 1), 1 },
        };
    }

    @Test
    @Parameters(method = "largeValueSetSizes")
    public void testBulkSQLUpdateWithLargeValueSets(int count, int skip) {
        Owner owner = this.createOwner();

        for (int i = 1; i <= count; ++i) {
            this.createContent("c" + i, "content-" + i, owner);
        }

        Map<Object, Object> values = new LinkedHashMap<Object, Object>();

        for (int i = 1; i <= count; ++i) {
            // We want every odd value to be unaffected, but we still want a fake update entry
            // for the query
            values.put("content-" + (i % 2 == skip ? i : "X" + i), "update-" + i);
        }

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);
        assertEquals(count, result);

        testContentCurator.clear();

        for (int i = 1; i <= count; ++i) {
            Content content = this.ownerContentCurator.getContentById(owner, "c" + i);

            if (i % 2 == skip) {
                assertEquals("update-" + i, content.getName());
            }
            else {
                assertEquals("content-" + i, content.getName());
            }
        }
    }

    protected Object[] largeValueSetAndCriteriaSizes() {
        List<Object[]> entries = new LinkedList<Object[]>();

        // Declaring these as variables because the constant names are loooooooong
        int caseBlockSize = AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE;
        int inBlockSize = AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE;

        for (float multi = 1; multi < 4.0f; multi += 0.5) {
            entries.add(new Object[] { (int) (caseBlockSize * multi), (int) (inBlockSize * multi) });
            entries.add(new Object[] { (int) (caseBlockSize * multi + 1), (int) (inBlockSize * multi + 1) });
        }

        return entries.toArray();
    }

    @Test
    @Parameters(method = "largeValueSetAndCriteriaSizes")
    public void testBulkSQLUpdateWithLargeValueSetAndCriteriaList(int valueCount, int criteriaListSize) {
        Owner owner = this.createOwner();

        Map<Object, Object> values = new HashMap<Object, Object>();

        for (int i = 1; i <= valueCount; ++i) {
            this.createContent("c" + i, "content-" + i, owner);

            // We want every odd value to be unaffected, but we still want a fake update entry
            // for the query
            values.put("content-" + (i % 2 == 0 ? i : "X" + i), "update-" + i);
        }

        Map<String, Object> criteria = new HashMap<String, Object>();
        List<String> valueList = new LinkedList<String>();
        criteria.put("name", valueList);

        for (int i = 1; i <= criteriaListSize; ++i) {
            valueList.add("content-" + (i % 2 == 0 ? i : "X" + i));
        }

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, criteria);
        assertEquals(valueCount / 2, result);

        testContentCurator.clear();

        for (int i = 1; i <= valueCount; ++i) {
            Content content = this.ownerContentCurator.getContentById(owner, "c" + i);

            if (i % 2 == 0) {
                assertEquals("update-" + i, content.getName());
            }
            else {
                assertEquals("content-" + i, content.getName());
            }
        }
    }


}

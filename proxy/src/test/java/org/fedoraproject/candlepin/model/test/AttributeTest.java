/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class AttributeTest extends DatabaseTestFixture {

    @Before
    public void setupTestObjects() {
        String someAttribute = "canEatCheese";
    }

    @Test
    public void testCreateAttr() {
        Attribute newAttr = new Attribute();
    }

    @Test
    public void testAttributeSetName() {
        Attribute newAttr = new Attribute();
        newAttr.setName("OwesUsMoney");
        persistAndCommit(newAttr);
    }

    @Test
    public void testAttributeGetName() {
        Attribute newAttr = new Attribute();
        String someName = "OwesUsMoney";

        newAttr.setName(someName);
        persistAndCommit(newAttr);
        assertEquals(someName, newAttr.getName());
    }

    @Test
    public void testAttributeSetQuantity() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(100);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);
    }

    @Test
    public void testAttributeGetQuantity() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(200);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);
        assertEquals(someNumber, newAttr.getQuantity());
    }

    @Test
    public void testLookup() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(100);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);

        Attribute foundAttr = attributeCurator.find(newAttr.getId());
        assertEquals(newAttr.getName(), foundAttr.getName());
    }

    @Test
    public void testList() throws Exception {
        List<Attribute> attributes = attributeCurator.findAll();
        int beforeCount = attributes.size();

        attributes = attributeCurator.findAll();
        int afterCount = attributes.size();
        // assertEquals(10, afterCount - beforeCount);
    }

}

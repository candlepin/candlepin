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

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

public class AttributeTest extends DatabaseTestFixture {

    @Test
    public void testLookup() {
        Attribute newAttr = new Attribute("OwesUsMoney_100", "100");
        attributeCurator.create(newAttr);

        Attribute foundAttr = attributeCurator.find(newAttr.getId());
        assertEquals(newAttr.getName(), foundAttr.getName());
        assertEquals(newAttr.getValue(), foundAttr.getValue());
        assertEquals(0, foundAttr.getChildAttributes().size());
    }

    @Test
    public void testChildAttributes() {
        Attribute parent = new Attribute("parent", "p");
        Attribute child1 = new Attribute("child1", "c1");
        Attribute grandChild1 = new Attribute("grandChild1", "gc1");
        Attribute child2 = new Attribute("child2", "c2");

        child1.addChildAttribute(grandChild1);
        parent.addChildAttribute(child1);
        parent.addChildAttribute(child2);
        attributeCurator.create(parent);

        Attribute foundAttr = attributeCurator.find(parent.getId());
        assertEquals(2, parent.getChildAttributes().size());
        assertEquals(1, parent.getChildAttribute(child1.getName())
            .getChildAttributes().size());
    }

}

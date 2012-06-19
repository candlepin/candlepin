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
package org.candlepin.resource.test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.candlepin.model.ConsumerType;
import org.candlepin.resource.ConsumerTypeResource;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerTypeResourceTest
 */
public class ConsumerTypeResourceTest extends DatabaseTestFixture {

    protected ConsumerTypeResource consumerTypeResource;

    @Before
    public void setUp() {
        consumerTypeResource = injector.getInstance(ConsumerTypeResource.class);
    }

    @Test
    public void testCRUD() {
        String type = "JarJar-" + System.currentTimeMillis();
        ConsumerType consumerType = new ConsumerType();
        consumerType.setLabel(type);
        assertEquals("the label getter works", type, consumerType.getLabel());
        // Ensure it is not there
        assertTrue("The type already exists", !this.typeExists(type));
        // Create it
        consumerType = consumerTypeResource.create(consumerType);

        // Make sure it is there
        assertTrue("The type was not found", this.typeExists(type));

        // Find it by ID
        ConsumerType consumerType2 = consumerTypeResource
            .getConsumerType(consumerType.getId());
        assertNotNull("The type was not found by ID", consumerType2);

        // Update it
        String type2 = "JarJar2-" + System.currentTimeMillis();
        consumerType.setLabel(type2);
        consumerTypeResource.update(consumerType);
        assertTrue("The update was not found", this.typeExists(type2));

        // Delete It
        consumerTypeResource.deleteConsumerType(consumerType.getId());

        // Make sure it is not there
        assertTrue("The type was found after a delete", !this.typeExists(type));
    }

    public boolean typeExists(String type) {
        List<ConsumerType> types = consumerTypeResource.list();
        boolean found = false;
        for (ConsumerType aType : types) {
            found = type.equals(aType.getLabel());
            if (found) {
                break;
            }
        }
        return found;
    }

}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerContentOverrideCuratorTest
 */
public class ConsumerContentOverrideCuratorTest extends DatabaseTestFixture {

    private Consumer consumer;
    private Owner owner;

    @Before
    public void setUp() {
        owner = createOwner();
        consumer = createConsumer(owner);
    }

    @Test
    public void testCreateOverride() {
        ConsumerContentOverride override = new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1");
        assertEquals(override, this.consumerContentOverrideCurator.create(override));
    }

    @Test
    public void testCreateOverrideForcesLowercaseName() {
        ConsumerContentOverride override = new ConsumerContentOverride(consumer,
            "test-repo", "GpGCheck", "1");
        ConsumerContentOverride created =
            this.consumerContentOverrideCurator.create(override);
        assertEquals("gpgcheck", created.getName());
    }

    @Test
    public void testModifyOverride() {
        ConsumerContentOverride override = new ConsumerContentOverride(consumer,
            "test-repo", "GpGCheck", "1");
        ConsumerContentOverride created =
            this.consumerContentOverrideCurator.create(override);
        created.setValue("0");
        ConsumerContentOverride merged = this.consumerContentOverrideCurator.merge(created);
        assertEquals("0", merged.getValue());
    }

    @Test
    public void testModifyOverrideForcesNameToLowercase() {
        ConsumerContentOverride override = new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "0");
        ConsumerContentOverride created =
            this.consumerContentOverrideCurator.create(override);
        created.setName("GPGCHECK");
        ConsumerContentOverride merged = this.consumerContentOverrideCurator.merge(created);
        assertEquals("gpgcheck", merged.getName());
    }

    @Test
    public void testRetrieveByName() {
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        ConsumerContentOverride found =
            consumerContentOverrideCurator.retrieve(consumer, "test-repo", "gpgcheck");
        assertNotNull(found);
        assertEquals(consumer, found.getConsumer());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameIsCaseInsensitive() {
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        ConsumerContentOverride found =
            consumerContentOverrideCurator.retrieve(consumer, "test-repo", "GPGCheck");
        assertNotNull(found);
        assertEquals(consumer, found.getConsumer());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameDoesntExist() {
        ConsumerContentOverride found =
            consumerContentOverrideCurator.retrieve(consumer, "not-a-repo", "gpgcheck");
        assertNull(found);
    }

    @Test
    public void testRemoveByName() {
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "remaining-override", "remaining"));
        consumerContentOverrideCurator.removeByName(consumer, "test-repo", "gpgcheck");
        List<ConsumerContentOverride> remaining =
            consumerContentOverrideCurator.getList(consumer);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByNameCaseInsensitive() {
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "remaining-override", "remaining"));
        consumerContentOverrideCurator.removeByName(consumer, "test-repo", "GpGChecK");
        List<ConsumerContentOverride> remaining =
            consumerContentOverrideCurator.getList(consumer);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByContentLabel() {
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "foo", "foo-v"));
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "should-remain", "remaining", "true"));
        consumerContentOverrideCurator.removeByContentLabel(consumer, "test-repo");
        List<ConsumerContentOverride> remaining =
            consumerContentOverrideCurator.getList(consumer);
        assertEquals(1, remaining.size());
        assertEquals("should-remain", remaining.get(0).getContentLabel());
        assertEquals("remaining", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByConsumer() {
        Consumer consumer2 = createConsumer(owner);
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer2,
            "test-repo", "gpgcheck", "1"));

        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "test-repo", "gpgcheck", "1"));
        consumerContentOverrideCurator.create(new ConsumerContentOverride(consumer,
            "another-test-repo", "gpgcheck", "0"));
        consumerContentOverrideCurator.removeByConsumer(consumer);

        assertTrue(consumerContentOverrideCurator.getList(consumer).isEmpty());
        assertEquals(1, consumerContentOverrideCurator.getList(consumer2).size());
    }
}

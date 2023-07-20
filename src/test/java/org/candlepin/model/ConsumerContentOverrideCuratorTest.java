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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;



/**
 * ConsumerContentOverrideCuratorTest
 */
public class ConsumerContentOverrideCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;
    private Consumer consumer;

    @BeforeEach
    public void setUp() {
        owner = this.createOwner("test-owner", "Test Owner");
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        consumer = new Consumer()
            .setName("a consumer")
            .setUsername("username")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
    }

    private ConsumerContentOverride buildConsumerContentOverride(Consumer consumer) {
        String suffix = TestUtil.randomString(8, TestUtil.CHARSET_NUMERIC_HEX);

        return new ConsumerContentOverride()
            .setConsumer(consumer)
            .setContentLabel("cco-" + suffix)
            .setName("test_override-" + suffix)
            .setValue("value-" + suffix);
    }

    @Test
    public void normalCreateAndRetrieve() {
        ConsumerContentOverride cco = this.buildConsumerContentOverride(this.consumer);
        this.consumerContentOverrideCurator.create(cco);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        ConsumerContentOverride fetched = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), cco.getName());

        assertNotNull(fetched);
        assertEquals(cco.getValue(), fetched.getValue());
    }

    @Test
    public void normalCreateAndUpdate() {
        ConsumerContentOverride cco = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco);
        this.consumerContentOverrideCurator.flush();

        cco.setValue("value-update");
        consumerContentOverrideCurator.merge(cco);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), cco.getName());

        assertNotNull(cco2);
        assertEquals("value-update", cco2.getValue());
    }

    @Test
    public void deleteByName() {
        ConsumerContentOverride cco = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco);

        consumerContentOverrideCurator.removeByName(this.consumer, cco.getContentLabel(), cco.getName());
        this.consumerContentOverrideCurator.flush();

        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), cco.getName());

        assertNull(cco2);
    }

    @Test
    public void deleteByNameCaseInsensitive() {
        ConsumerContentOverride cco = this.buildConsumerContentOverride(this.consumer)
            .setName("naME");
        consumerContentOverrideCurator.create(cco);

        consumerContentOverrideCurator.removeByName(this.consumer, cco.getContentLabel(), "nAMe");
        this.consumerContentOverrideCurator.flush();

        ConsumerContentOverride cco2 = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), cco.getName());

        assertNull(cco2);
    }

    @Test
    public void retrieveByNameCaseInsensitive() {
        ConsumerContentOverride cco = this.buildConsumerContentOverride(this.consumer)
            .setName("naME");
        this.consumerContentOverrideCurator.create(cco);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        ConsumerContentOverride ccof1 = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), "naME");

        ConsumerContentOverride ccof2 = consumerContentOverrideCurator.retrieve(this.consumer,
            cco.getContentLabel(), "Name");

        assertNotNull(ccof1);
        assertNotNull(ccof2);
        assertEquals(ccof1, ccof2);
    }

    @Test
    public void deleteByLabel() {
        ConsumerContentOverride cco1 = this.buildConsumerContentOverride(this.consumer)
            .setContentLabel("test-content");
        consumerContentOverrideCurator.create(cco1);

        ConsumerContentOverride cco2 = this.buildConsumerContentOverride(this.consumer)
            .setContentLabel("test-content");
        consumerContentOverrideCurator.create(cco2);

        this.consumerContentOverrideCurator.flush();

        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll().list();
        assertEquals(2, ccoList.size());

        consumerContentOverrideCurator.removeByContentLabel(consumer, "test-content");
        List<ConsumerContentOverride> ccoList2 = consumerContentOverrideCurator.listAll().list();
        assertEquals(0, ccoList2.size());
    }

    @Test
    public void deleteByConsumer() {
        ConsumerContentOverride cco1 = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco1);
        ConsumerContentOverride cco2 = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco2);

        this.consumerContentOverrideCurator.flush();
        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll().list();
        assertEquals(2, ccoList.size());

        consumerContentOverrideCurator.removeByParent(consumer);
        List<ConsumerContentOverride> ccoList2 = consumerContentOverrideCurator.listAll().list();
        assertEquals(0, ccoList2.size());
    }

    @Test
    public void testAddOrUpdateUpdatesValue() {
        ConsumerContentOverride cco1 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test_content-1")
            .setName("cco-1")
            .setValue("value-1");

        this.consumerContentOverrideCurator.create(cco1);
        this.consumerContentOverrideCurator.flush();

        ConsumerContentOverride cco2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test_content-1")
            .setName("cco-1")
            .setValue("value-2");

        this.consumerContentOverrideCurator.addOrUpdate(consumer, cco2);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll().list();
        assertEquals(1, ccoList.size());
        assertEquals(cco2.getValue(), ccoList.get(0).getValue());
    }

    @Test
    public void testAddOrUpdateCreatesNew() {
        ConsumerContentOverride cco1 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-content1")
            .setName("name1")
            .setValue("value");

        consumerContentOverrideCurator.create(cco1);

        ConsumerContentOverride cco2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-content2")
            .setName("name2")
            .setValue("value2");

        consumerContentOverrideCurator.addOrUpdate(consumer, cco2);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll().list();
        assertEquals(2, ccoList.size());
    }

    @Test
    public void testCreateOverride() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer);
        assertEquals(override, this.consumerContentOverrideCurator.create(override));
    }

    @Test
    public void testCreateOverrideForcesLowercaseName() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer)
            .setName("GpGCheck");

        ConsumerContentOverride created = this.consumerContentOverrideCurator.create(override);
        assertEquals("gpgcheck", created.getName());
    }

    @Test
    public void testModifyOverride() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer)
            .setValue("1");

        ConsumerContentOverride created = this.consumerContentOverrideCurator.create(override);
        created.setValue("0");

        ConsumerContentOverride merged = this.consumerContentOverrideCurator.merge(created);
        assertEquals("0", merged.getValue());
    }

    @Test
    public void testModifyOverrideForcesNameToLowercase() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer)
            .setName("gpgcheck");

        ConsumerContentOverride created = this.consumerContentOverrideCurator.create(override);
        created.setName("GPGCHECK");

        ConsumerContentOverride merged = this.consumerContentOverrideCurator.merge(created);
        assertEquals("gpgcheck", merged.getName());
    }

    @Test
    public void testRetrieveByName() {
        ConsumerContentOverride override = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test_content_label")
            .setName("test_override")
            .setValue("1");

        this.consumerContentOverrideCurator.create(override);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        ConsumerContentOverride found = consumerContentOverrideCurator
            .retrieve(this.consumer, override.getContentLabel(), override.getName());

        assertNotNull(found);
        assertEquals(override.getConsumer(), found.getConsumer());
        assertEquals(override.getContentLabel(), found.getContentLabel());
        assertEquals(override.getName(), found.getName());
        assertEquals(override.getValue(), found.getValue());
    }

    @Test
    public void testRetrieveByNameIsCaseInsensitive() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer)
            .setName("gpgcheck");

        this.consumerContentOverrideCurator.create(override);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        ConsumerContentOverride found = this.consumerContentOverrideCurator
            .retrieve(this.consumer, override.getContentLabel(), "GPGCheck");

        assertNotNull(found);
        assertEquals(override.getConsumer(), found.getConsumer());
        assertEquals(override.getContentLabel(), found.getContentLabel());
        assertEquals(override.getName(), found.getName());
        assertEquals(override.getValue(), found.getValue());
    }

    @Test
    public void testRetrieveByNameDoesntExist() {
        ConsumerContentOverride override = this.buildConsumerContentOverride(this.consumer);
        this.consumerContentOverrideCurator.create(override);
        this.consumerContentOverrideCurator.flush();

        ConsumerContentOverride found = this.consumerContentOverrideCurator
            .retrieve(this.consumer, "not-a-repo", "gpgcheck");

        assertNull(found);
    }

    @Test
    public void testRemoveByName() {
        ConsumerContentOverride override1 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("gpgcheck")
            .setValue("1");

        ConsumerContentOverride override2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("remaining-override")
            .setValue("remaining");

        this.consumerContentOverrideCurator.create(override1);
        this.consumerContentOverrideCurator.create(override2);
        this.consumerContentOverrideCurator.flush();
        this.consumerContentOverrideCurator.clear();

        this.consumerContentOverrideCurator
            .removeByName(this.consumer, override1.getContentLabel(), override1.getName());

        this.consumerContentOverrideCurator.flush();

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer).list();
        assertEquals(1, remaining.size());
        assertEquals(override2.getName(), remaining.get(0).getName());
    }

    @Test
    public void testRemoveByNameCaseInsensitive() {
        ConsumerContentOverride override1 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("gpgcheck")
            .setValue("1");

        ConsumerContentOverride override2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("remaining-override")
            .setValue("remaining");

        this.consumerContentOverrideCurator.create(override1);
        this.consumerContentOverrideCurator.create(override2);
        this.consumerContentOverrideCurator.flush();

        this.consumerContentOverrideCurator.removeByName(consumer, override1.getContentLabel(), "GpGChecK");

        this.consumerContentOverrideCurator.flush();

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer).list();
        assertEquals(1, remaining.size());
        assertEquals(override2.getName(), remaining.get(0).getName());
    }

    @Test
    public void testRemoveByContentLabel() {
        ConsumerContentOverride override1 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("gpgcheck")
            .setValue("1");

        ConsumerContentOverride override2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("foo")
            .setValue("foo-v");

        ConsumerContentOverride override3 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("should-remain")
            .setName("remaining")
            .setValue("true");

        this.consumerContentOverrideCurator.create(override1);
        this.consumerContentOverrideCurator.create(override2);
        this.consumerContentOverrideCurator.create(override3);
        this.consumerContentOverrideCurator.flush();

        this.consumerContentOverrideCurator.removeByContentLabel(consumer, "test-repo");

        this.consumerContentOverrideCurator.flush();

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer).list();
        assertEquals(1, remaining.size());
        assertEquals(override3.getContentLabel(), remaining.get(0).getContentLabel());
        assertEquals(override3.getName(), remaining.get(0).getName());
    }

    @Test
    public void testRemoveByConsumer() {
        Consumer consumer2 = this.createConsumer(owner);

        ConsumerContentOverride override1 = new ConsumerContentOverride()
            .setConsumer(consumer2)
            .setContentLabel("test-repo")
            .setName("gpgcheck")
            .setValue("1");

        ConsumerContentOverride override2 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("test-repo")
            .setName("gpgcheck")
            .setValue("1");

        ConsumerContentOverride override3 = new ConsumerContentOverride()
            .setConsumer(this.consumer)
            .setContentLabel("another-test-repo")
            .setName("override-name")
            .setValue("0");

        this.consumerContentOverrideCurator.create(override1);
        this.consumerContentOverrideCurator.create(override2);
        this.consumerContentOverrideCurator.create(override3);
        this.consumerContentOverrideCurator.flush();

        this.consumerContentOverrideCurator.removeByParent(this.consumer);
        this.consumerContentOverrideCurator.flush();

        assertTrue(consumerContentOverrideCurator.getList(consumer).list().isEmpty());
        assertEquals(1, consumerContentOverrideCurator.getList(consumer2).list().size());
    }
}

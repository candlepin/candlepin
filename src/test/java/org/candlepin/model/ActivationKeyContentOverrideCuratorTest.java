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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;



/**
 * ActivationKeyContentOverrideCuratorTest
 *
 * Slightly modified from ConsumerContentOverrideCuratorTest
 */
public class ActivationKeyContentOverrideCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;
    private ActivationKey key;

    @BeforeEach
    public void setUp() {
        this.owner = new Owner()
            .setKey("test-owner")
            .setDisplayName("Test Owner");

        this.owner = ownerCurator.create(this.owner);

        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        key = new ActivationKey("a key", owner);
        activationKeyCurator.create(key);
    }

    private ActivationKeyContentOverride buildActivationKeyContentOverride(ActivationKey key,
        String contentLabel, String name, String value) {

        return new ActivationKeyContentOverride()
            .setKey(key)
            .setContentLabel(contentLabel)
            .setName(name)
            .setValue(value);
    }

    @Test
    public void normalCreateAndRetrieve() {
        ActivationKeyContentOverride cco = this.buildActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        ActivationKeyContentOverride cco2 = activationKeyContentOverrideCurator.retrieve(
            key, "test-content", "name");
        assert (cco2 != null);
        assertEquals("value", cco2.getValue());
    }

    @Test
    public void normalCreateAndUpdate() {
        ActivationKeyContentOverride cco = this.buildActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        cco.setValue("value-update");
        activationKeyContentOverrideCurator.merge(cco);

        ActivationKeyContentOverride cco2 = activationKeyContentOverrideCurator.retrieve(key, "test-content",
            "name");
        assert (cco2 != null);
        assertEquals("value-update", cco2.getValue());
    }

    @Test
    public void deleteByName() {
        ActivationKeyContentOverride cco = this.buildActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        activationKeyContentOverrideCurator.removeByName(key, "test-content", "name");
        ActivationKeyContentOverride cco2 = activationKeyContentOverrideCurator.retrieve(
            key, "test-content", "name");
        assert (cco2 == null);
    }

    @Test
    public void deleteByLabel() {
        ActivationKeyContentOverride cco1 = this.buildActivationKeyContentOverride(
            key, "test-content", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = this.buildActivationKeyContentOverride(
            key, "test-content", "name2", "value");
        activationKeyContentOverrideCurator.create(cco2);

        activationKeyContentOverrideCurator.removeByContentLabel(key, "test-content");
        List<ActivationKeyContentOverride> ccoList = activationKeyContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }

    @Test
    public void deleteByConsumer() {
        ActivationKeyContentOverride cco1 = this.buildActivationKeyContentOverride(
            key, "test-content1", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = this.buildActivationKeyContentOverride(
            key, "test-content2", "name2", "value");
        activationKeyContentOverrideCurator.create(cco2);

        activationKeyContentOverrideCurator.removeByParent(key);
        List<ActivationKeyContentOverride> ccoList = activationKeyContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }

    @Test
    public void testCreateOverride() {
        ActivationKeyContentOverride override = this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1");
        assertEquals(override, this.activationKeyContentOverrideCurator.create(override));
    }

    @Test
    public void testCreateOverrideForcesLowercaseName() {
        ActivationKeyContentOverride override = this.buildActivationKeyContentOverride(key,
            "test-repo", "GpGCheck", "1");
        ActivationKeyContentOverride created = this.activationKeyContentOverrideCurator.create(override);
        assertEquals("gpgcheck", created.getName());
    }

    @Test
    public void testModifyOverride() {
        ActivationKeyContentOverride override = this.buildActivationKeyContentOverride(key,
            "test-repo", "GpGCheck", "1");
        ActivationKeyContentOverride created = this.activationKeyContentOverrideCurator.create(override);
        created.setValue("0");
        ActivationKeyContentOverride merged = this.activationKeyContentOverrideCurator.merge(created);
        assertEquals("0", merged.getValue());
    }

    @Test
    public void testModifyOverrideForcesNameToLowercase() {
        ActivationKeyContentOverride override = this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "0");
        ActivationKeyContentOverride created = this.activationKeyContentOverrideCurator.create(override);
        created.setName("GPGCHECK");
        ActivationKeyContentOverride merged = this.activationKeyContentOverrideCurator.merge(created);
        assertEquals("gpgcheck", merged.getName());
    }

    @Test
    public void testRetrieveByName() {
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        ActivationKeyContentOverride found = activationKeyContentOverrideCurator
            .retrieve(key, "test-repo", "gpgcheck");

        assertNotNull(found);
        assertEquals(key, found.getKey());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameIsCaseInsensitive() {
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        ActivationKeyContentOverride found = activationKeyContentOverrideCurator.retrieve(key, "test-repo",
            "GPGCheck");
        assertNotNull(found);
        assertEquals(key, found.getKey());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameDoesntExist() {
        ActivationKeyContentOverride found = activationKeyContentOverrideCurator.retrieve(key, "not-a-repo",
            "gpgcheck");
        assertNull(found);
    }

    @Test
    public void testRemoveByName() {
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "remaining-override", "remaining"));
        activationKeyContentOverrideCurator.removeByName(key, "test-repo", "gpgcheck");
        List<ActivationKeyContentOverride> remaining = activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByNameCaseInsensitive() {
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "remaining-override", "remaining"));
        activationKeyContentOverrideCurator.removeByName(key, "test-repo", "GpGChecK");
        List<ActivationKeyContentOverride> remaining = activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByContentLabel() {
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "foo", "foo-v"));
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "should-remain", "remaining", "true"));
        activationKeyContentOverrideCurator.removeByContentLabel(key, "test-repo");
        List<ActivationKeyContentOverride> remaining = activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("should-remain", remaining.get(0).getContentLabel());
        assertEquals("remaining", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByConsumer() {
        ActivationKey key2 = new ActivationKey("other key", owner);
        key2 = activationKeyCurator.create(key2);
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key2,
            "test-repo", "gpgcheck", "1"));

        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(this.buildActivationKeyContentOverride(key,
            "another-test-repo", "gpgcheck", "0"));
        activationKeyContentOverrideCurator.removeByParent(key);

        assertTrue(activationKeyContentOverrideCurator.getList(key).isEmpty());
        assertEquals(1, activationKeyContentOverrideCurator.getList(key2).size());
    }

    @Test
    public void testRetrieveAllWithNullConsumer() {
        ActivationKeyContentOverride override = new ActivationKeyContentOverride()
            .setContentLabel(TestUtil.randomString())
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        Map<String, Map<String, ActivationKeyContentOverride>> actual = activationKeyContentOverrideCurator
            .retrieveAll(null, List.of(override));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testRetrieveAll() {
        Owner owner = this.createOwner();
        ActivationKey key1 = this.createActivationKey(owner);
        ActivationKey key2 = this.createActivationKey(owner);

        ActivationKeyContentOverride key1Override1 = new ActivationKeyContentOverride()
            .setKey(key1)
            .setContentLabel("c1-repo1")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ActivationKeyContentOverride key2Override1 = new ActivationKeyContentOverride()
            .setKey(key2)
            .setContentLabel("c2-repo1")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ActivationKeyContentOverride key2Override2 = new ActivationKeyContentOverride()
            .setKey(key2)
            .setContentLabel("c2-repo2")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ActivationKeyContentOverride key2Override3 = new ActivationKeyContentOverride()
            .setKey(key2)
            .setContentLabel("c2-repo3")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        key1Override1 = activationKeyContentOverrideCurator.create(key1Override1);
        key2Override1 = activationKeyContentOverrideCurator.create(key2Override1);
        key2Override2 = activationKeyContentOverrideCurator.create(key2Override2);
        key2Override3 = activationKeyContentOverrideCurator.create(key2Override3);

        Map<String, Map<String, ActivationKeyContentOverride>> actual = activationKeyContentOverrideCurator
            .retrieveAll(key2, List.of(key2Override1, key2Override2));

        Map<String, ActivationKeyContentOverride> expected1 = Map.of(key2Override1.getName(), key2Override1);
        Map<String, ActivationKeyContentOverride> expected2 = Map.of(key2Override2.getName(), key2Override2);

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsOnlyKeys(List.of(key2Override1.getContentLabel(), key2Override2.getContentLabel()))
            .extractingByKeys(key2Override1.getContentLabel(), key2Override2.getContentLabel())
            .containsExactly(expected1, expected2);
    }
}

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
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;



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

        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll();
        assertEquals(2, ccoList.size());

        consumerContentOverrideCurator.removeByContentLabel(consumer, "test-content");
        List<ConsumerContentOverride> ccoList2 = consumerContentOverrideCurator.listAll();
        assertEquals(0, ccoList2.size());
    }

    @Test
    public void deleteByConsumer() {
        ConsumerContentOverride cco1 = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco1);
        ConsumerContentOverride cco2 = this.buildConsumerContentOverride(this.consumer);
        consumerContentOverrideCurator.create(cco2);

        this.consumerContentOverrideCurator.flush();
        List<ConsumerContentOverride> ccoList = consumerContentOverrideCurator.listAll();
        assertEquals(2, ccoList.size());

        consumerContentOverrideCurator.removeByParent(consumer);
        List<ConsumerContentOverride> ccoList2 = consumerContentOverrideCurator.listAll();
        assertEquals(0, ccoList2.size());
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

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer);
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

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer);
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

        List<ConsumerContentOverride> remaining = consumerContentOverrideCurator.getList(consumer);
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

        assertTrue(consumerContentOverrideCurator.getList(consumer).isEmpty());
        assertEquals(1, consumerContentOverrideCurator.getList(consumer2).size());
    }

    @Test
    public void testGetLayeredContentOverridesIncludesConsumerContentOverrides() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);

        ConsumerContentOverride consumerOverride1 = new ConsumerContentOverride()
            .setConsumer(consumer1)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-consumer_val-1");

        ConsumerContentOverride consumerOverride2 = new ConsumerContentOverride()
            .setConsumer(consumer1)
            .setContentLabel("repo1")
            .setName("attrib2")
            .setValue("attrib2-consumer_val-2");

        ConsumerContentOverride consumerOverride3 = new ConsumerContentOverride()
            .setConsumer(consumer2)
            .setContentLabel("repo1")
            .setName("attrib3")
            .setValue("attrib3-consumer_val-3");

        this.consumerContentOverrideCurator.create(consumerOverride1);
        this.consumerContentOverrideCurator.create(consumerOverride2);
        this.consumerContentOverrideCurator.create(consumerOverride3);

        List<ContentOverride<?, ?>> overrides1 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1);

        List<ContentOverride<?, ?>> overrides2 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1.getId());

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(consumerOverride1, consumerOverride2);

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(consumerOverride1, consumerOverride2);
    }

    @Test
    public void testGetLayeredContentOverridesIncludesEnvironmentContentOverrides() {
        Owner owner = this.createOwner();
        Environment env1 = this.createEnvironment(owner);
        Environment env2 = this.createEnvironment(owner);
        Consumer consumer1 = this.createConsumer(owner);

        EnvironmentContentOverride envOverride1 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-1");

        EnvironmentContentOverride envOverride2 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib2")
            .setValue("attrib2-env_value-2");

        EnvironmentContentOverride envOverride3 = new EnvironmentContentOverride()
            .setEnvironment(env2)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-3");

        this.environmentContentOverrideCurator.create(envOverride1);
        this.environmentContentOverrideCurator.create(envOverride2);
        this.environmentContentOverrideCurator.create(envOverride3);

        consumer1.addEnvironment(env1);

        this.consumerCurator.merge(consumer1);

        List<ContentOverride<?, ?>> overrides1 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1);

        List<ContentOverride<?, ?>> overrides2 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1.getId());

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(envOverride1, envOverride2);

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(envOverride1, envOverride2);
    }

    /**
     * This test verifies that the layering logic will use the override from the highest priority
     * environment in the case of a conflict on override label+name in multiple environments, *but
     * no conflicting override on the consumer*.
     */
    @Test
    public void testGetLayeredContentOverridesPrefersHighestPriorityEnvironment() {
        Owner owner = this.createOwner();
        Environment env1 = this.createEnvironment(owner);
        Environment env2 = this.createEnvironment(owner);
        Consumer consumer1 = this.createConsumer(owner);

        EnvironmentContentOverride envOverride1 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-1");

        EnvironmentContentOverride envOverride2 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib2")
            .setValue("attrib2-env_value-2");

        EnvironmentContentOverride envOverride3 = new EnvironmentContentOverride()
            .setEnvironment(env2)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-3");

        this.environmentContentOverrideCurator.create(envOverride1);
        this.environmentContentOverrideCurator.create(envOverride2);
        this.environmentContentOverrideCurator.create(envOverride3);

        // Order matters here. env2 being added first gives it higher priority, so we should get its
        // overrides before env1's.
        consumer1.addEnvironment(env2);
        consumer1.addEnvironment(env1);

        this.consumerCurator.merge(consumer1);

        List<ContentOverride<?, ?>> overrides1 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1);

        List<ContentOverride<?, ?>> overrides2 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1.getId());

        assertThat(overrides1)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(envOverride2, envOverride3);

        assertThat(overrides2)
            .hasSize(2)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(envOverride2, envOverride3);
    }

    /**
     * This test verifies that the layering behavior will prefer the override on the consumer in
     * cases where there is a conflict on label+name between overrides; even if there is a conflict
     * between the consumer's environments.
     */
    @Test
    public void testGetLayeredContentOverridesPrefersConsumerOverride() {
        Owner owner = this.createOwner();
        Environment env1 = this.createEnvironment(owner);
        Environment env2 = this.createEnvironment(owner);
        Consumer consumer1 = this.createConsumer(owner);

        EnvironmentContentOverride envOverride1 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-1");

        EnvironmentContentOverride envOverride2 = new EnvironmentContentOverride()
            .setEnvironment(env1)
            .setContentLabel("repo1")
            .setName("attrib2")
            .setValue("attrib2-env_value");

        EnvironmentContentOverride envOverride3 = new EnvironmentContentOverride()
            .setEnvironment(env2)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-env_value-2");

        ConsumerContentOverride consumerOverride1 = new ConsumerContentOverride()
            .setConsumer(consumer1)
            .setContentLabel("repo1")
            .setName("attrib1")
            .setValue("attrib1-consumer_val");

        ConsumerContentOverride consumerOverride2 = new ConsumerContentOverride()
            .setConsumer(consumer1)
            .setContentLabel("repo1")
            .setName("attrib3")
            .setValue("attrib3-consumer_val");

        this.environmentContentOverrideCurator.create(envOverride1);
        this.environmentContentOverrideCurator.create(envOverride2);
        this.environmentContentOverrideCurator.create(envOverride3);
        this.consumerContentOverrideCurator.create(consumerOverride1);
        this.consumerContentOverrideCurator.create(consumerOverride2);

        // Technically the order matters here, but the conflict should be ignored in favor of the
        // consumer's specific content override anyway
        consumer1.addEnvironment(env2);
        consumer1.addEnvironment(env1);
        this.consumerCurator.merge(consumer1);

        List<ContentOverride<?, ?>> overrides1 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1);

        List<ContentOverride<?, ?>> overrides2 = this.consumerContentOverrideCurator
            .getLayeredContentOverrides(consumer1.getId());

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(consumerOverride1, envOverride2, consumerOverride2);

        assertThat(overrides2)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorOnFields("contentLabel", "name", "value")
            .containsExactlyInAnyOrder(consumerOverride1, envOverride2, consumerOverride2);
    }

    @Test
    public void testRetrieveAllWithNullConsumer() {
        ConsumerContentOverride override = new ConsumerContentOverride()
            .setContentLabel(TestUtil.randomString())
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        Map<String, Map<String, ConsumerContentOverride>> actual = consumerContentOverrideCurator
            .retrieveAll(null, List.of(override));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testRetrieveAll() {
        Owner owner = this.createOwner();
        Consumer c1 = this.createConsumer(owner);
        Consumer c2 = this.createConsumer(owner);

        ConsumerContentOverride c1Override1 = new ConsumerContentOverride()
            .setConsumer(c1)
            .setContentLabel("c1-repo1")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ConsumerContentOverride c2Override1 = new ConsumerContentOverride()
            .setConsumer(c2)
            .setContentLabel("c2-repo1")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ConsumerContentOverride c2Override2 = new ConsumerContentOverride()
            .setConsumer(c2)
            .setContentLabel("c2-repo2")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        ConsumerContentOverride c2Override3 = new ConsumerContentOverride()
            .setConsumer(c2)
            .setContentLabel("c2-repo3")
            .setName(TestUtil.randomString())
            .setValue(TestUtil.randomString());

        c1Override1 = consumerContentOverrideCurator.create(c1Override1);
        c2Override1 = consumerContentOverrideCurator.create(c2Override1);
        c2Override2 = consumerContentOverrideCurator.create(c2Override2);
        c2Override3 = consumerContentOverrideCurator.create(c2Override3);

        Map<String, Map<String, ConsumerContentOverride>> actual = consumerContentOverrideCurator
            .retrieveAll(c2, List.of(c2Override1, c2Override2));

        Map<String, ConsumerContentOverride> expected1 = Map.of(c2Override1.getName(), c2Override1);
        Map<String, ConsumerContentOverride> expected2 = Map.of(c2Override2.getName(), c2Override2);

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsOnlyKeys(List.of(c2Override1.getContentLabel(), c2Override2.getContentLabel()))
            .extractingByKeys(c2Override1.getContentLabel(), c2Override2.getContentLabel())
            .containsExactly(expected1, expected2);
    }
}

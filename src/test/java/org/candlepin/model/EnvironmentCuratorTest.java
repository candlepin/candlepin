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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public class EnvironmentCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Environment typedEnvironment;

    @BeforeEach
    public void setUp() {
        this.owner = this.createOwner("test-owner", "Test Owner");
    }

    @Test
    public void create() {
        Environment environment = environmentCurator.create(new Environment("env1", "Env 1", owner));
        assertEquals(1, environmentCurator.listAll().size());
        Environment e = environmentCurator.get("env1");
        assertEquals(owner, e.getOwner());
    }

    @Test
    public void delete() {
        Environment environment = environmentCurator.create(new Environment("env1", "Env 1", owner));
        environmentCurator.delete(environment);
        assertEquals(0, environmentCurator.listAll().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void listForOwnerNullType(List<String> type) {
        Environment environment = environmentCurator.create(new Environment("env1", "Env 1", owner));
        List<Environment> envs = environmentCurator.listByType(owner, null, type);
        assertEquals(1, envs.size());
        assertEquals(envs.get(0), environment);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void listForOwnerByNameNullType(List<String> type) {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner));
        Environment env2 = environmentCurator.create(new Environment("env2", "Another Env", owner));

        List<Environment> envs = environmentCurator.listByType(owner, "Another Env", type);
        assertNotNull(envs);
        assertEquals(1, envs.size());
        assertEquals(env2, envs.get(0));
    }

    @Test
    public void listForOwnerByTypeByName() {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner)
            .setType("Test_Type"));
        Environment env2 = environmentCurator.create(new Environment("env2", "Another Env", owner));

        List<Environment> envs = environmentCurator.listByType(owner, "Another Env", List.of("Test-Type"));
        assertNotNull(envs);
        assertEquals(0, envs.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void listForOwnerByTypeNullName(String envName) {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner)
            .setType("Test_Type"));
        Environment env2 = environmentCurator.create(new Environment("env2", "Another Env", owner));
        List<Environment> envs = environmentCurator.listByType(owner, envName, List.of("Test_Type"));
        assertNotNull(envs);
        assertEquals(1, envs.size());
        assertEquals(env1, envs.get(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void listForOwnerNullName(String envName) {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner));
        Environment env2 = environmentCurator.create(new Environment("env2", "Env 2", owner)
            .setType("Test_Type"));
        List<Environment> envs = environmentCurator.listAllTypes(owner, envName);
        assertNotNull(envs);
        assertEquals(2, envs.size());
        assertThat(envs, contains(env1, env2));
    }

    @Test
    public void listForOwnerBadName() {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner));
        Environment env2 = environmentCurator.create(new Environment("env2", "Env 2", owner)
            .setType("Test_Type"));
        List<Environment> envs = environmentCurator.listAllTypes(owner, "Not An Environment");
        assertNotNull(envs);
        assertEquals(0, envs.size());
    }

    @Test
    public void listForOwnerBadType() {
        Environment env1 = environmentCurator.create(new Environment("env1", "Env 1", owner)
            .setType("Test_Type_1"));
        Environment env2 = environmentCurator.create(new Environment("env2", "Env 2", owner)
            .setType("Test_Type_2"));
        List<Environment> envs = environmentCurator.listByType(owner, null, List.of("Test_Type_Infinity"));
        assertNotNull(envs);
        assertEquals(0, envs.size());
    }

    @Test
    public void testDeleteEnvironmentForOwner() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Content content1 = this.createContent("c1", "c1");
        Content content2 = this.createContent("c2", "c2");
        Content content3 = this.createContent("c3", "c3");

        Environment environment1 = this.createEnvironment(owner1, "test_env-1", "test_env-1", null, null,
            List.of(content1));

        Environment environment2 = this.createEnvironment(owner1, "test_env-2", "test_env-2", null, null,
            List.of(content2));

        Environment environment3 = this.createEnvironment(owner2, "test_env-3", "test_env-3", null, null,
            List.of(content3));

        int output = this.environmentCurator.deleteEnvironmentsForOwner(owner1);
        assertEquals(2, output);

        this.environmentCurator.evict(environment1);
        this.environmentCurator.evict(environment2);
        this.environmentCurator.evict(environment3);
        environment1 = this.environmentCurator.get(environment1.getId());
        environment2 = this.environmentCurator.get(environment2.getId());
        environment3 = this.environmentCurator.get(environment3.getId());

        assertNull(environment1);
        assertNull(environment2);
        assertNotNull(environment3);

        Collection<EnvironmentContent> envcontent = environment3.getEnvironmentContent();
        assertEquals(1, envcontent.size());
        assertEquals(content3.getId(), envcontent.iterator().next().getContentId());
    }

    @Test
    public void testGetEnvironmentIdByName() {
        Owner owner = this.createOwner("owner1");
        Environment environment = this.createEnvironment(owner,
            "SomeId", "fooBar", null, null, null);
        String envName = this.environmentCurator.getEnvironmentIdByName(owner.getId(), environment.getName());

        assertEquals("SomeId", envName);

        String envNameWhenEnvIdNull = this.environmentCurator.getEnvironmentIdByName(owner.getId(), null);
        String envNameWhenOwnerIdNull = this.environmentCurator
            .getEnvironmentIdByName(null, environment.getName());

        assertNull(envNameWhenOwnerIdNull);
        assertNull(envNameWhenEnvIdNull);
    }

    @Test
    public void shouldListConsumersWithEnvironments() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);
        Content content1 = this.createContent("c1", "c1");
        Content content2 = this.createContent("c3", "c3");
        Environment environment1 = this.createEnvironment(
            owner1, "test_env-1", "test_env-1", null, null, List.of(content1));
        Environment environment2 = this.createEnvironment(
            owner1, "test_env-2", "test_env-2", null, null, List.of(content2));
        consumer1.addEnvironment(environment1);
        consumer1.addEnvironment(environment2);
        consumer2.addEnvironment(environment1);
        consumer2.addEnvironment(environment2);

        List<Consumer> output = this.environmentCurator.getEnvironmentConsumers(environment1.getId());
        assertEquals(2, output.size());
        for (Consumer consumer : output) {
            assertEquals(2, consumer.getEnvironmentIds().size());
        }
    }

    @Test
    public void shouldListConsumersWithTheirEnvironments() {
        Owner owner1 = this.createOwner("owner");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner1);
        Content content1 = this.createContent("c1", "c1");
        Content content2 = this.createContent("c2", "c2");
        Content content3 = this.createContent("c3", "c3");
        Environment environment1 = this.createEnvironment(
            owner1, "test_env-1", "test_env-1", null, null, List.of(content1));
        Environment environment2 = this.createEnvironment(
            owner1, "test_env-2", "test_env-2", null, null, List.of(content2));
        Environment environment3 = this.createEnvironment(
            owner1, "test_env-3", "test_env-3", null, null, List.of(content3));
        consumer1.addEnvironment(environment1);
        consumer1.addEnvironment(environment2);
        consumer1.addEnvironment(environment3);
        consumer2.addEnvironment(environment3);
        consumer2.addEnvironment(environment2);
        consumer2.addEnvironment(environment1);

        consumer1 = this.consumerCurator.saveOrUpdate(consumer1);
        consumer2 = this.consumerCurator.saveOrUpdate(consumer2);

        Map<String, List<String>> output = this.environmentCurator
            .findEnvironmentsOf(List.of(consumer1.getId(), consumer2.getId()));
        assertEquals(2, output.size());

        assertEquals(environment1.getId(), output.get(consumer1.getId()).get(0));
        assertEquals(environment2.getId(), output.get(consumer1.getId()).get(1));
        assertEquals(environment3.getId(), output.get(consumer1.getId()).get(2));

        assertEquals(environment3.getId(), output.get(consumer2.getId()).get(0));
        assertEquals(environment2.getId(), output.get(consumer2.getId()).get(1));
        assertEquals(environment1.getId(), output.get(consumer2.getId()).get(2));
    }

}

/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.Consumer;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

public class ContentAccessPayloadKeyBuilderTest {

    @Test
    public void testConstructorWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ContentAccessPayloadKeyBuilder(null);
        });
    }

    @Test
    public void testSetEnvironmentsWithNullEnvironments() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String initalKey = builder.build();

        // Should clear the environments and produce a different key value
        builder.setEnvironments(null);

        String newKey = builder.build();
        assertThat(newKey)
            .isNotNull()
            .isNotEqualTo(initalKey);
    }

    @Test
    public void testBuild() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String key = builder.build();

        assertThat(key)
            .isNotNull()
            .isNotBlank();
    }

    @Test
    public void testBuildWithEnvironmentsInDifferentOrder() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");

        Environment env1 = createEnvironment(owner);
        Environment env2 = createEnvironment(owner);
        Environment env3 = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(env1, env2, env3));

        String initial = builder.build();
        assertThat(initial)
            .isNotNull();

        builder.setEnvironments(List.of(env3, env2, env1));

        String actual = builder.build();
        assertThat(actual)
            .isNotNull()
            .isNotEqualTo(initial);
    }

    @Test
    public void testBuildWithSupportedArchitectureInDifferentOrder() {
        Owner owner = createOwner();

        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch-1, supp-arch-2, supp-arch-3");
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String initial = builder.build();
        assertThat(initial)
            .isNotNull();

        // Update the ordering of the supported architectures
        consumer
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch-3, supp-arch-2, supp-arch-1");

        builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String actual = builder.build();

        assertThat(actual)
            .isNotNull()
            .isEqualTo(initial);
    }

    @Test
    public void testBuildWithDifferentArch() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String intial = builder.build();

        consumer.setFact(Consumer.Facts.ARCHITECTURE, "x86");

        builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String actual = builder.build();

        assertThat(actual)
            .isNotNull()
            .isNotEqualTo(intial);
    }

    @Test
    public void testBuildWithDifferentSupportArches() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String intial = builder.build();

        consumer.setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch, supp-arch2");

        builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String actual = builder.build();

        assertThat(actual)
            .isNotNull()
            .isNotEqualTo(intial);
    }

    @Test
    public void testBuildWithDifferentEnvironments() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");
        Environment environment = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment));

        String intial = builder.build();

        Environment environment2 = createEnvironment(owner);
        builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(environment2));

        String actual = builder.build();

        assertThat(actual)
            .isNotNull()
            .isNotEqualTo(intial);
    }

    @Test
    public void testBuildWithDuplicateEnvironments() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setFact(Consumer.Facts.SUPPORTED_ARCHITECTURES, "supp-arch");

        Environment env1 = createEnvironment(owner);
        Environment env2 = createEnvironment(owner);
        Environment env3 = createEnvironment(owner);

        ContentAccessPayloadKeyBuilder builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(env1, env2, env3));

        String expected = builder.build();

        // The highest priority environment (first in the list) should be used and the duplicates should be
        // discarded.
        builder = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(List.of(env1, env2, env1, env3, env2, env3));

        String actual = builder.build();

        assertThat(actual)
            .isNotNull()
            .isEqualTo(expected);
    }

    private Owner createOwner() {
        String key = TestUtil.randomString("owner-key-");
        String name = TestUtil.randomString("owner-name-");
        return TestUtil.createOwner(key, name);
    }

    private Consumer createConsumer(Owner owner) {
        return new Consumer()
            .setId(TestUtil.randomString("consumer-id-"))
            .setName(TestUtil.randomString("consumer-name-"))
            .setOwner(owner);
    }

    private Environment createEnvironment(Owner owner) {
        return new Environment()
            .setId(TestUtil.randomString("env-id-"))
            .setName(TestUtil.randomString("env-"))
            .setOwner(owner)
            .setDescription("");
    }

}


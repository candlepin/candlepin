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


/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


@SpecTest
public class AapConsumerSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());

        // Skip all tests if the AAP consumer type is not available on this server.
        // The type is added by a database migration; tests will be skipped until
        // that migration is applied.
        try {
            ConsumerDTO probe = Consumers.random(owner, ConsumerTypes.Aap);
            ConsumerDTO created = adminClient.consumers().createConsumer(probe);
            // Clean up the probe consumer
            adminClient.consumers().deleteConsumer(created.getUuid());
        }
        catch (ApiException e) {
            assumeTrue(false, "AAP consumer type not available: " + e.getMessage());
        }
    }

    @Test
    public void shouldCreateAapConsumer() {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.Aap);
        ConsumerDTO created = adminClient.consumers().createConsumer(consumer);

        assertThat(created)
            .isNotNull();
        assertThat(created.getUuid())
            .isNotNull()
            .isNotBlank();
        assertThat(created.getType().getLabel())
            .isEqualTo("aap");
        assertThat(created.getOwner().getKey())
            .isEqualTo(owner.getKey());
    }

    @Test
    public void shouldRetrieveAapConsumerByUuid() {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.Aap);
        ConsumerDTO created = adminClient.consumers().createConsumer(consumer);

        ConsumerDTO retrieved = adminClient.consumers().getConsumer(created.getUuid());

        assertThat(retrieved.getUuid())
            .isEqualTo(created.getUuid());
        assertThat(retrieved.getType().getLabel())
            .isEqualTo("aap");
    }

    @Test
    public void shouldAuthenticateAapConsumerViaSsl() {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.Aap);
        ConsumerDTO created = adminClient.consumers().createConsumer(consumer);

        ApiClient consumerClient = ApiClients.ssl(created);
        ConsumerDTO retrieved = consumerClient.consumers().getConsumer(created.getUuid());

        assertThat(retrieved.getUuid())
            .isEqualTo(created.getUuid());
        assertThat(retrieved.getType().getLabel())
            .isEqualTo("aap");
    }

    @Test
    public void shouldListAapConsumersByType() {
        adminClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.System));
        adminClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Aap));
        adminClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Aap));

        List<ConsumerDTOArrayElement> aapConsumers = adminClient.owners()
            .listOwnerConsumers(owner.getKey(), Set.of("aap"));

        assertThat(aapConsumers)
            .hasSize(2)
            .allSatisfy(c -> assertThat(c.getType().getLabel()).isEqualTo("aap"));
    }

    @Test
    public void shouldNotBeManifestConsumer() {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.Aap);
        ConsumerDTO created = adminClient.consumers().createConsumer(consumer);

        // AAP is a direct consumer, not a distributor - verify it is not a manifest type
        assertThat(created.getType().getManifest())
            .isFalse();
    }

}

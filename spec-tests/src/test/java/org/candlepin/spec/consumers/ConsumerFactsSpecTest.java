/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

@SpecTest
public class ConsumerFactsSpecTest {

    private static ApiClient admin;
    private OwnerDTO owner;
    private ApiClient ownerClient;

    @BeforeAll
    static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        this.ownerClient = ApiClients.basic(UserUtil.createAdminUser(admin, this.owner));
    }

    @Test
    public void shouldAllowAddingFacts() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.putFactsItem(Facts.MemoryTotal.key(), "100");
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .containsEntry(Facts.MemoryTotal.key(), "100");
        assertThat(updatedConsumer.getInstalledProducts())
            .map(ConsumerInstalledProductDTO::getProductName)
            .containsExactly("Installed");
    }

    @Test
    public void shouldAllowUpdatingFacts() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.putFactsItem(Facts.OperatingSystem.key(), "BSD");
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .containsEntry(Facts.OperatingSystem.key(), "BSD");
    }

    @Test
    public void shouldAllowRemovingFacts() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.getFacts().remove(Facts.OperatingSystem.key());
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .doesNotContainKey(Facts.OperatingSystem.key());
    }

    @Test
    public void shouldUpdateConsumerUpdatedDateWhenFactsAreUpdated() throws InterruptedException {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialDate = consumer.getUpdated();

        // MySQL drops millis, we need to wait a bit longer
        Thread.sleep(1000);
        consumer.getFacts().remove(Facts.OperatingSystem.key());
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        OffsetDateTime updatedDate = updatedConsumer.getUpdated();

        assertThat(updatedDate)
            .isAfter(initialDate);
    }

    @Test
    public void shouldAllowRemovingAllFacts() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.getFacts().clear();
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .isEmpty();
    }

    @Test
    @SuppressWarnings("indentation")
    public void shouldAllowFactKeysAndValuesOfLength255() {
        String key = "a".repeat(255);
        String value = "b".repeat(255);
        ConsumerDTO consumer = ownerClient.consumers()
            .createConsumer(Consumers.random(this.owner)
                .putFactsItem(key, value)
            );

        assertThat(consumer.getFacts())
            .containsEntry(key, value);
    }

    @Test
    public void shouldTruncateLongFactKeys() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.putFactsItem("a".repeat(256), "some_value");
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .containsKey("a".repeat(252) + "...");
    }

    @Test
    public void shouldTruncateLongFactValues() {
        ConsumerDTO consumer = createConsumer();
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer.putFactsItem(Facts.Arch.key(), "a".repeat(256));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        ConsumerDTO updatedConsumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        assertThat(updatedConsumer.getFacts())
            .containsEntry(Facts.Arch.key(), "a".repeat(252) + "...");
    }

    private ConsumerDTO createConsumer() {
        ConsumerInstalledProductDTO installedProduct = new ConsumerInstalledProductDTO()
            .productId("installedproduct")
            .productName("Installed");

        return ownerClient.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.Arch.key(), "i686")
            .putFactsItem(Facts.OperatingSystem.key(), "Linux")
            .addInstalledProductsItem(installedProduct)
        );
    }

}

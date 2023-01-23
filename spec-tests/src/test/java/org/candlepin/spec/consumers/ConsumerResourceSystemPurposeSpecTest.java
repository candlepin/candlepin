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
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;


@SpecTest
public class ConsumerResourceSystemPurposeSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldLetAConsumerAndSetServiceLevel() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        String serviceLevel = "test_service_level";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).serviceLevel(serviceLevel));
        assertThat(consumer)
            .returns(serviceLevel, ConsumerDTO::getServiceLevel);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(serviceLevel, ConsumerDTO::getServiceLevel);
    }

    @Test
    public void shouldLetAConsumerAndSetServiceType() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        String serviceType = "test_service_type";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).serviceType(serviceType));
        assertThat(consumer)
            .returns(serviceType, ConsumerDTO::getServiceType);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(serviceType, ConsumerDTO::getServiceType);
    }

    @Test
    public void shouldLetAConsumerRegisterAndSetSystemPurposeRole() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        String testRole = "test_role";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).role(testRole));
        assertThat(consumer)
            .returns(testRole, ConsumerDTO::getRole);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(testRole, ConsumerDTO::getRole);
    }

    @Test
    public void shouldLetAConsumerRegisterAndSetSystemPurposeUsage() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        String testUsage = "test_usage";
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).usage(testUsage));
        assertThat(consumer)
            .returns(testUsage, ConsumerDTO::getUsage);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(testUsage, ConsumerDTO::getUsage);
    }

    @Test
    public void shouldLetAConsumerRegisterAndSetSystemPurposeAddons() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));

        Set<String> testAddons = Set.of("test_addon-1", "test_addon-2", "test_addon-3");
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).addOns(testAddons));
        assertThat(consumer.getAddOns())
            .hasSize(testAddons.size())
            .containsAll(testAddons);
        // reload to be sure it was persisted
        consumer = someUser.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getAddOns())
            .hasSize(testAddons.size())
            .containsAll(testAddons);
    }

    @Test
    public void shouldAllowAConsumerToUpdateTheirServiceLevel() {
        String serviceLevel = StringUtil.random("vIp");
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel));
        product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(), product1);
        ProductDTO product2 = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Layered"))
            .addAttributesItem(ProductAttributes.SupportLevelExempt.withValue("true"));
        product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(), product2);
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1).quantity(1L));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getServiceLevel()).isEmpty();

        userClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .serviceLevel(serviceLevel));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel, ConsumerDTO::getServiceLevel);

        // Null update shouldn't modify the setting
        userClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO());
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel, ConsumerDTO::getServiceLevel);

        // Make sure we can reset to empty for service level
        userClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().serviceLevel(""));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getServiceLevel()).isEmpty();

        // The service level should be case-insensitive
        assertThat(serviceLevel.equals(serviceLevel.toLowerCase())).isFalse();
        userClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().serviceLevel(serviceLevel.toLowerCase()));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel.toLowerCase(), ConsumerDTO::getServiceLevel);
    }

    @Test
    public void shouldNotListServiceLevelsFromExpiredPools() {
        String serviceLevel = "Expired";
        ProductDTO product = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel));
        product = adminClient.ownerProducts().createProductByOwner(owner.getKey(), product);
        OffsetDateTime now = OffsetDateTime.now();
        PoolDTO pool = Pools.random(product)
            .startDate(now.minusDays(2L))
            .endDate(now.minusDays(1L));
        adminClient.owners().createPool(owner.getKey(), pool);

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumerClient.owners().ownerServiceLevels(owner.getKey(), null)).isEmpty();
    }
}

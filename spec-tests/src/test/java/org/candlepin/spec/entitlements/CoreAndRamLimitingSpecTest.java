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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.ComplianceAssert.assertThatCompliance;

import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class CoreAndRamLimitingSpecTest {

    private static final String STACKING_ID = StringUtil.random("compliance");
    private static ApiClient admin;
    private OwnerDTO owner;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldBeValidWhenCoresRamAndSocketsAreCovered() {
        ProductDTO product = createProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "4")
            .putFactsItem(Facts.MemoryTotal.key(), "8000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerCoreOnlyNotCovered() {
        ProductDTO product = createProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "8")
            .putFactsItem(Facts.MemoryTotal.key(), "8000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerRamOnlyNotCovered() {
        ProductDTO product = createProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "4")
            .putFactsItem(Facts.MemoryTotal.key(), "16000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerSocketsOnlyNotCovered() {
        ProductDTO product = createProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "8")
            .putFactsItem(Facts.CoresPerSocket.key(), "2")
            .putFactsItem(Facts.MemoryTotal.key(), "8000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBeValidWhenConsumerCoreRequiresExtraEntitlement() {
        ProductDTO product = createStackingProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "16")
            .putFactsItem(Facts.MemoryTotal.key(), "8000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), product);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(4);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product);
    }

    @Test
    public void shouldBeValidWhenConsumerSocketRequiresExtraEntitlement() {
        ProductDTO product = createStackingProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "10")
            .putFactsItem(Facts.MemoryTotal.key(), "8000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), product);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(3);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerRamExceedsEntitlement() {
        ProductDTO product = createStackingProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "4")
            .putFactsItem(Facts.MemoryTotal.key(), "16000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product);
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldAllowConsumerToBeCompliantForSocketAndCoreQuantityAcrossStackedPools() {
        ProductDTO product1 = createStackingProduct();
        ProductDTO product2 = createStackingProduct();
        PoolDTO pool1 = admin.owners().createPool(this.owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = admin.owners().createPool(this.owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CpuSockets.key(), "4")
            .putFactsItem(Facts.CoresPerSocket.key(), "8")
            .putFactsItem(Facts.MemoryTotal.key(), "16000000")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, product1);
        consumerClient.consumers().bindPoolSync(consumer.getUuid(), pool1.getId(), 2);
        consumerClient.consumers().bindPoolSync(consumer.getUuid(), pool2.getId(), 2);

        List<EntitlementDTO> entitlements = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(entitlements)
            .hasSize(2)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product1);
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer, ProductDTO... products) {
        Set<ConsumerInstalledProductDTO> installedProducts = Arrays.stream(products)
            .map(Products::toInstalled)
            .collect(Collectors.toSet());

        updateInstalledProducts(client, consumer, installedProducts);
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer,
        Set<ConsumerInstalledProductDTO> hostInstalledProducts) {
        client.consumers()
            .updateConsumer(consumer.getUuid(), consumer.installedProducts(hostInstalledProducts));
    }

    private ProductDTO createProduct() {
        return admin.ownerProducts().createProduct(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("1.2"),
            ProductAttributes.Ram.withValue("8"),
            ProductAttributes.Sockets.withValue("4"),
            ProductAttributes.Cores.withValue("16"),
            ProductAttributes.WarningPeriod.withValue("15"),
            ProductAttributes.ManagementEnabled.withValue("true"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent")
        ));
    }

    private ProductDTO createStackingProduct() {
        return admin.ownerProducts().createProduct(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("1.2"),
            ProductAttributes.Ram.withValue("8"),
            ProductAttributes.Sockets.withValue("4"),
            ProductAttributes.Cores.withValue("16"),
            ProductAttributes.WarningPeriod.withValue("15"),
            ProductAttributes.ManagementEnabled.withValue("true"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent"),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.StackingId.withValue(STACKING_ID)
        ));
    }

}

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


@SpecTest
@SuppressWarnings("indentation")
public class CoreSpecTest {

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
    public void shouldUseCoresIfVCPUIsNotAvailable() {
        ProductDTO product = createSocketProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "1")
                .putFactsItem(Facts.CpuSockets.key(), "8")
                .putFactsItem(Facts.VirtIsGuest.key(), "true"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers().autoBindSync(consumer.getUuid());

        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(4);
    }

    @Test
    public void shouldConsumeCoreEntitlementIfRequestingV32Certificate() {
        ProductDTO product = createCoreProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), product);

        assertThat(entitlements)
            .hasSize(1);
    }

    @Test
    public void shouldBeValidWhenConsumerCoreIsCovered() {
        ProductDTO product = createCoreProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        consumerClient.consumers().bindProduct(consumer.getUuid(), product);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerCoreIsNotCovered() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "32"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerCoreCoveredButNotSockets() {
        ProductDTO product = createCoreAndSocketProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "2")
                .putFactsItem(Facts.CpuSockets.key(), "8"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBePartialWhenConsumerSocketsCoveredButNotCore() {
        ProductDTO product = createCoreAndSocketProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "8")
                .putFactsItem(Facts.CpuSockets.key(), "4"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasPartiallyCompliantProducts(product);
    }

    @Test
    public void shouldBeValidWhenBothCoreAndSocketsAreCovered() {
        ProductDTO product = createCoreAndSocketProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "4")
                .putFactsItem(Facts.CpuSockets.key(), "4"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), null);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isValid()
            .isCompliant()
            .hasCompliantProducts(product);
    }

    @Test
    public void shouldHealWhenCoreLimited() {
        ProductDTO product = createCoreProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "8"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers().autoBindSync(consumer.getUuid());

        assertThat(entitlements)
            .hasSize(1);
    }

    @Test
    public void shouldNotHealWhenSystemCoreIsNotCovered() {
        ProductDTO product = createCoreProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "12"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers().autoBindSync(consumer.getUuid());

        assertThat(entitlements)
            .isEmpty();
    }

    @Test
    public void shouldHealWhenBothCoreAndSocketLimited() {
        ProductDTO product = createCoreAndSocketProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(
            Consumers.random(this.owner)
                .putFactsItem(Facts.CertificateVersion.key(), "3.2")
                .putFactsItem(Facts.CoresPerSocket.key(), "4")
                .putFactsItem(Facts.CpuSockets.key(), "4"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers().autoBindSync(consumer.getUuid());

        assertThat(entitlements)
            .hasSize(1);
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer,
        Set<ConsumerInstalledProductDTO> hostInstalledProducts) {
        client.consumers()
            .updateConsumer(consumer.getUuid(), consumer.installedProducts(hostInstalledProducts));
    }

    private ProductDTO createCoreProduct() {
        return admin.ownerProducts()
            .createProductByOwner(this.owner.getKey(), Products.withAttributes(
                ProductAttributes.Version.withValue("6.4"),
                ProductAttributes.Cores.withValue("8"),
                ProductAttributes.WarningPeriod.withValue("15"),
                ProductAttributes.ManagementEnabled.withValue("true"),
                ProductAttributes.SupportLevel.withValue("standard"),
                ProductAttributes.SupportType.withValue("excellent")
            ));
    }

    private ProductDTO createSocketProduct() {
        return admin.ownerProducts()
            .createProductByOwner(this.owner.getKey(), Products.withAttributes(
                ProductAttributes.Version.withValue("6.4"),
                ProductAttributes.Cores.withValue("2"),
                ProductAttributes.Sockets.withValue("1"),
                ProductAttributes.InstanceMultiplier.withValue("2"),
                ProductAttributes.StackingId.withValue("prod3"),
                ProductAttributes.MultiEntitlement.withValue("yes"),
                ProductAttributes.WarningPeriod.withValue("15"),
                ProductAttributes.ManagementEnabled.withValue("true"),
                ProductAttributes.SupportLevel.withValue("standard"),
                ProductAttributes.SupportType.withValue("excellent")
            ));
    }

    private ProductDTO createCoreAndSocketProduct() {
        return admin.ownerProducts()
            .createProductByOwner(this.owner.getKey(), Products.withAttributes(
                ProductAttributes.Version.withValue("1.2"),
                ProductAttributes.Cores.withValue("16"),
                ProductAttributes.Sockets.withValue("4"),
                ProductAttributes.WarningPeriod.withValue("15"),
                ProductAttributes.ManagementEnabled.withValue("true"),
                ProductAttributes.SupportLevel.withValue("standard"),
                ProductAttributes.SupportType.withValue("excellent")
            ));
    }

}

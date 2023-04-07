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
import org.candlepin.spec.bootstrap.assertions.ReasonAttributes;
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

import java.util.List;
import java.util.Set;


@SpecTest
@SuppressWarnings("indentation")
public class StackingComplianceReasonsSpecTest {

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
    public void shouldReportStackDoesNotCoverRam() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216")
            .putFactsItem(Facts.CpuSockets.key(), "4"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasRamReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportPartialForStackThatDoesNotCoverRamAndHasNoInstalledProducts() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216")
            .putFactsItem(Facts.CpuSockets.key(), "4"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasRamReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverSockets() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CpuSockets.key(), "6"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasSocketsReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("4"),
                ReasonAttributes.Has.withValue("6"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverCores() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CoresPerSocket.key(), "30")
            .putFactsItem(Facts.CpuSockets.key(), "1"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasCoresReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("20"),
                ReasonAttributes.Has.withValue("30"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverVcpu() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CoresPerSocket.key(), "30")
            .putFactsItem(Facts.CpuSockets.key(), "1")
            .putFactsItem(Facts.VirtIsGuest.key(), "true")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasVcpuReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("16"),
                ReasonAttributes.Has.withValue("30"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverArch() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "ppc64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CoresPerSocket.key(), "10")
            .putFactsItem(Facts.CpuSockets.key(), "1")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasArchReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("x86_64"),
                ReasonAttributes.Has.withValue("ppc64"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverAllInstalledProducts() {
        ProductDTO product = createStackableProduct();
        ProductDTO notCoveredProduct = createNotCoveredProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CpuSockets.key(), "4")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(
            Products.toInstalled(product),
            Products.toInstalled(notCoveredProduct)
        ));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isInvalid()
            .isNotCompliant()
            .hasReasons(1)
            .hasNotCoveredReason(
                ReasonAttributes.ProductId.withValue(notCoveredProduct.getId()),
                ReasonAttributes.Name.withValue(notCoveredProduct.getName())
            );
    }

    @Test
    public void shouldReportStackDoesNotCoverMultipleAttributes() {
        ProductDTO product = createStackableProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "ppc64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216")
            .putFactsItem(Facts.CoresPerSocket.key(), "12")
            .putFactsItem(Facts.CpuSockets.key(), "20")
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 2);
        assertThat(entitlements)
            .hasSize(1)
            .map(EntitlementDTO::getQuantity)
            .containsOnly(2);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(4)
            .hasArchReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("x86_64"),
                ReasonAttributes.Has.withValue("ppc64"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasRamReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasSocketsReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("4"),
                ReasonAttributes.Has.withValue("20"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasCoresReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("20"),
                ReasonAttributes.Has.withValue("240"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer,
        Set<ConsumerInstalledProductDTO> hostInstalledProducts) {
        client.consumers()
            .updateConsumer(consumer.getUuid(), consumer.installedProducts(hostInstalledProducts));
    }

    private ProductDTO createStackableProduct() {
        return admin.ownerProducts().createProductByOwner(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("1.2"),
            ProductAttributes.Ram.withValue("4"),
            ProductAttributes.Sockets.withValue("2"),
            ProductAttributes.Cores.withValue("10"),
            ProductAttributes.Vcpu.withValue("8"),
            ProductAttributes.Arch.withValue("x86_64"),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent"),
            ProductAttributes.StackingId.withValue(STACKING_ID)
        ));
    }

    private ProductDTO createNotCoveredProduct() {
        return admin.ownerProducts().createProductByOwner(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("6.4"),
            ProductAttributes.Sockets.withValue("2"),
            ProductAttributes.WarningPeriod.withValue("15"),
            ProductAttributes.ManagementEnabled.withValue("true"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent")
        ));
    }

}

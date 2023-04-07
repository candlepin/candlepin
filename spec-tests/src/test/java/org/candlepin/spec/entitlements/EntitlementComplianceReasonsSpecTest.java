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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;


@SpecTest
@SuppressWarnings("indentation")
public class EntitlementComplianceReasonsSpecTest {

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
    public void shouldBePartiallyCompliantWhenUsingCoresIfVcpuIsNotAvailable() {
        ProductDTO product = createSocketProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.CoresPerSocket.key(), "1")
            .putFactsItem(Facts.CpuSockets.key(), "8")
            .putFactsItem(Facts.VirtIsGuest.key(), "true"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).hasSize(1);

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasVcpuReason(
                ReasonAttributes.StackId.withValue(STACKING_ID),
                ReasonAttributes.Covered.withValue("2"),
                ReasonAttributes.Has.withValue("8"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportNotCoveredProducts() {
        ProductDTO product = createCoreProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isInvalid()
            .isNotCompliant()
            .hasReasons(1)
            .hasNotCoveredReason(
                ReasonAttributes.ProductId.withValue(product.getId()),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportRamNotCoveredButNoInstalledProduct() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasRamReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportsNotCoveredRam() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasRamReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldBeInvalidForComplianceAfterEndDate() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.randomUpstream(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CpuSockets.key(), "2"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        OffsetDateTime afterEndDate = entitlements.stream().findFirst()
            .map(ent -> ent.getEndDate().plusDays(2))
            .orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid(), afterEndDate);
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isInvalid()
            .isNotCompliant()
            .hasReasons(1)
            .hasNotCoveredReason(
                ReasonAttributes.ProductId.withValue(product.getId()),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportSocketsNotCovered() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.randomUpstream(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CpuSockets.key(), "12"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasSocketsReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("2"),
                ReasonAttributes.Has.withValue("12"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportCoresNotCovered() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.randomUpstream(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "x86_64")
            .putFactsItem(Facts.MemoryTotal.key(), "4194304")
            .putFactsItem(Facts.CoresPerSocket.key(), "12")
            .putFactsItem(Facts.CpuSockets.key(), "2"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasCoresReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("22"),
                ReasonAttributes.Has.withValue("24"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportArchNotCovered() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.randomUpstream(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "ppc64")
            .putFactsItem(Facts.CpuSockets.key(), "2"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasReasons(1)
            .hasArchReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("x86_64"),
                ReasonAttributes.Has.withValue("ppc64"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    @Test
    public void shouldReportMultipleReasons() {
        ProductDTO product = createCoreProduct();
        PoolDTO pool = admin.owners().createPool(this.owner.getKey(), Pools.randomUpstream(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.2")
            .putFactsItem(Facts.Arch.key(), "ppc64")
            .putFactsItem(Facts.MemoryTotal.key(), "16777216")
            .putFactsItem(Facts.CoresPerSocket.key(), "12")
            .putFactsItem(Facts.CpuSockets.key(), "20"));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        updateInstalledProducts(consumerClient, consumer, Set.of(Products.toInstalled(product)));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO entitlement = entitlements.stream().findFirst().orElseThrow();

        ComplianceStatusDTO complianceStatus = consumerClient.consumers()
            .getComplianceStatus(consumer.getUuid());
        assertThatCompliance(complianceStatus)
            .isNotNull()
            .isPartial()
            .isNotCompliant()
            .hasArchReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("x86_64"),
                ReasonAttributes.Has.withValue("ppc64"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasRamReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("8"),
                ReasonAttributes.Has.withValue("16"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasSocketsReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("2"),
                ReasonAttributes.Has.withValue("20"),
                ReasonAttributes.Name.withValue(product.getName())
            )
            .hasCoresReason(
                ReasonAttributes.EntitlementId.withValue(entitlement.getId()),
                ReasonAttributes.Covered.withValue("22"),
                ReasonAttributes.Has.withValue("240"),
                ReasonAttributes.Name.withValue(product.getName())
            );
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer,
        Set<ConsumerInstalledProductDTO> hostInstalledProducts) {
        client.consumers()
            .updateConsumer(consumer.getUuid(), consumer.installedProducts(hostInstalledProducts));
    }

    private ProductDTO createCoreProduct() {
        return admin.ownerProducts().createProductByOwner(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("6.4"),
            ProductAttributes.Ram.withValue("8"),
            ProductAttributes.Sockets.withValue("2"),
            ProductAttributes.Cores.withValue("22"),
            ProductAttributes.Arch.withValue("x86_64"),
            ProductAttributes.WarningPeriod.withValue("15"),
            ProductAttributes.ManagementEnabled.withValue("true"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent")
        ));
    }

    private ProductDTO createSocketProduct() {
        return admin.ownerProducts().createProductByOwner(this.owner.getKey(), Products.withAttributes(
            ProductAttributes.Version.withValue("6.4"),
            ProductAttributes.Cores.withValue("2"),
            ProductAttributes.Sockets.withValue("1"),
            ProductAttributes.InstanceMultiplier.withValue("2"),
            ProductAttributes.StackingId.withValue(STACKING_ID),
            ProductAttributes.MultiEntitlement.withValue("yes"),
            ProductAttributes.WarningPeriod.withValue("15"),
            ProductAttributes.ManagementEnabled.withValue("true"),
            ProductAttributes.SupportLevel.withValue("standard"),
            ProductAttributes.SupportType.withValue("excellent")
        ));
    }

}

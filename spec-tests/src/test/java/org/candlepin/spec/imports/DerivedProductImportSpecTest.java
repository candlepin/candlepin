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
package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@SpecTest
@OnlyInStandalone
public class DerivedProductImportSpecTest {

    private static final String EXPECTED_CONTENT_URL = "/path/to/arch/specific/content";

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldRetrieveSubscriptonCertificateFromDerivedPoolEntitlement() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        OwnerDTO distOwner = ownerApi.createOwner(Owners.random());
        ApiClient distUserClient = ApiClients.basic(UserUtil.createUser(client, distOwner));

        ProductDTO derivedProduct = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .attributes(List.of(
            ProductAttributes.Cores.withValue("6"),
            ProductAttributes.Sockets.withValue("8"))));

        ProductDTO stackedDatacenterProduct = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .attributes(List.of(
            ProductAttributes.VirtualLimit.withValue("unlimited"),
            ProductAttributes.StackingId.withValue("mixed-stack"),
            ProductAttributes.Sockets.withValue("2"),
            ProductAttributes.MultiEntitlement.withValue("yes")))
            .derivedProduct(derivedProduct));

        PoolDTO datacenterPool = ownerApi.createPool(owner.getKey(),
            Pools.random(stackedDatacenterProduct)
            .contractNumber("222")
            .accountNumber("")
            .orderNumber(""));

        // create the distributor consumer
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .facts(Map.of("distributorVersion", "sam-1.3")));
        ApiClient distributorClient = ApiClients.ssl(distributor);
        String distVersion = StringUtil.random("version");
        client.distributorVersions().create(new DistributorVersionDTO()
            .name(distVersion)
            .displayName("SAM")
            .capabilities(Set.of(new DistributorVersionCapabilityDTO().name("cert_v3"),
                new DistributorVersionCapabilityDTO().name("derived_product"))));
        distributorClient.consumers().updateConsumer(distributor.getUuid(),
            new ConsumerDTO().facts(Map.of("distributor_version", distVersion)));
        // entitlements from data center pool
        distributorClient.consumers().bindPool(distributor.getUuid(), datacenterPool.getId(), 10);

        // make manifest
        File export = client.consumers().exportData(distributor.getUuid(), null, null, null);
        export.deleteOnExit();
        // remove client at 'host'
        userClient.consumers().deleteConsumer(distributor.getUuid());
        client.pools().deletePool(datacenterPool.getId());
        // import to make org at 'distributor'
        AsyncJobStatusDTO importJob = client.owners().importManifestAsync(distOwner.getKey(), List.of(), export);
        assertThatJob(client.jobs().waitForJob(importJob)).isFinished();

        List<PoolDTO> pools = ownerApi.listOwnerPoolsByProduct(distOwner.getKey(),
            stackedDatacenterProduct.getId());
        assertThat(pools).singleElement();
        PoolDTO distPool = pools.get(0);

        // make host client to get entitlement from distributor
        ConsumerDTO distConsumer1 = distUserClient.consumers().createConsumer(Consumers.random(distOwner));
        ApiClient distConsumerClient1 = ApiClients.ssl(distConsumer1);
        String guestUuid = StringUtil.random("uuid");
        distConsumerClient1.consumers().updateConsumer(distConsumer1.getUuid(),
            new ConsumerDTO().guestIds(List.of(new GuestIdDTO().guestId(guestUuid))));

        // spawn pool for derived product
        distConsumerClient1.consumers().bindPool(distConsumer1.getUuid(), distPool.getId(), 1);
        // make guest client
        ConsumerDTO distConsumer2 = distUserClient.consumers().createConsumer(
            Consumers.random(distOwner)
            .facts(Map.of("virt.uuid", guestUuid, "virt.is_guest", "true")));
        ApiClient distConsumerClient2 = ApiClients.ssl(distConsumer2);
        // entitle from derived pool
        Optional<PoolDTO> poolToBind = ownerApi.listOwnerPools(distOwner.getKey()).stream()
            .filter(x -> "STACK_DERIVED".equals(x.getType()))
            .findFirst();
        assertThat(poolToBind).isPresent();
        EntitlementDTO distEnt = ApiClient.MAPPER.convertValue(distConsumerClient2.consumers().bindPool(
            distConsumer2.getUuid(), poolToBind.get().getId(), 1).get(0), EntitlementDTO.class);

        // use entitlement to get subscription cert back at primary pool
        assertThat(client.entitlements().getUpstreamCert(distEnt.getId()))
            .startsWith("-----BEGIN CERTIFICATE-----");
    }
}

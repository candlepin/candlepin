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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.EntitlementsApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;


@SpecTest
public class EntitlementResourceFilteringSpecTest {

    private static ConsumerClient consumerClient;
    private static EntitlementsApi entitlementsApi;
    private static ConsumerDTO consumer;
    private static ProductDTO monitoring;

    @BeforeAll
    public static void beforeAll() throws ApiException {
        ApiClient client = ApiClients.admin();
        OwnerClient ownerClient = client.owners();
        OwnerProductApi ownerProductApi = client.ownerProducts();
        entitlementsApi = client.entitlements();

        OwnerDTO owner = ownerClient.createOwner(Owners.random());

        monitoring = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("monitoring"))
            .attributes(List.of(new AttributeDTO().name("variant").value("Satellite Starter Pack"))));
        ProductDTO virtual = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("virtualization")));

        // entitle owner for the virt and monitoring products
        ownerClient.createPool(owner.getKey(), Pools.random(monitoring));
        ownerClient.createPool(owner.getKey(), Pools.random(virtual));

        consumer = client.consumers().createConsumer(Consumers.random(owner));
        consumerClient = ApiClients.ssl(consumer).consumers();

        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());
    }

    @Test
    public void shouldFilterEntitlementsWithMatches() {
        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(
            consumer.getUuid(), "virtualization", null, null, null, null, null);
        assertThat(ents).hasSize(1);
    }

    @Test
    public void shouldFilterEntitlementsByProductAttribute() {
        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(consumer.getUuid(), null,
            List.of("variant:Satellite Starter Pack"), null, null, null, null);

        assertThat(ents)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .extracting(EntitlementDTO::getPool)
            .isNotNull()
            .extracting(PoolDTO::getProductAttributes, as(collection(AttributeDTO.class)))
            .singleElement()
            .returns("Satellite Starter Pack", AttributeDTO::getValue);
    }

    @Test
    public void shouldFilterConsumerEntitlementsByProductAttribute() {
        List<EntitlementDTO> ents = consumerClient.listEntitlements(
            consumer.getUuid(), null, null, null, null, null, null, null);
        assertThat(ents).hasSize(2);

        ents = consumerClient.listEntitlements(consumer.getUuid(),
            null, null, List.of("variant:Satellite Starter Pack"), null, null, null, null);
        assertThat(ents).hasSize(1);

        assertEquals("Satellite Starter Pack",
            ents.get(0).getPool().getProductAttributes().get(0).getValue());
    }

    @Test
    public void shouldAllowFilteringCertsBySerial() {
        List<EntitlementDTO> ents = consumerClient.listEntitlements(
            consumer.getUuid(), monitoring.getId(), null, null, null, null, null, null);
        assertThat(ents).hasSize(1);

        List<CertificateSerialDTO> certSerials = ents.stream()
            .flatMap(e -> e.getCertificates().stream().map(CertificateDTO::getSerial))
            .collect(Collectors.toList());
        assertThat(certSerials).hasSize(1);

        assertThat(consumerClient.fetchCertificates(
            consumer.getUuid(), certSerials.get(0).getSerial().toString())).hasSize(1);
    }

}

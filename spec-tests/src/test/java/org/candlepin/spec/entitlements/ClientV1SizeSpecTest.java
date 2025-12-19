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
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@SpecTest
public class ClientV1SizeSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    @Test
    public void shouldRegenTheV1EntitlementCertBasedOnContentSetLimit() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<ContentDTO> batchContent = createBatchContent(owner, 200);
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Version.withValue("6.4"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.ManagementEnabled.withValue("true"))
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("false"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        product1 = ownerProductApi.addContentToProduct(owner.getKey(), product1.getId(),
            batchContent.get(0).getId(), true);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO system = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"));
        system = userClient.consumers().createConsumer(system);
        ApiClient systemClient = ApiClients.ssl(system);
        JsonNode ent1 = systemClient.consumers().bindProduct(system.getUuid(), product1);
        String ent1Id = ent1.get(0).get("id").asText();

        Map<String, Boolean> contentIds = batchContent.stream().skip(1).limit(9)
            .collect(Collectors.toMap(ContentDTO::getId, x -> true));
        final String product1Id = product1.getId();
        product1 = ownerProductApi.addContentsToProduct(owner.getKey(), product1Id, contentIds);
        AsyncJobStatusDTO status = client.entitlements()
            .regenerateEntitlementCertificatesForProduct(product1Id, true);
        status = client.jobs().waitForJob(status.getId());
        assertThatJob(status).isFinished();

        EntitlementDTO ent2 = systemClient.entitlements().getEntitlement(ent1Id);
        assertThat(ent2.getCertificates().iterator().next().getSerial().getSerial())
            .isNotEqualTo(ent1.get(0).get("certificates").get(0).get("serial").get("id").asLong());
        assertThat(client.crl().getCurrentCrl()).contains(
            ent1.get(0).get("certificates").get(0).get("serial").get("id").asLong());

        // the content change to > 185 will not cause a regeneration. It will also not throw an error.
        contentIds = batchContent.stream().skip(10).limit(190)
            .collect(Collectors.toMap(ContentDTO::getId, x -> true));
        product1 = ownerProductApi.addContentsToProduct(owner.getKey(), product1Id, contentIds);
        status = client.entitlements().regenerateEntitlementCertificatesForProduct(product1Id, true);
        status = client.jobs().waitForJob(status.getId());
        assertThatJob(status).isFinished();

        EntitlementDTO ent3 = systemClient.entitlements().getEntitlement(ent1Id);
        assertThat(ent3.getCertificates().iterator().next().getSerial().getSerial())
            .isEqualTo(ent2.getCertificates().iterator().next().getSerial().getSerial());
        assertThat(client.crl().getCurrentCrl()).doesNotContain(
            ent2.getCertificates().iterator().next().getSerial().getSerial());

        // updating the client will allow the cert to be regenerated
        systemClient.consumers().updateConsumer(system.getUuid(), new ConsumerDTO()
            .facts(Map.of("system.certificate_version", "3.2")));
        EntitlementDTO ent4 = systemClient.entitlements().getEntitlement(ent1Id);
        assertThat(ent4.getCertificates().iterator().next().getSerial().getSerial())
            .isNotEqualTo(ent2.getCertificates().iterator().next().getSerial().getSerial());
        assertThat(client.crl().getCurrentCrl()).contains(
            ent2.getCertificates().iterator().next().getSerial().getSerial());
    }

    @Test
    public void shouldNotAllowAnWExcessiveContentSetToBlockOthers() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<ContentDTO> batchContent = createBatchContent(owner, 200);
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Version.withValue("6.4"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.ManagementEnabled.withValue("true"))
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("false"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        final String product1Id = product1.getId();
        product1 = ownerProductApi.addContentToProduct(owner.getKey(), product1Id,
            batchContent.get(0).getId(), true);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));

        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Version.withValue("6.4"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.ManagementEnabled.withValue("true"))
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("false"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        final String product2Id = product2.getId();
        product2 = ownerProductApi.addContentToProduct(owner.getKey(), product2Id,
            batchContent.get(0).getId(), true);
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO system = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"));
        system = userClient.consumers().createConsumer(system);
        ApiClient systemClient = ApiClients.ssl(system);

        JsonNode ent1 = systemClient.consumers().bindProduct(system.getUuid(), product1);
        JsonNode ent2 = systemClient.consumers().bindProduct(system.getUuid(), product2);

        Map<String, Boolean> contentIds = batchContent.stream().skip(1).limit(199)
            .collect(Collectors.toMap(ContentDTO::getId, x -> true));

        product1 = ownerProductApi.addContentsToProduct(owner.getKey(), product1Id, contentIds);
        product2 = ownerProductApi.addContentToProduct(owner.getKey(), product2Id,
            batchContent.get(1).getId(), true);
        AsyncJobStatusDTO status = client.entitlements()
            .regenerateEntitlementCertificatesForProduct(product1Id, true);
        status = client.jobs().waitForJob(status.getId());
        assertThatJob(status).isFinished();
        status = client.entitlements().regenerateEntitlementCertificatesForProduct(product2Id, true);
        status = client.jobs().waitForJob(status.getId());
        assertThatJob(status).isFinished();

        EntitlementDTO newEnt1 = systemClient.entitlements().getEntitlement(ent1.get(0).get("id").asText());
        EntitlementDTO newEnt2 = systemClient.entitlements().getEntitlement(ent2.get(0).get("id").asText());

        assertThat(newEnt1.getCertificates().iterator().next().getSerial().getSerial())
            .isEqualTo(ent1.get(0).get("certificates").get(0).get("serial").get("id").asLong());
        assertThat(newEnt2.getCertificates().iterator().next().getSerial().getSerial())
            .isNotEqualTo(ent2.get(0).get("certificates").get(0).get("serial").get("id").asLong());
        assertThat(client.crl().getCurrentCrl()).contains(
            ent2.get(0).get("certificates").get(0).get("serial").get("id").asLong());
    }

    private List<ContentDTO> createBatchContent(OwnerDTO owner, int count) {
        List<ContentDTO> result = new ArrayList<>();
        IntStream.range(0, count).forEach(entry -> {
            result.add(ownerContentApi.createContent(owner.getKey(), Contents.random()));
        });
        return result;
    }
}

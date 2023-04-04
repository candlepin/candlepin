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
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
public class ImportUpdateSinglePoolSpecTest {

    @Test
    public void shouldBeAbleToMaintainMultipleImportedEntitlementsFromTheSamePool() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        ProductDTO prod = Products.random();
        ExportGenerator exportGenerator = new ExportGenerator(adminClient)
            .minimal()
            .withProduct(prod);
        Export export = exportGenerator.export();
        ConsumerDTO consumer = export.consumer();

        List<EntitlementDTO> ents = adminClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents).singleElement();
        EntitlementDTO ent1 = ents.get(0);

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        AsyncJobStatusDTO importJob = adminClient.owners().importManifestAsync(ownerKey, List.of(), export.file());
        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob).isFinished();

        List<SubscriptionDTO> subs = adminClient.owners().getOwnerSubscriptions(ownerKey);
        assertThat(subs)
            .singleElement()
            .extracting(SubscriptionDTO::getProduct)
            .isNotNull()
            .returns(prod.getId(), ProductDTO::getId);

        int expectedQuantity = 5;
        JsonNode ent2 = adminClient.consumers()
            .bindPool(consumer.getUuid(), subs.get(0).getUpstreamPoolId(), expectedQuantity)
            .get(0);
        Thread.sleep(500);

        export = exportGenerator.export();
        importJob = adminClient.owners().importManifestAsync(ownerKey, List.of(), export.file());
        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob).isFinished();
        subs = adminClient.owners().getOwnerSubscriptions(ownerKey);
        assertThat(subs)
            .hasSize(2);

        adminClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent1.getId());
        Thread.sleep(500);

        export = exportGenerator.export();
        importJob = adminClient.owners().importManifestAsync(ownerKey, List.of(), export.file());
        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob).isFinished();
        subs = adminClient.owners().getOwnerSubscriptions(ownerKey);
        assertThat(subs)
            .singleElement()
            .returns(Long.valueOf(expectedQuantity), SubscriptionDTO::getQuantity)
            .returns(ent2.get("id").asText(), SubscriptionDTO::getUpstreamEntitlementId);
    }

}

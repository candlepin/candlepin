/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.spec;

import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ExportResultDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyWithCapability;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@SpecTest
@OnlyInHosted
public class ExportV2SpecTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldCreateExportAsync() throws ApiException {

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        adminClient.hosted().createOwner(owner);
        ProductDTO prod = adminClient.hosted().createProduct(Products.random());
        ContentDTO content = adminClient.hosted().createContent(Contents.random());
        adminClient.hosted().addContentToProduct(prod.getId(), content.getId(), true);
        // active subscription
        SubscriptionDTO subscription1 = adminClient.hosted()
            .createSubscription(Subscriptions.random(owner, prod));
        // future subscription
        OffsetDateTime futureStart = OffsetDateTime.now().plusYears(1);
        SubscriptionDTO subscription2 = adminClient.hosted()
            .createSubscription(Subscriptions.random(owner, prod)
                .startDate(futureStart)
                .endDate(futureStart.plusYears(1)));

        assertThatJob(adminClient.owners().refreshPools(ownerKey, false))
            .terminates(adminClient)
            .isFinished();
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(ownerKey, true, false, null);

        assertEquals(2, pools.size());

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
        pools.forEach(pool -> adminClient.consumers()
            .bindPool(consumer.getUuid(), pool.getId(), 1));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        File export = createExportAsync(consumerClient, consumer.getUuid());

        try (
            FileSystem archive = FileSystems.newFileSystem(
                URI.create("jar:" + export.toURI()),
                Map.of())) {
            assertTrue(Files.exists(archive.getPath("meta.json")));
            testFileContent("meta.json", meta -> {
                assertEquals("2", meta.get("manifestVersion")
                    .asString());
            }).accept(archive);
            testFileContent("products/" + prod.getId() + ".json", product -> {
                assertEquals(prod.getName(), product.get("name")
                    .asString());
                ObjectNode productContents = product.get("productContent")
                    .asObject();
                assertEquals(1, productContents.size());
                JsonNode productContent = productContents.get(content.getId());
                assertNotNull(productContent);
                assertEquals(content.getId(), productContent.get("id")
                    .asString());
                assertEquals(content.getType(), productContent.get("type")
                    .asString());
                assertEquals(content.getLabel(), productContent.get("label")
                    .asString());
            }).accept(archive);

            pools.forEach(pool -> testSubscription(archive, pool));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertNotNull(export);
    }

    private void testSubscription(FileSystem fs, PoolDTO pool) {
        testFileContent("subscriptions/" + pool.getSubscriptionId() + ".json", meta -> {
            assertEquals(pool.getSubscriptionId(), meta.get("subscriptionId")
                .asString());
            assertEquals(pool.getProductId(), meta.get("productId")
                .asString());
        }).accept(fs);
    }

    @Test
    @OnlyInHosted
    @OnlyWithCapability("cloud_registration")
    public void shouldNotExportAnonymousManifestWithAnonymousToken() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        ProductDTO product1 = adminClient.hosted().createProduct(Products.random());
        ContentDTO content1 = adminClient.hosted().createContent(Contents.random());
        ContentDTO content2 = adminClient.hosted().createContent(Contents.random());
        adminClient.hosted().addContentToProduct(product1.getId(), content1.getId(), true);
        adminClient.hosted().addContentToProduct(product1.getId(), content2.getId(), true);

        ProductDTO product2 = adminClient.hosted().createProduct(Products.random());
        ContentDTO content3 = adminClient.hosted().createContent(Contents.random());
        adminClient.hosted().addContentToProduct(product2.getId(), content3.getId(), true);

        adminClient.hosted()
            .associateProductIdsToCloudOffer(offerId, List.of(product1.getId(), product2.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = ApiClients.noAuth().cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        Response response = Request.from(ApiClients.bearerToken(result.getToken()))
            .setPath("/v2/consumers/{consumer_uuid}/export")
            .setPathParam("consumer_uuid",  result.getAnonymousConsumerUuid())
            .setMethod(Request.Method.POST)
            .addHeader("accept", "application/json")
            .execute();

        assertEquals(404, response.getCode());
    }

    private java.util.function.Consumer<FileSystem> testFileContent(String path,
                                                            java.util.function.Consumer<JsonNode> test) {
        return fs -> {
            Path contentPath = fs.getPath(path);
            assertTrue(Files.exists(contentPath));
            try (BufferedReader reader = Files.newBufferedReader(contentPath)) {
                JsonNode content = mapper.readTree(reader);
                test.accept(content);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private File createExportAsync(ApiClient apiClient, String consumerUuid)
        throws ApiException {
        AsyncJobStatusDTO jobStatus = apiClient.consumers()
            .exportDataAsyncV2(consumerUuid);
        jobStatus = apiClient.jobs()
            .waitForJob(jobStatus.getId());
        if (!jobStatus.getState()
            .equals("FINISHED")) {
            throw new ApiException("Unable to create export.");
        }

        ExportResultDTO result = ApiClient.MAPPER
            .convertValue(jobStatus.getResultData(), ExportResultDTO.class);
        assertNotNull(result);
        assertNotNull(result.getExportId());

        File export = apiClient.consumers()
            .downloadExistingExport(consumerUuid, result.getExportId());
        export.deleteOnExit();

        return export;
    }
}

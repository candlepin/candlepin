/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@SpecTest
class OwnerContentResourceSpecTest {

    private OwnerDTO createOwner(ApiClient client) {
        return client.owners().createOwner(Owners.random());
    }

    private ApiClient createOrgAdminClient(ApiClient adminClient, OwnerDTO owner) {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private ApiClient createConsumerClient(ApiClient adminClient, OwnerDTO owner) {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        return ApiClients.ssl(consumer.getIdCert());
    }

    private ContentDTO getFullyPopulatedContent() {
        // Manually construct this so we know exactly what/how fields are populated
        String cid = StringUtil.random(8, StringUtil.CHARSET_NUMERIC);

        Set<String> modifiedProductIds = Set.of(
            "mpi1-" + cid,
            "mpi2-" + cid,
            "mpi3-" + cid);

        return new ContentDTO()
            .id("test_content-" + cid)
            .name("test content " + cid)
            .type("content type " + cid)
            .label("content label " + cid)
            .vendor("content vendor " + cid)
            .contentUrl("content url " + cid)
            .requiredTags("content tags " + cid)
            .releaseVer("release version " + cid)
            .gpgUrl("gpg url " + cid)
            .modifiedProductIds(modifiedProductIds)
            .arches("content arches " + cid)
            .metadataExpire(Long.parseLong(cid));
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingContents() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        ContentDTO output = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldUpdateGeneratedFieldsWhenUpdatingOwnerContents() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        ContentDTO entity = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setName(entity.getName() + "-update");
        ContentDTO output = adminClient.ownerContent().updateContent(owner.getKey(), entity.getId(), entity);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldAllowSuperAdminsToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = this.getFullyPopulatedContent();
        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), content);

        assertThat(created)
            .isNotNull()
            .usingRecursiveComparison()
            .ignoringFields("uuid", "created", "updated")
            .isEqualTo(content);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), content.getId());

        assertThat(fetched)
            .isNotNull()
            .usingRecursiveComparison()
            .ignoringFields("uuid", "created", "updated")
            .isEqualTo(content);
    }

    @Test
    public void shouldAllowCreatingProductsInOrgsWithLongKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random()
            .key(StringUtil.random("test_org-", 245, StringUtil.CHARSET_NUMERIC_HEX)));

        ContentDTO content = this.getFullyPopulatedContent();
        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), content);

        assertThat(created)
            .isNotNull()
            .usingRecursiveComparison()
            .ignoringFields("uuid", "created", "updated")
            .isEqualTo(content);
    }

    private static Stream<Arguments> criticalContentStringFieldsAndValues() {
        Set<String> fields = Set.of("label", "name", "type", "vendor");
        List<String> values = Arrays.asList("", null);

        List<Arguments> matrix = new ArrayList<>();

        for (String field : fields) {
            for (String value : values) {
                matrix.add(Arguments.of(field, value));
            }
        }

        return matrix.stream();
    }

    @ParameterizedTest
    @MethodSource("criticalContentStringFieldsAndValues")
    public void shouldRequireCriticalStringFieldsAreValidWhenCreatingContent(String fieldName, String value)
        throws Exception {

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentResourceSpecTest.this.createOwner(adminClient);

        ContentDTO content = this.getFullyPopulatedContent();

        // Convert the content to a JsonNode so we can clear the field
        ObjectNode jsonNode = ApiClient.MAPPER.readValue(content.toJson(), ObjectNode.class);

        assertThatObject(jsonNode)
            .isNotNull()
            .returns(true, node -> node.has(fieldName));

        jsonNode.put(fieldName, value);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(jsonNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .contains(fieldName + " cannot be null or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"label", "name", "type", "vendor"})
    public void shouldRequireCriticalFieldsArePopulatedWhenCreatingContent(String fieldName)
        throws Exception {

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentResourceSpecTest.this.createOwner(adminClient);

        ContentDTO content = this.getFullyPopulatedContent();

        // Convert the content to a JsonNode so we can clear the field
        ObjectNode jsonNode = ApiClient.MAPPER.readValue(content.toJson(), ObjectNode.class);

        assertThatObject(jsonNode)
            .isNotNull()
            .returns(true, node -> node.has(fieldName));

        jsonNode.remove(fieldName);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(jsonNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .contains(fieldName + " cannot be null or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "label", "name", "vendor", "contentUrl", "requiredTags", "releaseVer",
        "gpgUrl", "arches"})
    public void shouldValidateMaxLengthOfFieldsOnCreate(String fieldName) throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentResourceSpecTest.this.createOwner(adminClient);
        ContentDTO content = this.getFullyPopulatedContent();

        // Convert the content to a JsonNode so we can set the field
        ObjectNode jsonNode = ApiClient.MAPPER.readValue(content.toJson(), ObjectNode.class);
        jsonNode.put(fieldName, "s".repeat(256));

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody(jsonNode.toString())
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .contains(fieldName)
            .contains("size must be between");
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "label", "name", "vendor", "contentUrl", "requiredTags", "releaseVer",
        "gpgUrl", "arches"})
    public void shouldValidateMaxLengthOfFieldsOnBatchCreate(String fieldName) throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentResourceSpecTest.this.createOwner(adminClient);
        ContentDTO content = this.getFullyPopulatedContent();

        // Convert the content to a JsonNode so we can set the field
        ObjectNode jsonNode = ApiClient.MAPPER.readValue(content.toJson(), ObjectNode.class);
        jsonNode.put(fieldName, "s".repeat(256));

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content/batch")
            .setPathParam("owner_key", owner.getKey())
            .setMethod("POST")
            .setBody("[" + jsonNode.toString() + "]")
            .execute();

        assertThat(response)
            .returns(400, Response::getCode);

        assertThat(response.getBodyAsString())
            .isNotNull()
            .contains(fieldName)
            .contains("size must be between");
    }

    @Test
    public void shouldNotAllowCreatingContentWithInvalidIDs() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        String invalidId = "a".repeat(33);

        ContentDTO content = this.getFullyPopulatedContent()
            .id(invalidId);

        assertBadRequest(() -> adminClient.ownerContent().createContent(owner.getKey(), content));

        assertNotFound(() -> adminClient.ownerContent().getContentById(owner.getKey(), content.getId()));
    }

    @Test
    public void shouldNotAllowCreatingContentInBatchesWithInvalidIDs() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        String invalidId = "a".repeat(33);

        ContentDTO content1 = this.getFullyPopulatedContent()
            .id(invalidId);

        ContentDTO content2 = this.getFullyPopulatedContent();

        assertBadRequest(() -> adminClient.ownerContent()
            .createContentBatch(owner.getKey(), List.of(content1, content2)));

        assertNotFound(() -> adminClient.ownerContent().getContentById(owner.getKey(), content1.getId()));
        assertNotFound(() -> adminClient.ownerContent().getContentById(owner.getKey(), content2.getId()));
    }

    @Test
    public void shouldNotAllowOrgAdminsToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        assertForbidden(() -> orgAdminClient.ownerContent().createContent(owner.getKey(), Contents.random()));
    }

    @Test
    public void shouldNotAllowConsumersToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().createContent(owner.getKey(), Contents.random()));
    }

    @Test
    public void shouldAllowSuperAdminsToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);
    }

    @Test
    public void shouldAllowOrgAdminsToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        ContentDTO fetched = orgAdminClient.ownerContent().getContentById(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);
    }

    @Test
    public void shouldNotAllowConsumersToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().getContentById(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldAllowSuperAdminsToUpdateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        String cid = StringUtil.random(8, StringUtil.CHARSET_NUMERIC);

        Set<String> modifiedProductIds = Set.of(
            "mpi1-" + cid,
            "mpi2-" + cid,
            "mpi3-" + cid);

        ContentDTO content = new ContentDTO()
            .id("test_content-" + cid)
            .name("test content " + cid)
            .type("content type " + cid)
            .label("content label " + cid)
            .vendor("content vendor " + cid)
            .contentUrl("content url " + cid)
            .requiredTags("content tags " + cid)
            .releaseVer("release version " + cid)
            .gpgUrl("gpg url " + cid)
            .modifiedProductIds(modifiedProductIds)
            .arches("content arches " + cid)
            .metadataExpire(Long.parseLong(cid));

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), content);
        assertNotNull(created);

        Set<String> updatedModifiedProductIds = Set.of(
            "mpi1-" + cid + "-updated",
            "mpi2-" + cid + "-updated",
            "mpi3-" + cid + "-updated");

        ContentDTO contentUpdate = new ContentDTO()
            .id(content.getId())
            .name(content.getName() + "-updated")
            .type(content.getType() + "-updated")
            .label(content.getLabel() + "-updated")
            .vendor(content.getVendor() + "-updated")
            .contentUrl(content.getContentUrl() + "-updated")
            .requiredTags(content.getRequiredTags() + "-updated")
            .releaseVer(content.getReleaseVer() + "-updated")
            .gpgUrl(content.getGpgUrl() + "-updated")
            .modifiedProductIds(updatedModifiedProductIds)
            .arches(content.getArches() + "-updated")
            .metadataExpire(content.getMetadataExpire() + 10L);

        ContentDTO updated = adminClient.ownerContent().updateContent(owner.getKey(), contentUpdate.getId(),
            contentUpdate);

        assertNotNull(updated);

        // Impl note:
        // We can't do a raw equality check since our generated content won't have a value for the
        // uuid, created, or updated fields. We can cheat a bit and just use those on the returned
        // DTO to test everything else with .equals
        content.uuid(created.getUuid())
            .created(created.getCreated())
            .updated(created.getUpdated());

        contentUpdate.uuid(updated.getUuid())
            .created(updated.getCreated())
            .updated(updated.getUpdated());

        assertEquals(content, created);
        assertEquals(contentUpdate, updated);
        assertNotEquals(created, updated);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), updated.getId());
        assertNotNull(fetched);

        // Same deal here, but we've already set the fields to what they should be upstream
        assertEquals(contentUpdate, fetched);
    }

    @Test
    public void shouldNotAllowUpdatingIDFields() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        // Cache the ID since we're about to clobber it
        String id = created.getId();

        // These fields should be silently ignored during an update
        ContentDTO update = Contents.copy(created)
            .uuid("updated_uuid")
            .id("updated_id");

        ContentDTO updated = adminClient.ownerContent().updateContent(owner.getKey(), id, update);
        assertNotNull(updated);

        // Both DTOs should be identical yet, since the fields changed aren't changeable
        assertEquals(created, updated);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), id);
        assertNotNull(fetched);

        // Same here; should still be identical
        assertEquals(created, fetched);
        assertEquals(updated, fetched);
    }

    @Test
    public void shouldUpdateProductsWhenAttachedContentChanges() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(content);

        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        assertNotNull(product);

        product = adminClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), true);
        assertNotNull(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(content);

        ContentDTO update = Contents.copy(content)
            .name("updated_content");

        ContentDTO updatedContent = adminClient.ownerContent()
            .updateContent(owner.getKey(), content.getId(), update);

        assertNotNull(updatedContent);
        assertNotEquals(content, updatedContent);

        ProductDTO updatedProduct = adminClient.ownerProducts()
            .getProductById(owner.getKey(), product.getId());

        assertNotNull(updatedProduct);
        assertNotEquals(product, updatedProduct);

        assertThat(updatedProduct.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(updatedContent);
    }

    @Test
    public void shouldNotAllowOrgAdminsToUpdateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        created.setName("updated content");

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        assertForbidden(() -> orgAdminClient.ownerContent()
            .updateContent(owner.getKey(), created.getId(), created));
    }

    @Test
    public void shouldNotAllowConsumersToUpdateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        created.setName("updated content");

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent()
            .updateContent(owner.getKey(), created.getId(), created));
    }

    @Test
    public void shouldAllowSuperAdminsToRemoveContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);

        adminClient.ownerContent().removeContent(owner.getKey(), created.getId());

        assertNotFound(() -> adminClient.ownerContent().getContentById(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldNotAllowOrgAdminsToRemoveContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        assertForbidden(() -> orgAdminClient.ownerContent().removeContent(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldNotAllowConsumersToRemoveContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(created);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().removeContent(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldNotAllowRemovingContentAttachedToAProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ProductDTO product = adminClient.ownerProducts().createProduct(owner.getKey(), Products.random()
            .addProductContentItem(Contents.toProductContent(content, true)));

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(content);

        assertBadRequest(() -> adminClient.ownerContent().removeContent(owner.getKey(), content.getId()));

        // Verify the content still exists
        ContentDTO updatedContent = adminClient.ownerContent()
            .getContentById(owner.getKey(), content.getId());

        assertThat(updatedContent)
            .isNotNull()
            .isEqualTo(content);

        // Verify that we still haven't touched the product
        ProductDTO updatedProduct = adminClient.ownerProducts()
            .getProductById(owner.getKey(), product.getId());

        assertThat(updatedProduct.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(content);
    }

    @Test
    public void shouldForceRegenerationOfEntitlementsProvidingChangedContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // Create a pool to consume
        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Contents.random());
        assertNotNull(content);

        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        assertNotNull(product);

        product = adminClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), true);
        assertNotNull(product);

        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.randomUpstream(product));
        assertNotNull(pool);

        // Create a consumer and consume it
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer.getIdCert());

        String output = consumerClient.consumers()
            .bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);
        assertNotNull(output);

        List<CertificateSerialDTO> certSerials1 = consumerClient.consumers()
            .getEntitlementCertificateSerials(consumer.getUuid());

        assertNotNull(certSerials1);
        assertEquals(1, certSerials1.size());

        // Update the content
        ContentDTO contentUpdate = Contents.copy(content)
            .gpgUrl("https://www.gpg.url/test");

        ContentDTO updatedContent = adminClient.ownerContent()
            .updateContent(owner.getKey(), contentUpdate.getId(), contentUpdate);

        assertNotNull(updatedContent);
        assertNotEquals(content, updatedContent);

        // Ensure the consumer's entitlement will be regenerated (whenever)
        List<CertificateSerialDTO> certSerials2 = consumerClient.consumers()
            .getEntitlementCertificateSerials(consumer.getUuid());

        assertNotNull(certSerials2);
        assertEquals(1, certSerials2.size());

        CertificateSerialDTO certSerial1 = certSerials1.get(0);
        CertificateSerialDTO certSerial2 = certSerials2.get(0);

        assertNotEquals(certSerial1.getSerial(), certSerial2.getSerial());
    }

    @Nested
    @OnlyInHosted
    @TestInstance(Lifecycle.PER_CLASS)
    public class LockedEntityTests {

        private OwnerDTO owner;
        private ContentDTO targetEntity;

        @BeforeAll
        public void setup() throws Exception {
            // Create an upstream product, attach some content, attach it to a subscription, and
            // then refresh to pull it down as a "locked" content.
            ApiClient adminClient = ApiClients.admin();

            OwnerDTO owner = Owners.random();

            ContentDTO content = Contents.random();

            ProductContentDTO pc = new ProductContentDTO()
                .content(content)
                .enabled(true);

            ProductDTO eng = Products.randomEng()
                .addProductContentItem(pc);

            ProductDTO sku = Products.randomSKU()
                .addProvidedProductsItem(eng);

            SubscriptionDTO subscription = Subscriptions.random(owner, sku);

            adminClient.hosted().createOwner(owner);
            adminClient.hosted().createSubscription(subscription, true);

            this.owner = adminClient.owners().createOwner(owner);
            assertNotNull(this.owner);
            assertEquals(owner.getKey(), this.owner.getKey());

            AsyncJobStatusDTO job = adminClient.owners().refreshPools(owner.getKey(), false);
            job = adminClient.jobs().waitForJob(job);
            assertEquals("FINISHED", job.getState());

            this.targetEntity = adminClient.ownerContent().getContentById(owner.getKey(), content.getId());
            assertNotNull(this.targetEntity);
            assertEquals(content.getId(), this.targetEntity.getId());
        }

        @Test
        public void shouldNotAllowUpdatingLockedContent() throws Exception {
            ApiClient adminClient = ApiClients.admin();

            ContentDTO update = Contents.copy(this.targetEntity)
                .name("updated content name")
                .label("updated label");

            assertForbidden(() -> adminClient.ownerContent()
                .updateContent(this.owner.getKey(), update.getId(), update));
        }

        @Test
        public void shouldNotAllowDeletingLockedContent() throws Exception {
            ApiClient adminClient = ApiClients.admin();

            assertForbidden(() -> adminClient.ownerContent()
                .removeContent(this.owner.getKey(), this.targetEntity.getId()));
        }
    }

    /**
     * This test attempts to verify that performing multiple modifications to content entity
     * does not cause unintended side effects or unexpected loss of data.
     *
     * The expected result of this test is that in the case of parallel modification, a content
     * entity should be in the last state received by Candlepin, and should not lose any external
     * links back to the content, such as those coming from products, or environments.
     */
    @Test
    @SuppressWarnings("MethodLength")
    public void shouldPermitSafeParallelModification() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        EnvironmentDTO env = adminClient.owners().createEnvironment(owner.getKey(), Environments.random());
        assertNotNull(env);

        int products = 10;  // number of products to create
        int content = 3;    // number of content to create per product

        Map<String, Set<String>> productContentIdMap = new HashMap<>();
        Map<String, ContentDTO> contentMap = Collections.synchronizedMap(new HashMap<>());

        // Setup initial state
        for (int pcount = 0; pcount < products; ++pcount) {
            ProductDTO pdto = Products.randomSKU()
                .id(String.format("prod_%d", pcount));

            pdto = adminClient.ownerProducts().createProduct(owner.getKey(), pdto);
            assertNotNull(pdto);

            for (int ccount = 0; ccount < content; ++ccount) {
                // Create content
                ContentDTO cdto = Contents.random()
                    .id(String.format("%s-cont_%d", pdto.getId(), ccount));

                cdto = adminClient.ownerContent().createContent(owner.getKey(), cdto);
                assertNotNull(cdto);

                // Promote it in the environment
                ContentToPromoteDTO promotion = new ContentToPromoteDTO()
                    .environmentId(env.getId())
                    .contentId(cdto.getId())
                    .enabled(true);

                adminClient.environments().promoteContent(env.getId(), List.of(promotion), null);

                // Attach it to the product
                adminClient.ownerProducts()
                    .addContentToProduct(owner.getKey(), pdto.getId(), cdto.getId(), true);

                // Store the mapping so we can reference this relationship later (maybe?)
                contentMap.put(cdto.getId(), cdto);

                productContentIdMap.computeIfAbsent(pdto.getId(), key -> new HashSet<>())
                    .add(cdto.getId());
            }
        }

        // Actual test: Modify the contents in as parallel as possible.
        Set<Thread> threads = new HashSet<>();
        int iterations = 10;
        long maxThreadRuntime = 60000;

        AtomicInteger changes = new AtomicInteger(0);
        for (String contentId : contentMap.keySet()) {
            final String cid = contentId;

            threads.add(new Thread(() -> {
                for (int i = 0; i < iterations; ++i) {
                    ContentDTO update = new ContentDTO()
                        .id(cid)
                        .gpgUrl(StringUtil.random("gpgurl-", 8, StringUtil.CHARSET_ALPHANUMERIC))
                        .arches(StringUtil.random("arches-", 8, StringUtil.CHARSET_ALPHANUMERIC))
                        .requiredTags(StringUtil.random("tags-", 8, StringUtil.CHARSET_ALPHANUMERIC));

                    ContentDTO cdto = adminClient.ownerContent()
                        .updateContent(owner.getKey(), cid, update);

                    assertThat(cdto)
                        .isNotNull()
                        .returns(cid, ContentDTO::getId)
                        .returns(update.getGpgUrl(), ContentDTO::getGpgUrl)
                        .returns(update.getArches(), ContentDTO::getArches)
                        .returns(update.getRequiredTags(), ContentDTO::getRequiredTags);

                    // Potentially dangerous since we're not synchronized at all, but sure.
                    contentMap.put(cid, cdto);

                    changes.incrementAndGet();
                }
            }));
        }

        // Manually invoke the orphan cleanup job three or four times throughout
        threads.add(new Thread(() -> {
            int maxChanges = iterations * products * content;
            int minChanges = Math.min(maxChanges >>> 2, 1);

            int prev = 0;
            int current = 0;
            long endTime = System.currentTimeMillis() + maxThreadRuntime;

            try {
                while (current < maxChanges && System.currentTimeMillis() < endTime) {
                    current = changes.get();

                    if (current - prev >= minChanges) {
                        AsyncJobStatusDTO orphanCleanupJob = adminClient.jobs()
                            .scheduleJob("OrphanCleanupJob");
                        assertNotNull(orphanCleanupJob);

                        orphanCleanupJob = adminClient.jobs().waitForJob(orphanCleanupJob);
                        assertEquals("FINISHED", orphanCleanupJob.getState());

                        prev = current;
                    }

                    Thread.sleep(500);
                }
            }
            catch (InterruptedException e) {
                // intentionally left empty
            }
        }));

        threads.forEach(thread -> {
            thread.setDaemon(true);
            thread.start();
        });

        threads.forEach(thread -> {
            try {
                while (thread.isAlive()) {
                    thread.join(maxThreadRuntime);
                }
            }
            catch (InterruptedException e) {
                // intentionally left empty
            }
        });

        // Verify environment still lists our expected content
        env = adminClient.environments().getEnvironment(env.getId());
        assertNotNull(env);

        assertThat(env.getEnvironmentContent())
            .isNotNull()
            .hasSize(contentMap.size())
            .map(EnvironmentContentDTO::getContentId)
            .containsExactlyInAnyOrderElementsOf(contentMap.keySet());

        // Verify that the products still link to valid instances of the content we just modified
        for (Map.Entry<String, Set<String>> entry : productContentIdMap.entrySet()) {
            String pid = entry.getKey();
            Set<String> cids = entry.getValue();

            ProductDTO pdto = adminClient.ownerProducts().getProductById(owner.getKey(), pid);
            assertNotNull(pdto);

            assertThat(pdto.getProductContent())
                .isNotNull()
                .hasSize(cids.size())
                .map(ProductContentDTO::getContent)
                .map(ContentDTO::getId)
                .containsExactlyInAnyOrderElementsOf(cids);
        }
    }

    @Nested
    @Isolated
    @TestInstance(Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    public class ContentQueryTests {

        private static final String QUERY_PATH = "/owners/{owner_key}/content";

        private static final String INCLUSION_INCLUDE = "include";
        private static final String INCLUSION_EXCLUDE = "exclude";
        private static final String INCLUSION_EXCLUSIVE = "exclusive";

        private static record ProductInfo(ProductDTO dto, OwnerDTO owner, Set<String> activeOwners) {

            public String id() {
                return this.dto() != null ? this.dto().getId() : null;
            }

            public boolean active() {
                return !this.activeOwners().isEmpty();
            }

            public boolean custom() {
                return this.owner() != null;
            }

            public Stream<ContentDTO> content() {
                return Optional.ofNullable(this.dto())
                  .map(ProductDTO::getProductContent)
                  .map(Collection::stream)
                  .orElse(Stream.empty())
                  .map(ProductContentDTO::getContent);
            }
        };

        private ApiClient adminClient;

        private List<OwnerDTO> owners;
        private Map<String, ProductInfo> productMap;

        /**
         * Verifies either the manifest generator extension or the hosted test extension is present as
         * required by the current operating mode.
         */
        private void checkRequiredExtensions() {
            if (CandlepinMode.isStandalone()) {
                assumeTrue(CandlepinMode::hasManifestGenTestExtension);
            }
            else {
                assumeTrue(CandlepinMode::hasHostedTestExtension);
            }
        }

        private OwnerDTO createOwner(String keyPrefix) {
            OwnerDTO owner = Owners.random()
                .key(StringUtil.random(keyPrefix + "-"));

            return this.adminClient.owners()
                .createOwner(owner);
        }

        private SubscriptionDTO createSubscription(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Impl note:
            // We create active subscriptions in the future to work around a limitation on manifests
            // disallowing and ignoring expired pools
            return Subscriptions.random(owner, product)
                .startDate(active ? now.minus(7, ChronoUnit.DAYS) : now.plus(7, ChronoUnit.DAYS))
                .endDate(now.plus(30, ChronoUnit.DAYS));
        }

        private PoolDTO createPool(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            PoolDTO pool = Pools.random(product)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(active ? now.plus(7, ChronoUnit.DAYS) : now.minus(3, ChronoUnit.DAYS));

            return this.adminClient.owners()
                .createPool(owner.getKey(), pool);
        }

        private void mapProductInfo(ProductDTO product, OwnerDTO owner, OwnerDTO activeOwner) {
            Set<String> activeOwnerKeys = new HashSet<>();
            if (activeOwner != null) {
                activeOwnerKeys.add(activeOwner.getKey());
            }

            ProductInfo existing = this.productMap.get(product.getId());
            if (existing != null) {
                activeOwnerKeys.addAll(existing.activeOwners());
            }

            ProductInfo pinfo = new ProductInfo(product, owner, activeOwnerKeys);
            this.productMap.put(product.getId(), pinfo);

        }

        private void commitGlobalSubscriptions(Collection<SubscriptionDTO> subscriptions) throws Exception {
            Map<String, List<SubscriptionDTO>> subscriptionMap = new HashMap<>();

            for (SubscriptionDTO subscription : subscriptions) {
                NestedOwnerDTO owner = subscription.getOwner();

                subscriptionMap.computeIfAbsent(owner.getKey(), key -> new ArrayList<>())
                    .add(subscription);
            }

            for (Map.Entry<String, List<SubscriptionDTO>> entry : subscriptionMap.entrySet()) {
                String ownerKey = entry.getKey();
                List<SubscriptionDTO> ownerSubs = entry.getValue();

                AsyncJobStatusDTO job;
                if (CandlepinMode.isStandalone()) {
                    File manifest = new ExportGenerator()
                        .addSubscriptions(ownerSubs)
                        .export();

                    job = this.adminClient.owners()
                        .importManifestAsync(ownerKey, List.of(), manifest);
                }
                else {
                    ownerSubs.forEach(subscription -> this.adminClient.hosted()
                        .createSubscription(subscription, true));

                    job = this.adminClient.owners()
                        .refreshPools(ownerKey, false);
                }

                assertThatJob(job)
                    .isNotNull()
                    .terminates(this.adminClient)
                    .isFinished();
            }
        }

        @BeforeAll
        public void setup() throws Exception {
            // Ensure we have our required test extensions or we'll be very broken...
            this.checkRequiredExtensions();

            this.adminClient = ApiClients.admin();

            this.owners = List.of(
                this.createOwner("owner1"),
                this.createOwner("owner2"),
                this.createOwner("owner3"));

            this.productMap = new HashMap<>();

            List<SubscriptionDTO> subscriptions = new ArrayList<>();

            // Dummy org we use for creating a bunch of future pools for our global products. We'll also
            // create per-org subscriptions for these products later. Also note that these will never be
            // fully resolved.
            OwnerDTO globalOwner = this.createOwner("global");

            List<ProductDTO> globalProducts = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                List<ContentDTO> contents = new ArrayList<>();

                for (int c = 0; c < 2; ++c) {
                    ContentDTO content = Contents.random()
                        .id(String.format("g-content-%d%s", i, (char) ('a' + c)))
                        .name(String.format("global_content_%d%s", i, (char) ('a' + c)))
                        .label(String.format("global content %d%s", i, (char) ('a' + c)));

                    contents.add(content);
                }

                ProductDTO gprod = new ProductDTO()
                    .id("g-prod-" + i)
                    .name("global product " + i)
                    .addProductContentItem(Contents.toProductContent(contents.get(0), true))
                    .addProductContentItem(Contents.toProductContent(contents.get(1), false));

                subscriptions.add(this.createSubscription(globalOwner, gprod, false));
                globalProducts.add(gprod);
                this.mapProductInfo(gprod, null, null);
            }

            for (int oidx = 1; oidx <= owners.size(); ++oidx) {
                OwnerDTO owner = owners.get(oidx - 1);

                List<ProductDTO> ownerProducts = new ArrayList<>();
                for (int i = 1; i <= 2; ++i) {
                    List<ContentDTO> contents = new ArrayList<>();

                    for (int c = 0; c < 2; ++c) {
                        ContentDTO content = Contents.random()
                            .id(String.format("o%d-content-%d%s", oidx, i, (char) ('a' + c)))
                            .name(String.format("%s_content_%d%s", owner.getKey(), i, (char) ('a' + c)))
                            .label(String.format("%s content %d%s", owner.getKey(), i, (char) ('a' + c)));

                        contents.add(this.adminClient.ownerContent()
                            .createContent(owner.getKey(), content));
                    }

                    ProductDTO cprod = new ProductDTO()
                        .id(String.format("o%d-prod-%d", oidx, i))
                        .name(String.format("%s product %d", owner.getKey(), i))
                        .addProductContentItem(Contents.toProductContent(contents.get(0), true))
                        .addProductContentItem(Contents.toProductContent(contents.get(1), false));

                    cprod = this.adminClient.ownerProducts()
                        .createProduct(owner.getKey(), cprod);

                    ownerProducts.add(cprod);
                    this.mapProductInfo(cprod, owner, null);
                }

                // Create an active and inactive global subscription for this org
                subscriptions.add(this.createSubscription(owner, globalProducts.get(0), true));
                subscriptions.add(this.createSubscription(owner, globalProducts.get(1), false));
                this.mapProductInfo(globalProducts.get(0), null, owner);

                // Create an active and inactive custom subscription
                this.createPool(owner, ownerProducts.get(0), true);
                this.createPool(owner, ownerProducts.get(1), false);
                this.mapProductInfo(ownerProducts.get(0), owner, owner);
            }

            this.commitGlobalSubscriptions(subscriptions);
        }

        // Impl note:
        // Since we cannot depend on the global state being prestine from run to run (or even test to
        // test), we cannot use exact matching on our output as extraneous products from previous test
        // runs or otherwise pre-existing data may show up in the result set. Instead, these tests will
        // verify our expected products are present, and unexpected products from the test data are not.

        private List<String> getUnexpectedIds(List<String> expectedIds) {
            return this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .filter(Predicate.not(expectedIds::contains))
                .toList();
        }

        private Predicate<ProductInfo> buildOwnerProductPredicate(OwnerDTO owner) {
            return pinfo -> pinfo.owner() == null || owner.getKey().equals(pinfo.owner().getKey());
        }

        @Test
        public void shouldAllowQueryingWithoutAnyFilters() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldDefaultToActiveExclusiveCustomInclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, null, null);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldFailWithInvalidOwners() {
            assertNotFound(() -> this.adminClient.ownerContent()
                .getContentsByOwner("invalid_owner", null, null, null, null));
        }

        @Test
        public void shouldAllowFilteringOnIDs() {
            // Randomly seeded for consistency; seed itself chosen randomly
            Random rand = new Random(81451);

            OwnerDTO owner = this.owners.get(1);

            List<String> cids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getId)
                .toList();

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .filter(cids::contains)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), cids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_id" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidIds(String cid) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            OwnerDTO owner = this.owners.get(1);

            String expectedCid = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .findAny()
                .get();

            List<String> expectedCids = List.of(expectedCid);
            List<String> cids = Arrays.asList(expectedCid, cid);

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), cids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringOnLabel() {
            Random rand = new Random(54978);

            OwnerDTO owner = this.owners.get(1);

            List<String> labels = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getLabel)
                .toList();

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .filter(content -> labels.contains(content.getLabel()))
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, labels, INCLUSION_INCLUDE,
                    INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_label" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidLabels(String label) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything
            OwnerDTO owner = this.owners.get(1);

            ContentDTO content = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .findAny()
                .get();

            List<String> labels = Arrays.asList(content.getLabel(), label);
            List<String> expectedCids = List.of(content.getId());

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, labels, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringWithActiveIncluded() {
            // This is effectively the same as no filter -- we expect everything within the org back
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids);
        }

        @Test
        public void shouldAllowFilteringWithActiveExcluded() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> !pinfo.active())
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringWithActiveExclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldErrorWithInvalidActiveInclusion() {
            OwnerDTO owner = this.owners.get(1);

            assertBadRequest(() -> this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, "invalid_type", INCLUSION_INCLUDE));
        }

        @Test
        public void shouldAllowFilteringWithCustomIncluded() {
            // This is effectively the same as no filter -- we expect everything in the org back
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids);
        }

        @Test
        public void shouldAllowFilteringWithCustomExcluded() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(pinfo -> !pinfo.custom())
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringWithCustomExclusive() {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldErrorWithInvalidCustomInclusion() {
            OwnerDTO owner = this.owners.get(1);

            assertBadRequest(() -> this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, "invalid_type"));
        }

        @Test
        public void shouldAllowQueryingWithMultipleFilters() {
            Random rand = new Random(58574);

            OwnerDTO owner = this.owners.get(1);

            // Hand pick a content to ensure that we have *something* that comes out of the filter, should
            // our random selection process not have any intersection.
            ContentDTO selectedContent = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .findAny()
                .get();

            List<String> cids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getId)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> labels = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getLabel)
                .collect(Collectors.toCollection(ArrayList::new));

            cids.add(selectedContent.getId());
            labels.add(selectedContent.getLabel());

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .filter(this.buildOwnerProductPredicate(owner))
                .flatMap(ProductInfo::content)
                .filter(content -> cids.contains(content.getId()))
                .filter(content -> labels.contains(content.getLabel()))
                .map(ContentDTO::getId)
                .toList();

            assertThat(expectedCids)
                .isNotEmpty();

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), cids, labels, INCLUSION_EXCLUSIVE,
                    INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
        public void shouldAllowQueryingInPages(int pageSize) {
            OwnerDTO owner = this.owners.get(1);

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(this.buildOwnerProductPredicate(owner))
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<String> received = new ArrayList<>();

            int page = 0;
            while (true) {
                Response response = Request.from(this.adminClient)
                    .setPath(QUERY_PATH)
                    .setPathParam("owner_key", owner.getKey())
                    .addQueryParam("page", String.valueOf(++page))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .addQueryParam("active", "include")
                    .addQueryParam("custom", "exclusive")
                    .execute();

                assertEquals(200, response.getCode());

                List<ContentDTO> output = response.deserialize(new TypeReference<List<ContentDTO>>() {});
                assertNotNull(output);

                if (output.isEmpty()) {
                    break;
                }

                output.stream()
                    .map(ContentDTO::getId)
                    .sequential()
                    .forEach(received::add);
            }

            assertThat(received)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("page", String.valueOf(page))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("per_page", String.valueOf(pageSize))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithAscendingOrderedOutput(String field) {
            OwnerDTO owner = this.owners.get(1);

            Map<String, Comparator<ContentDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ContentDTO::getId),
                "name", Comparator.comparing(ContentDTO::getName),
                "uuid", Comparator.comparing(ContentDTO::getUuid));

            List<ContentDTO> contents = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedCids = contents.stream()
                .sorted(comparatorMap.get(field))
                .map(ContentDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "asc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ContentDTO> output = response.deserialize(new TypeReference<List<ContentDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyElementsOf(expectedCids); // this must be an ordered check
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithDescendingOrderedOutput(String field) {
            OwnerDTO owner = this.owners.get(1);

            Map<String, Comparator<ContentDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ContentDTO::getId),
                "name", Comparator.comparing(ContentDTO::getName),
                "uuid", Comparator.comparing(ContentDTO::getUuid));

            List<ContentDTO> contents = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedCids = contents.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(ContentDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "desc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ContentDTO> output = response.deserialize(new TypeReference<List<ContentDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyElementsOf(expectedCids); // this must be an ordered check
        }

        @Test
        public void shouldFailWithInvalidOrderField() {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("sort_by", "invalid field")
                .execute();

            assertEquals(400, response.getCode());
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            OwnerDTO owner = this.owners.get(1);

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .setPathParam("owner_key", owner.getKey())
                .addQueryParam("order", "invalid order")
                .execute();

            assertEquals(400, response.getCode());
        }

        // These tests verify the definition of "active" is properly implemented, ensuring "active" is
        // defined as a product which is attached to a pool which has started and has not expired, or
        // attached to another active product (recursively).
        //
        // This definition is recursive in nature, so the effect is that it should consider any product
        // that is a descendant of a product attached to a non-future pool that hasn't yet expired.

        @Test
        public void shouldOnlySelectActiveContentFromActivePools() {
            // "active" only considers pools which have started but have not yet expired -- that is:
            // (start time < now() < end time)
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                ContentDTO contentA = Contents.random().id(String.format("p%d-ca", i));
                ContentDTO contentB = Contents.random().id(String.format("p%d-cb", i));

                contentA = this.adminClient.ownerContent().createContent(owner.getKey(), contentA);
                contentB = this.adminClient.ownerContent().createContent(owner.getKey(), contentB);

                ProductDTO product = Products.random()
                    .id("prod-" + i)
                    .name("product " + i)
                    .addProductContentItem(Contents.toProductContent(contentA, true))
                    .addProductContentItem(Contents.toProductContent(contentB, false));

                this.adminClient.ownerProducts().createProduct(owner.getKey(), product);
                products.add(product);
            }

            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Create three pools: expired, current (active), future
            PoolDTO pool1 = Pools.random(products.get(0))
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(now.minus(3, ChronoUnit.DAYS));
            PoolDTO pool2 = Pools.random(products.get(1))
                .startDate(now.minus(3, ChronoUnit.DAYS))
                .endDate(now.plus(3, ChronoUnit.DAYS));
            PoolDTO pool3 = Pools.random(products.get(2))
                .startDate(now.plus(3, ChronoUnit.DAYS))
                .endDate(now.plus(7, ChronoUnit.DAYS));

            this.adminClient.owners().createPool(owner.getKey(), pool1);
            this.adminClient.owners().createPool(owner.getKey(), pool2);
            this.adminClient.owners().createPool(owner.getKey(), pool3);

            // Active = exclusive should only find the active pool; future and expired pools should be omitted
            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyInAnyOrder("p2-ca", "p2-cb");
        }

        @Test
        public void shouldAlsoIncludeDescendantProductsOfActiveProducts() {
            // "active" includes descendants of products attached to an active pool
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 0; i < 20; ++i) {
                ContentDTO contentA = Contents.random().id(String.format("p%d-ca", i));
                ContentDTO contentB = Contents.random().id(String.format("p%d-cb", i));

                contentA = this.adminClient.ownerContent().createContent(owner.getKey(), contentA);
                contentB = this.adminClient.ownerContent().createContent(owner.getKey(), contentB);

                ProductDTO product = new ProductDTO()
                    .id("p" + i)
                    .name("product " + i)
                    .addProductContentItem(Contents.toProductContent(contentA, true))
                    .addProductContentItem(Contents.toProductContent(contentB, false));

                // Impl note: We can't create the products quite yet
                products.add(product);
            }

            /*
            pool -> prod - p0
                        derived - p1
                            provided - p2
                            provided - p3
                                provided - p4
                        provided - p5
                        provided - p6
            pool -> prod - p7
                        derived - p8*
                        provided - p9
            pool -> prod - p8*
                        provided - p10
                            provided - p11
                        provided - p12
                            provided - p13
                    prod - p14
                        derived - p15
                            provided - p16
                    prod - p17
                        provided - p18
            pool -> prod - p19
                    prod - p20
            */

            products.get(0).setDerivedProduct(products.get(1));
            products.get(0).addProvidedProductsItem(products.get(5));
            products.get(0).addProvidedProductsItem(products.get(6));

            products.get(1).addProvidedProductsItem(products.get(2));
            products.get(1).addProvidedProductsItem(products.get(3));

            products.get(3).addProvidedProductsItem(products.get(4));

            products.get(7).setDerivedProduct(products.get(8));
            products.get(7).addProvidedProductsItem(products.get(9));

            products.get(8).addProvidedProductsItem(products.get(10));
            products.get(8).addProvidedProductsItem(products.get(12));

            products.get(10).addProvidedProductsItem(products.get(11));

            products.get(12).addProvidedProductsItem(products.get(13));

            products.get(14).setDerivedProduct(products.get(15));

            products.get(15).setDerivedProduct(products.get(16));

            products.get(17).setDerivedProduct(products.get(18));

            // persist the products in reverse order so we don't hit any linkage errors
            for (int i = products.size() - 1; i >= 0; --i) {
                this.adminClient.ownerProducts().createProduct(owner.getKey(), products.get(i));
            }

            // create some pools to link to our product tree
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(0)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(7)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(8)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(19)));

            List<String> expectedCids = List.of("p0-ca", "p0-cb", "p1-ca", "p1-cb", "p2-ca", "p2-cb", "p3-ca",
                "p3-cb", "p4-ca", "p4-cb", "p5-ca", "p5-cb", "p6-ca", "p6-cb", "p7-ca", "p7-cb", "p8-ca",
                "p8-cb", "p9-ca", "p9-cb", "p10-ca", "p10-cb", "p11-ca", "p11-cb", "p12-ca", "p12-cb",
                "p13-ca", "p13-cb", "p19-ca", "p19-cb");

            List<ContentDTO> output = this.adminClient.ownerContent()
                .getContentsByOwner(owner.getKey(), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyInAnyOrderElementsOf(expectedCids);
        }
    }

}

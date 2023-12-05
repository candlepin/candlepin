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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;



@SpecTest
class OwnerContentSpecTest {

    private OwnerDTO createOwner(ApiClient client) {
        return client.owners().createOwner(Owners.random());
    }

    private ContentDTO createContent(ApiClient client, OwnerDTO owner) {
        return client.ownerContent()
            .createContent(owner.getKey(), Contents.random());
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
    public void shouldAllowSuperAdminsToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = this.getFullyPopulatedContent();
        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), content);

        assertNotNull(created);

        // Impl note:
        // We can't do a raw equality check since our generated content won't have a value for the
        // uuid, created, or updated fields. We can cheat a bit and just use those on the returned
        // DTO to test everything else with .equals
        content.uuid(created.getUuid())
            .created(created.getCreated())
            .updated(created.getUpdated());

        assertEquals(content, created);

        ContentDTO fetched = adminClient.ownerContent().getContentById(owner.getKey(), content.getId());
        assertNotNull(fetched);

        // Same deal here, but we've already set the fields to what they should be upstream
        assertEquals(content, fetched);
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
        OwnerDTO owner = OwnerContentSpecTest.this.createOwner(adminClient);

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

    private static Stream<String> critialContentFields() {
        return Stream.of("label", "name", "type", "vendor");
    }

    @ParameterizedTest
    @MethodSource("critialContentFields")
    public void shouldRequireCriticalFieldsArePopulatedWhenCreatingContent(String fieldName)
        throws Exception {

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentSpecTest.this.createOwner(adminClient);

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
    public void shouldListAllContentsInBulkFetch() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        String ownerKey = owner.getKey();

        ContentDTO content1 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO content2 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO content3 = adminClient.ownerContent().createContent(ownerKey, Contents.random());

        // Note: We must account for other globals that may have been created as well
        List<ContentDTO> contents = adminClient.ownerContent().getContentsByOwner(ownerKey, List.of(), false);
        assertThat(contents)
            .isNotNull()
            .hasSizeGreaterThanOrEqualTo(3)
            .contains(content1, content2, content3);
    }

    @Test
    public void shouldListsAllSpecifiedContentsInBulkFetch() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        String ownerKey = owner.getKey();

        ContentDTO content1 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO content2 = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        ContentDTO content3 = adminClient.ownerContent().createContent(ownerKey, Contents.random());

        // Pick two contents to use in a bulk get
        List<ContentDTO> contents = adminClient.ownerContent()
            .getContentsByOwner(ownerKey, List.of(content1.getId(), content3.getId()), false);

        assertThat(contents)
            .isNotNull()
            .hasSize(2)
            .containsOnly(content1, content3);
    }

    @Test
    public void shouldListContentInPages() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // The creation order here is important. By default, Candlepin sorts in descending order of the
        // entity's creation time, so we need to create them backward to let the default sorting order
        // let us page through them in ascending order.
        ContentDTO content3 = this.createContent(adminClient, owner);
        Thread.sleep(1000);
        ContentDTO content2 = this.createContent(adminClient, owner);
        Thread.sleep(1000);
        ContentDTO content1 = this.createContent(adminClient, owner);

        List<ContentDTO> content = List.of(content1, content2, content3);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        List<ContentDTO> c1set = response.deserialize(new TypeReference<List<ContentDTO>>() {});

        // Impl note: we aren't specifying the sort field or ID, so we can't guarantee any
        // particular object should be here, just that each page should be different,
        // non-duplicated, and part of our expected output.
        assertThat(c1set)
            .isNotNull()
            .hasSize(1)
            .isSubsetOf(content);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        List<ContentDTO> c2set = response.deserialize(new TypeReference<List<ContentDTO>>() {});
        assertThat(c2set)
            .isNotNull()
            .hasSize(1)
            .isSubsetOf(content)
            .doesNotContainAnyElementsOf(c1set);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        List<ContentDTO> c3set = response.deserialize(new TypeReference<List<ContentDTO>>() {});
        assertThat(c3set)
            .isNotNull()
            .hasSize(1)
            .isSubsetOf(content)
            .doesNotContainAnyElementsOf(c1set)
            .doesNotContainAnyElementsOf(c2set);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .addQueryParam("omit_global", "true")
            .execute();

        List<ContentDTO> c4set = response.deserialize(new TypeReference<>() {});
        assertThat(c4set)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldListContentInSortedPages() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // The creation order here is important. By default, Candlepin sorts in descending order of the
        // entity's creation time, so we need to create them backward to let the default sorting order
        // let us page through them in ascending order.
        ContentDTO content3 = this.createContent(adminClient, owner);
        Thread.sleep(1000);
        ContentDTO content2 = this.createContent(adminClient, owner);
        Thread.sleep(1000);
        ContentDTO content1 = this.createContent(adminClient, owner);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("order_by", "id")
            .addQueryParam("sort_order", "asc")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        assertThat(response.deserialize(new TypeReference<List<ContentDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(content1);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .addQueryParam("order_by", "id")
            .addQueryParam("sort_order", "asc")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        assertThat(response.deserialize(new TypeReference<List<ContentDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(content2);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .addQueryParam("order_by", "id")
            .addQueryParam("sort_order", "asc")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        assertThat(response.deserialize(new TypeReference<List<ContentDTO>>() {}))
            .isNotNull()
            .hasSize(1)
            .containsOnly(content3);

        response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .addQueryParam("order_by", "id")
            .addQueryParam("sort_order", "asc")
            .addQueryParam("omit_global", "true")
            .execute();

        assertNotNull(response);
        assertEquals(200, response.getCode());

        assertThat(response.deserialize(new TypeReference<List<ContentDTO>>() {}))
            .isNotNull()
            .isEmpty();
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
    public void shouldUpdateProductsWhenAttachedContentIsRemoved() throws Exception {
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

        adminClient.ownerContent().removeContent(owner.getKey(), content.getId());
        assertNotFound(() -> adminClient.ownerContent().getContentById(owner.getKey(), content.getId()));

        ProductDTO updatedProduct = adminClient.ownerProducts()
            .getProductById(owner.getKey(), product.getId());

        assertNotNull(updatedProduct);
        assertNotEquals(product, updatedProduct);

        assertThat(updatedProduct.getProductContent())
            .isNotNull()
            .hasSize(0);
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

        EnvironmentDTO env = adminClient.owners().createEnv(owner.getKey(), Environments.random());
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

}

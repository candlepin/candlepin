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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
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
import org.candlepin.spec.bootstrap.data.builder.Content;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.List;
import java.util.Set;



@SpecTest
class OwnerContentSpecTest {

    private OwnerDTO createOwner(ApiClient client) {
        return client.owners().createOwner(Owners.random());
    }

    private ContentDTO createContent(ApiClient client, OwnerDTO owner, String idPrefix) {
        return client.ownerContent()
            .createContent(owner.getKey(), Content.random(idPrefix));
    }

    private ApiClient createOrgAdminClient(ApiClient adminClient, OwnerDTO owner) {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private ApiClient createConsumerClient(ApiClient adminClient, OwnerDTO owner) {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        return ApiClients.ssl(consumer.getIdCert());
    }

    @Test
    public void shouldAllowSuperAdminsToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // Manually construct this so we know exactly what/how fields are populated
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

        // Impl note:
        // We can't do a raw equality check since our generated content won't have a value for the
        // uuid, created, or updated fields. We can cheat a bit and just use those on the returned
        // DTO to test everything else with .equals
        content.uuid(created.getUuid())
            .created(created.getCreated())
            .updated(created.getUpdated());

        assertEquals(content, created);

        ContentDTO fetched = adminClient.ownerContent().getOwnerContent(owner.getKey(), content.getId());
        assertNotNull(fetched);

        // Same deal here, but we've already set the fields to what they should be upstream
        assertEquals(content, fetched);
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class JsonManipulationTests {

        private ApiClient client;
        private Gson unmodified;

        @BeforeAll
        public void setup() throws Exception {
            // We need to enable serialization of nulls to craft the exact output we're expecting
            // and ensuring we're actually testing fields that are explicitly set to null
            this.client = ApiClients.admin();

            this.unmodified = this.client.getApiClient().getJSON().getGson();
            assertNotNull(this.unmodified);

            Gson modified = this.unmodified.newBuilder()
                .serializeNulls()
                .create();

            this.client.getApiClient().getJSON().setGson(modified);
        }

        @AfterAll
        public void restore() {
            // Restore the original Gson object so none of the other tests are affected
            assertNotNull(this.unmodified);
            this.client.getApiClient().getJSON().setGson(this.unmodified);
        }

        @Test
        public void shouldRequireCriticalFieldsWhenCreatingContent() throws Exception {
            // At the time of writing, this is only the ID field
            OwnerDTO owner = OwnerContentSpecTest.this.createOwner(this.client);

            ContentDTO content = Content.random()
                .id(null);

            assertBadRequest(() -> this.client.ownerContent().createContent(owner.getKey(), content));
        }
    }

    @Test
    public void shouldRequireCriticalFieldsWhenCreatingContent() throws Exception {
        // At the time of writing, this is only the ID field
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = OwnerContentSpecTest.this.createOwner(adminClient);

        ContentDTO content = Content.random()
            .id(null);

        assertBadRequest(() -> adminClient.ownerContent().createContent(owner.getKey(), content));
    }

    @Test
    public void shouldNotAllowOrgAdminsToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        assertForbidden(() -> orgAdminClient.ownerContent().createContent(owner.getKey(), Content.random()));
    }

    @Test
    public void shouldNotAllowConsumersToCreateContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().createContent(owner.getKey(), Content.random()));
    }

    @Test
    public void shouldAllowSuperAdminsToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ContentDTO fetched = adminClient.ownerContent().getOwnerContent(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);
    }

    @Test
    public void shouldAllowOrgAdminsToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        ContentDTO fetched = orgAdminClient.ownerContent().getOwnerContent(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);
    }

    @Test
    public void shouldNotAllowConsumersToFetchContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().getOwnerContent(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldListContentInPages() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // The creation order here is important. By default, Candlepin sorts in descending order of the
        // entity's creation time, so we need to create them backward to let the default sorting order
        // let us page through them in ascending order.
        ContentDTO content3 = this.createContent(adminClient, owner, "test_content-3");
        Thread.sleep(1000);
        ContentDTO content2 = this.createContent(adminClient, owner, "test_content-2");
        Thread.sleep(1000);
        ContentDTO content1 = this.createContent(adminClient, owner, "test_content-1");

        List<ContentDTO> content = List.of(content1, content2, content3);

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
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
            .execute();

        List<ContentDTO> c4set = response.deserialize(new TypeReference<List<ContentDTO>>() {});
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
        ContentDTO content3 = this.createContent(adminClient, owner, "test_content-3");
        Thread.sleep(1000);
        ContentDTO content2 = this.createContent(adminClient, owner, "test_content-2");
        Thread.sleep(1000);
        ContentDTO content1 = this.createContent(adminClient, owner, "test_content-1");

        Response response = Request.from(adminClient)
            .setPath("/owners/{owner_key}/content")
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("order_by", "id")
            .addQueryParam("sort_order", "asc")
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

        ContentDTO fetched = adminClient.ownerContent().getOwnerContent(owner.getKey(), updated.getId());
        assertNotNull(fetched);

        // Same deal here, but we've already set the fields to what they should be upstream
        assertEquals(contentUpdate, fetched);
    }

    @Test
    public void shouldNotAllowUpdatingCriticalFields() throws Exception {
        // At the time of writing, this is only the UUID and ID fields

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        // Cache the ID since we're about to clobber it
        String id = created.getId();

        // These fields should be silently ignored during an update
        ContentDTO update = Content.copy(created)
            .uuid("updated_uuid")
            .id("updated_id");

        ContentDTO updated = adminClient.ownerContent().updateContent(owner.getKey(), id, update);
        assertNotNull(updated);

        // Both DTOs should be identical yet, since the fields changed aren't changeable
        assertEquals(created, updated);

        ContentDTO fetched = adminClient.ownerContent().getOwnerContent(owner.getKey(), id);
        assertNotNull(fetched);

        // Same here; should still be identical
        assertEquals(created, fetched);
        assertEquals(updated, fetched);
    }

    @Test
    public void shouldUpdateProductsWhenAttachedContentChanges() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(content);

        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        assertNotNull(product);

        product = adminClient.ownerProducts()
            .addContent(owner.getKey(), product.getId(), content.getId(), true);
        assertNotNull(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(content);

        ContentDTO update = Content.copy(content)
            .name("updated_content");

        ContentDTO updatedContent = adminClient.ownerContent()
            .updateContent(owner.getKey(), content.getId(), update);

        assertNotNull(updatedContent);
        assertNotEquals(content, updatedContent);

        ProductDTO updatedProduct = adminClient.ownerProducts()
            .getProductByOwner(owner.getKey(), product.getId());

        assertNotNull(updatedProduct);
        assertNotEquals(product, updatedProduct);

        // Specifically, the UUID should have changed
        assertNotEquals(product.getUuid(), updatedProduct.getUuid());

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

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
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

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
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

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ContentDTO fetched = adminClient.ownerContent().getOwnerContent(owner.getKey(), created.getId());
        assertNotNull(fetched);
        assertEquals(created, fetched);

        adminClient.ownerContent().remove(owner.getKey(), created.getId());

        assertNotFound(() -> adminClient.ownerContent().getOwnerContent(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldNotAllowOrgAdminsToRemoveContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ApiClient orgAdminClient = this.createOrgAdminClient(adminClient, owner);
        assertForbidden(() -> orgAdminClient.ownerContent().remove(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldNotAllowConsumersToRemoveContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO created = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(created);

        ApiClient consumerClient = this.createConsumerClient(adminClient, owner);
        assertForbidden(() -> consumerClient.ownerContent().remove(owner.getKey(), created.getId()));
    }

    @Test
    public void shouldUpdateProductsWhenAttachedContentIsRemoved() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(content);

        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        assertNotNull(product);

        product = adminClient.ownerProducts()
            .addContent(owner.getKey(), product.getId(), content.getId(), true);
        assertNotNull(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1)
            .map(ProductContentDTO::getContent)
            .contains(content);

        adminClient.ownerContent().remove(owner.getKey(), content.getId());
        assertNotFound(() -> adminClient.ownerContent().getOwnerContent(owner.getKey(), content.getId()));

        ProductDTO updatedProduct = adminClient.ownerProducts()
            .getProductByOwner(owner.getKey(), product.getId());

        assertNotNull(updatedProduct);
        assertNotEquals(product, updatedProduct);

        // Specifically, the UUID should have changed
        assertNotEquals(product.getUuid(), updatedProduct.getUuid());

        assertThat(updatedProduct.getProductContent())
            .isNotNull()
            .hasSize(0);
    }

    @Test
    public void shouldForceRegenerationOfEntitlementsProvidingChangedContent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // Create a pool to consume
        ContentDTO content = adminClient.ownerContent().createContent(owner.getKey(), Content.random());
        assertNotNull(content);

        ProductDTO product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        assertNotNull(product);

        product = adminClient.ownerProducts()
            .addContent(owner.getKey(), product.getId(), content.getId(), true);
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
        ContentDTO contentUpdate = Content.copy(content)
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

            ContentDTO content = Content.random();

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

            this.targetEntity = adminClient.ownerContent().getOwnerContent(owner.getKey(), content.getId());
            assertNotNull(this.targetEntity);
            assertEquals(content.getId(), this.targetEntity.getId());
        }

        @Test
        public void shouldNotAllowUpdatingLockedContent() throws Exception {
            ApiClient adminClient = ApiClients.admin();

            ContentDTO update = Content.copy(this.targetEntity)
                .name("updated content name")
                .label("updated label");

            assertForbidden(() -> adminClient.ownerContent()
                .updateContent(this.owner.getKey(), update.getId(), update));
        }

        @Test
        public void shouldNotAllowDeletingLockedContent() throws Exception {
            ApiClient adminClient = ApiClients.admin();

            assertForbidden(() -> adminClient.ownerContent()
                .remove(this.owner.getKey(), this.targetEntity.getId()));
        }
    }

}

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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;




public class OwnerContentResourceTest extends DatabaseTestFixture {

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        // Make sure we don't have any latent page requests in the context
        ResteasyContext.clearContextData();
    }

    @AfterEach
    public void cleanup() throws Exception {
        // Also cleanup after ourselves for other tests
        ResteasyContext.clearContextData();
    }

    public OwnerContentResource buildResource() {
        return this.injector.getInstance(OwnerContentResource.class);
    }

    @Test
    public void testGetContentById() {
        Owner owner = this.createOwner("test_owner");
        Content content = this.createContent("test_content", "test_content");

        ContentDTO output = this.buildResource()
            .getContentById(owner.getKey(), content.getId());

        assertNotNull(output);
        assertEquals(content.getId(), output.getId());
    }

    @Test
    public void testGetContentByIdNotFound() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .getContentById(owner.getKey(), "test_content"));
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), cdto.getId()));

        ContentDTO output = this.buildResource()
            .createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.contentCurator.getContentById(owner.getKey(), cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void createContentWhenContentAlreadyExistsInNamespace() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        update.setLabel("test-label");
        update.setType("test-test");
        update.setVendor("test-vendor");

        assertNotNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        ContentDTO output = this.buildResource()
            .createContent(owner.getKey(), update);

        assertNotNull(output);
        assertEquals(update.getId(), output.getId());
        assertEquals(update.getName(), output.getName());

        Content entity = this.contentCurator.getContentById(owner.getKey(), update.getId());
        assertNotNull(entity);
        assertEquals(update.getName(), entity.getName());
    }

    @Test
    public void testCreateContentWhenContentAlreadyExistsInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        update.setLabel("test-label");
        update.setType("test-test");
        update.setVendor("test-vendor");

        assertNotNull(this.contentCurator.getContentById(null, update.getId()));
        assertNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        assertThrows(BadRequestException.class, () -> this.buildResource()
            .createContent(owner.getKey(), update));
    }

    @Test
    public void testCreateContentInOrgUsingLongKey() {
        Owner owner = this.createOwner("test_owner".repeat(25));
        ContentDTO cdto = TestUtil.createContentDTO("test_content");
        cdto.setLabel("test-label");
        cdto.setType("test-test");
        cdto.setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), cdto.getId()));

        ContentDTO output = this.buildResource()
            .createContent(owner.getKey(), cdto);

        assertNotNull(output);
        assertEquals(cdto.getId(), output.getId());

        Content entity = this.contentCurator.getContentById(owner.getKey(), cdto.getId());
        assertNotNull(entity);
        assertEquals(cdto.getName(), entity.getName());
        assertEquals(cdto.getLabel(), entity.getLabel());
        assertEquals(cdto.getType(), entity.getType());
        assertEquals(cdto.getVendor(), entity.getVendor());
    }

    @Test
    public void testUpdateContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");
        assertNotNull(this.contentCurator.getContentById(owner.getKey(), update.getId()));

        ContentDTO output = this.buildResource()
            .updateContent(owner.getKey(), update.getId(), update);

        assertNotNull(output);
        assertEquals(update.getId(), output.getId());
        assertEquals(update.getName(), output.getName());

        Content entity = this.contentCurator.getContentById(owner.getKey(), update.getId());

        assertNotNull(entity);
        assertEquals(update.getName(), entity.getName());
    }

    @Test
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test_owner");
        ContentDTO update = TestUtil.createContentDTO("test_content", "updated_name");

        assertNull(this.contentCurator.resolveContentId(owner.getKey(), update.getId()));

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .updateContent(owner.getKey(), update.getId(), update));

        assertNull(this.contentCurator.resolveContentId(owner.getKey(), update.getId()));
    }

    @Test
    public void testCannotUpdateContentInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Content content = this.contentCurator.create(template);

        ContentDTO update = TestUtil.createContentDTO(content.getId(), "updated_name");

        assertThrows(ForbiddenException.class, () -> this.buildResource()
            .updateContent(owner.getKey(), update.getId(), update));
    }

    @Test
    public void testCannotUpdateContentInOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Content content = this.contentCurator.create(template);

        ContentDTO update = TestUtil.createContentDTO(content.getId(), "updated_name");

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .updateContent(owner1.getKey(), update.getId(), update));
    }

    @Test
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        ContentDTO cdto = TestUtil.createContentDTO("test_content");

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .updateContent("fake_owner_key", cdto.getId(), cdto));
    }

    @Test
    public void testRemoveContent() {
        Owner owner = this.createOwner("test_owner");
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        content = this.contentCurator.create(content);

        assertNotNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));

        this.buildResource()
            .removeContent(owner.getKey(), content.getId());

        assertNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));
        assertNull(this.contentCurator.get(content.getUuid()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testRemoveContentCannotRemoveContentWithParentProducts(boolean enabled) {
        Owner owner = this.createOwner("test_org");

        Content content = TestUtil.createContent("c1", "content1")
            .setNamespace(owner.getKey());

        Product parent = TestUtil.createProduct("parent", "parent")
            .addContent(content, enabled);

        this.contentCurator.create(content);
        this.productCurator.create(parent);

        Throwable throwable = assertThrows(BadRequestException.class,
            () -> this.buildResource().removeContent(owner.getKey(), content.getId()));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("referenced by one or more products");

        assertNotNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));
    }

    @Test
    public void testCannotRemoveContentFromGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Content content = this.contentCurator.create(template);

        assertThrows(ForbiddenException.class, () -> this.buildResource()
            .removeContent(owner.getKey(), content.getId()));
    }

    @Test
    public void testCannotRemoveContentFromOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content template = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Content content = this.contentCurator.create(template);

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .removeContent(owner1.getKey(), content.getId()));
    }

    @Test
    public void testRemoveContentWithNonExistentContent() {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class, () -> this.buildResource()
            .removeContent(owner.getKey(), "test_content"));
    }

    /**
     * Creates a set of products for use with the "testGetContentsByOwner..." family of tests below. Do
     * not make changes to these products unless you are updating the testing for the
     * OwnerContentResource.getContentsByOwner method, and do not use this method to set up data for any
     * other test!
     *
     * Note that this test data does not create full product graphs for products. Testing the definition
     * of "active" is left to a set of explicit tests which validates it in full.
     */
    private void createDataForEndpointQueryTesting() {
        List<Owner> owners = List.of(
            this.createOwner("owner1"),
            this.createOwner("owner2"),
            this.createOwner("owner3"));

        List<Product> globalProducts = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            List<Content> contents = new ArrayList<>();

            for (int c = 0; c < 2; ++c) {
                Content content = new Content(String.format("g-content-%d%s", i, (char) ('a' + c)))
                    .setName(String.format("global_content_%d%s", i, (char) ('a' + c)))
                    .setLabel(String.format("global content %d%s", i, (char) ('a' + c)))
                    .setType("test")
                    .setVendor("vendor");

                contents.add(this.contentCurator.create(content));
            }

            Product gprod = new Product()
                .setId("g-prod-" + i)
                .setName("global product " + i)
                .addContent(contents.get(0), true)
                .addContent(contents.get(1), false);

            globalProducts.add(this.productCurator.create(gprod));
        }

        for (int oidx = 1; oidx <= owners.size(); ++oidx) {
            Owner owner = owners.get(oidx - 1);

            List<Product> ownerProducts = new ArrayList<>();

            // create two custom products
            for (int i = 1; i <= 2; ++i) {
                List<Content> contents = new ArrayList<>();

                for (int c = 0; c < 2; ++c) {
                    Content cont = new Content(String.format("o%d-content-%d%s", oidx, i, (char) ('a' + c)))
                        .setName(String.format("%s_content_%d%s", owner.getKey(), i, (char) ('a' + c)))
                        .setLabel(String.format("%s content %d%s", owner.getKey(), i, (char) ('a' + c)))
                        .setType("test")
                        .setVendor("vendor")
                        .setNamespace(owner.getKey());

                    contents.add(this.contentCurator.create(cont));
                }

                Product cprod = new Product()
                    .setId(String.format("o%d-prod-%d", oidx, i))
                    .setName(String.format("%s product %d", owner.getKey(), i))
                    .setNamespace(owner.getKey())
                    .addContent(contents.get(0), true)
                    .addContent(contents.get(1), false);

                ownerProducts.add(this.productCurator.create(cprod));
            }

            // create some pools:
            // - one which references a global product
            // - one which references a custom product
            Pool globalPool = this.createPool(owner, globalProducts.get(0));
            Pool customPool = this.createPool(owner, ownerProducts.get(0));
        }
    }

    @Test
    public void testGetContentsByOwnerFetchesWithNoFiltering() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerDefaultsToActiveOnly() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "o2-content-1a", "o2-content-1b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null, null, null);

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_owner" })
    @NullAndEmptySource
    public void testGetContentsByOwnerErrorsWithInvalidOwners(String ownerKey) {
        this.createDataForEndpointQueryTesting();

        OwnerContentResource resource = this.buildResource();

        assertThrows(NotFoundException.class, () -> resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetContentsByOwnerFetchesWithIDFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> contentIds = List.of("g-content-2a", "g-content-2b", "o1-content-1a", "o2-content-1b",
            "o3-content-1a");
        List<String> expectedCids = List.of("g-content-2a", "g-content-2b", "o2-content-1b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, contentIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_id" })
    @NullAndEmptySource
    public void testGetContentsByOwnerFetchesWithInvalidIDs(String contentId) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> contentIds = Arrays.asList("o2-content-1a", "o2-content-1b", contentId);
        List<String> expectedCids = List.of("o2-content-1a", "o2-content-1b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, contentIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithLabelFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> contentLabels = List.of("global content 3a", "owner2 content 2b", "owner3 content 1a");
        List<String> expectedCids = List.of("g-content-3a", "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, contentLabels,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_label" })
    @NullAndEmptySource
    public void testGetContentsByOwnerFetchesWithInvalidLabels(String contentLabel) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> contentLabels = Arrays.asList("owner2 content 1a", contentLabel);
        List<String> expectedCids = List.of("o2-content-1a");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, contentLabels,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithOmitActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-2a", "g-content-2b", "g-content-3a", "g-content-3b",
            "o2-content-2a", "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.EXCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithOnlyActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "o2-content-1a", "o2-content-1b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithIncludeActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        // active = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerErrorsWithInvalidActiveFilter() {
        Owner owner = this.createOwner("test_org");

        OwnerContentResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getContentsByOwner(owner.getKey(), null, null,
            "invalid_type", Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetContentsByOwnerFetchesWithOmitCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithOnlyCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUSIVE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerFetchesWithIncludeCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        // custom = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerErrorsWithInvalidCustomFilter() {
        Owner owner = this.createOwner("test_org");

        OwnerContentResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getContentsByOwner(owner.getKey(), null, null,
            Inclusion.INCLUDE.name(), "invalid_type"));
    }

    @Test
    public void testGetContentsByOwnerFetchesWithMultipleFilters() {
        this.createDataForEndpointQueryTesting();

        // This test configures a bunch of filters which loosely resolve to the following:
        // - active global content: not custom, not inactive (active = only, custom = omit)
        // - in orgs 2 or 3
        // - matching the given list of content IDs (gc1a+b, gc2a+b, o1c1a+b, o2c1a+b, o3c2a+b)
        // - matching the given list of content labels (gc1a, gc2b, gc3a, o2c1b, o2c2a)
        //
        // These filters should be applied as an intersection, resulting in a singular match on gc1a

        String ownerKey = "owner2";
        List<String> contentIds = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "o1-content-1a", "o1-content-1b", "o2-content-1a", "o2-content-1b", "o3-content-2a",
            "o3-content-2b");
        List<String> contentlabels = List.of("global content 1a", "global content 2b", "global content 3a",
            "owner2 content 1b", "owner2 content 2a");
        String activeInclusion = Inclusion.EXCLUSIVE.name();
        String customInclusion = Inclusion.EXCLUDE.name();

        List<String> expectedCids = List.of("g-content-1a");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, contentIds, contentlabels,
            activeInclusion, customInclusion);

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testGetContentsByOwnerFetchesPagedResults(int pageSize) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b");

        int expectedPages = pageSize < expectedCids.size() ?
            (expectedCids.size() / pageSize) + (expectedCids.size() % pageSize != 0 ? 1 : 0) :
            1;

        OwnerContentResource resource = this.buildResource();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            // Set the context page request
            ResteasyContext.popContextData(PageRequest.class);
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(++pages)
                .setPerPage(pageSize));

            Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
                Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

            assertNotNull(output);
            List<String> receivedPids = output.map(ContentDTO::getId)
                .toList();

            if (receivedPids.isEmpty()) {
                break;
            }

            found.addAll(receivedPids);
        }

        assertEquals(expectedPages, pages);
        assertThat(found)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testGetContentsByOwnerFetchesOrderedResults(String field) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        Map<String, Comparator<Content>> comparatorMap = Map.of(
            "id", Comparator.comparing(Content::getId),
            "name", Comparator.comparing(Content::getName),
            "uuid", Comparator.comparing(Content::getUuid));

        List<String> expectedCids = this.contentCurator.resolveContentsByNamespace(ownerKey)
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Content::getId)
            .toList();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy(field));

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        // Note that this output needs to be ordered according to our expected ordering!
        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsByOwnerErrorsWithInvalidOrderingRequest() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy("invalid_field_name"));

        OwnerContentResource resource = this.buildResource();
        assertThrows(BadRequestException.class,
            () -> resource.getContentsByOwner(ownerKey, null, null, null, null));
    }

    /**
     * These tests verifies the definition of "active" is properly implemented, ensuring "active" is defined
     * as a product which is attached to a pool which has started and has not expired, or attached to
     * another active product (recursively).
     *
     * This definition is recursive in nature, so the effect is that it should consider any product that
     * is a descendant of a product attached to a non-future pool that hasn't yet expired.
     */
    @Test
    public void testGetActiveProductsOnlyConsidersActivePools() {
        // - "active" only considers pools which have started but not expired (start time < now() < end time)
        Owner owner = this.createOwner("test_org");

        List<Product> products = new ArrayList<>();

        for (int idx = 1; idx <= 3; ++idx) {
            Content content1 = this.createContent(String.format("content-%da", idx));
            Content content2 = this.createContent(String.format("content-%db", idx));

            Product product = new Product()
                .setId("prod-" + idx)
                .setName("product " + idx)
                .setNamespace(owner.getKey())
                .addContent(content1, true)
                .addContent(content2, false);

            products.add(this.productCurator.create(product));
        }

        Function<Integer, Date> days = (offset) -> TestUtil.createDateOffset(0, 0, offset);
        Date now = new Date();

        // Create three pools: expired, current (active), future
        Pool pool1 = this.createPool(owner, products.get(0), 1L, days.apply(-3), days.apply(-1));
        Pool pool2 = this.createPool(owner, products.get(1), 1L, days.apply(-1), days.apply(1));
        Pool pool3 = this.createPool(owner, products.get(2), 1L, days.apply(1), days.apply(3));

        // Active = exclusive should only find the active pool; future and expired pools should be omitted
        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(owner.getKey(), null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrder("content-2a", "content-2b");
    }

    @Test
    public void testGetActiveProductsAlsoConsidersDescendantsOfActivePoolProducts() {
        // - "active" includes descendants of products attached to a pool
        Owner owner = this.createOwner("test_org");

        List<Product> products = new ArrayList<>();

        for (int i = 0; i < 20; ++i) {
            Content content1 = this.createContent(String.format("c%da", i));
            Content content2 = this.createContent(String.format("c%db", i));

            Product product = new Product()
                .setId("p" + i)
                .setName("product " + i)
                .addContent(content1, true)
                .addContent(content2, false);

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
        products.get(0).addProvidedProduct(products.get(5));
        products.get(0).addProvidedProduct(products.get(6));

        products.get(1).addProvidedProduct(products.get(2));
        products.get(1).addProvidedProduct(products.get(3));

        products.get(3).addProvidedProduct(products.get(4));

        products.get(7).setDerivedProduct(products.get(8));
        products.get(7).addProvidedProduct(products.get(9));

        products.get(8).addProvidedProduct(products.get(10));
        products.get(8).addProvidedProduct(products.get(12));

        products.get(10).addProvidedProduct(products.get(11));

        products.get(12).addProvidedProduct(products.get(13));

        products.get(14).setDerivedProduct(products.get(15));

        products.get(15).setDerivedProduct(products.get(16));

        products.get(17).setDerivedProduct(products.get(18));

        // persist the products in reverse order so we don't hit any hibernate errors
        for (int i = products.size() - 1; i >= 0; --i) {
            this.productCurator.create(products.get(i));
        }

        Pool pool1 = this.createPool(owner, products.get(0));
        Pool pool2 = this.createPool(owner, products.get(7));
        Pool pool3 = this.createPool(owner, products.get(8));
        Pool pool4 = this.createPool(owner, products.get(19));

        List<String> expectedCids = List.of("c0a", "c0b", "c1a", "c1b", "c2a", "c2b", "c3a", "c3b", "c4a",
            "c4b", "c5a", "c5b", "c6a", "c6b", "c7a", "c7b", "c8a", "c8b", "c9a", "c9b", "c10a", "c10b",
            "c11a", "c11b", "c12a", "c12b", "c13a", "c13b", "c19a", "c19b");

        OwnerContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContentsByOwner(owner.getKey(), null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }
}

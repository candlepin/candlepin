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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
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



/**
 * ContentResourceTest
 */
public class ContentResourceTest extends DatabaseTestFixture {

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

    private ContentResource buildResource() {
        return this.injector.getInstance(ContentResource.class);
    }

    @Test
    public void getContentNull() {
        ContentResource resource = this.buildResource();

        assertThrows(NotFoundException.class, () -> resource.getContentByUuid("10"));
    }

    @Test
    public void getContent() {
        Content content = this.createContent("test_content");

        ContentResource resource = this.buildResource();

        ContentDTO output = resource.getContentByUuid(content.getUuid());

        assertThat(output)
            .isNotNull()
            .returns(content.getUuid(), ContentDTO::getUuid);
    }

    /**
     * Creates a set of products for use with the "testGetContents..." family of tests below. Do not make
     * changes to these products unless you are updating the testing for the ContentResource.getContents
     * method, and do not use this method to set up data for any other test!
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
    public void testGetContentsFetchesWithNoFiltering() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o1-content-1a", "o1-content-1b", "o1-content-2a",
            "o1-content-2b", "o2-content-1a", "o2-content-1b", "o2-content-2a", "o2-content-2b",
            "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsDefaultsToActiveOnly() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "o1-content-1a", "o1-content-1b",
            "o2-content-1a", "o2-content-1b", "o3-content-1a", "o3-content-1b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null, null, null);

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithOwnerFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> ownerKeys = List.of("owner2", "owner3");
        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o2-content-1a", "o2-content-1b", "o2-content-2a",
            "o2-content-2b", "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(ownerKeys, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_owner" })
    @NullAndEmptySource
    public void testGetContentsErrorsWithInvalidOwners(String invalidOwnerKey) {
        this.createDataForEndpointQueryTesting();

        List<String> ownerKeys = Arrays.asList("owner1", invalidOwnerKey);

        ContentResource resource = this.buildResource();

        assertThrows(NotFoundException.class, () -> resource.getContents(ownerKeys, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetContentsFetchesWithIDFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> contentIds = List.of("g-content-2a", "g-content-2b", "o1-content-1a", "o1-content-1b",
            "o3-content-1a", "o3-content-1b");
        List<String> expectedCids = List.of("g-content-2a", "g-content-2b", "o1-content-1a", "o1-content-1b",
            "o3-content-1a", "o3-content-1b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, contentIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_id" })
    @NullAndEmptySource
    public void testGetContentsFetchesWithInvalidIDs(String contentId) {
        this.createDataForEndpointQueryTesting();

        List<String> contentIds = Arrays.asList("o1-content-1a", "o1-content-1b", contentId);
        List<String> expectedCids = List.of("o1-content-1a", "o1-content-1b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, contentIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithLabelFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> contentLabels = List.of("global content 3a", "owner1 content 2b", "owner3 content 1a");
        List<String> expectedCids = List.of("g-content-3a", "o1-content-2b", "o3-content-1a");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, contentLabels,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_label" })
    @NullAndEmptySource
    public void testGetContentsFetchesWithInvalidLabels(String contentLabel) {
        this.createDataForEndpointQueryTesting();

        List<String> contentLabels = Arrays.asList("owner1 content 2a", contentLabel);
        List<String> expectedCids = List.of("o1-content-2a");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, contentLabels,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithOmitActiveFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-2a", "g-content-2b", "g-content-3a", "g-content-3b",
            "o1-content-2a", "o1-content-2b", "o2-content-2a", "o2-content-2b", "o3-content-2a",
            "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.EXCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithOnlyActiveFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "o1-content-1a", "o1-content-1b",
            "o2-content-1a", "o2-content-1b", "o3-content-1a", "o3-content-1b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithIncludeActiveFilter() {
        this.createDataForEndpointQueryTesting();

        // active = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o1-content-1a", "o1-content-1b", "o1-content-2a",
            "o1-content-2b", "o2-content-1a", "o2-content-1b", "o2-content-2a", "o2-content-2b",
            "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsErrorsWithInvalidActiveFilter() {
        ContentResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getContents(null, null, null,
            "invalid_type", Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetContentsFetchesWithOmitCustomFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithOnlyCustomFilter() {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("o1-content-1a", "o1-content-1b", "o1-content-2a",
            "o1-content-2b", "o2-content-1a", "o2-content-1b", "o2-content-2a", "o2-content-2b",
            "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUSIVE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsFetchesWithIncludeCustomFilter() {
        this.createDataForEndpointQueryTesting();

        // custom = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o1-content-1a", "o1-content-1b", "o1-content-2a",
            "o1-content-2b", "o2-content-1a", "o2-content-1b", "o2-content-2a", "o2-content-2b",
            "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsErrorsWithInvalidCustomFilter() {
        ContentResource resource = this.buildResource();

        assertThrows(BadRequestException.class, () -> resource.getContents(null, null, null,
            Inclusion.INCLUDE.name(), "invalid_type"));
    }

    @Test
    public void testGetContentsFetchesWithMultipleFilters() {
        this.createDataForEndpointQueryTesting();

        // This test configures a bunch of filters which loosely resolve to the following:
        // - active global content: not custom, not inactive (active = only, custom = omit)
        // - in orgs 2 or 3
        // - matching the given list of content IDs (gc1a+b, gc2a+b, o1c1a+b, o2c1a+b, o3c2a+b)
        // - matching the given list of content labels (gc1a, gc2b, gc3a, o2c1b, o2c2a)
        //
        // These filters should be applied as an intersection, resulting in a singular match on gc1a

        List<String> ownerKeys = List.of("owner2", "owner3");
        List<String> contentIds = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "o1-content-1a", "o1-content-1b", "o2-content-1a", "o2-content-1b", "o3-content-2a",
            "o3-content-2b");
        List<String> contentlabels = List.of("global content 1a", "global content 2b", "global content 3a",
            "owner2 content 1b", "owner2 content 2a");
        String activeInclusion = Inclusion.EXCLUSIVE.name();
        String customInclusion = Inclusion.EXCLUDE.name();

        List<String> expectedCids = List.of("g-content-1a");

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(ownerKeys, contentIds, contentlabels,
            activeInclusion, customInclusion);

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testGetContentsFetchesPagedResults(int pageSize) {
        this.createDataForEndpointQueryTesting();

        List<String> expectedCids = List.of("g-content-1a", "g-content-1b", "g-content-2a", "g-content-2b",
            "g-content-3a", "g-content-3b", "o1-content-1a", "o1-content-1b", "o1-content-2a",
            "o1-content-2b", "o2-content-1a", "o2-content-1b", "o2-content-2a", "o2-content-2b",
            "o3-content-1a", "o3-content-1b", "o3-content-2a", "o3-content-2b");

        int expectedPages = pageSize < expectedCids.size() ?
            (expectedCids.size() / pageSize) + (expectedCids.size() % pageSize != 0 ? 1 : 0) :
            1;

        ContentResource resource = this.buildResource();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            // Set the context page request
            ResteasyContext.popContextData(PageRequest.class);
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(++pages)
                .setPerPage(pageSize));

            Stream<ContentDTO> output = resource.getContents(null, null, null, Inclusion.INCLUDE.name(),
                Inclusion.INCLUDE.name());

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
    public void testGetContentsFetchesOrderedResults(String field) {
        this.createDataForEndpointQueryTesting();

        Map<String, Comparator<Content>> comparatorMap = Map.of(
            "id", Comparator.comparing(Content::getId),
            "name", Comparator.comparing(Content::getName),
            "uuid", Comparator.comparing(Content::getUuid));

        List<String> expectedCids = this.contentCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Content::getId)
            .toList();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy(field));

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null, Inclusion.INCLUDE.name(),
            Inclusion.INCLUDE.name());

        // Note that this output needs to be ordered according to our expected ordering!
        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyElementsOf(expectedCids);
    }

    @Test
    public void testGetContentsErrorsWithInvalidOrderingRequest() {
        this.createDataForEndpointQueryTesting();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy("invalid_field_name"));

        ContentResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.getContents(null, null, null, null, null));
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
        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null, Inclusion.EXCLUSIVE.name(),
            Inclusion.INCLUDE.name());

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

        ContentResource resource = this.buildResource();
        Stream<ContentDTO> output = resource.getContents(null, null, null, Inclusion.EXCLUSIVE.name(),
            Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedCids);
    }
}

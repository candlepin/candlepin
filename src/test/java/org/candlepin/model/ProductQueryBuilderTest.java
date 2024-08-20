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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

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
 * ProductQueryBuilderTest
 */
public class ProductQueryBuilderTest extends DatabaseTestFixture {

    private ProductQueryBuilder buildQueryBuilder() {
        return new ProductQueryBuilder(this.getEntityManagerProvider());
    }

    /**
     * Creates a set of products for use with the "testQueryBuilder..." family of tests below. Do not make
     * changes to these products unless you are updating the testing for the ProductQueryBuilder class
     * and do not use this method to set up data for any other test!
     *
     * Note that this test data does not create full product graphs for products. Testing the definition
     * of "active" is left to a set of explicit tests which validates it in full.
     */
    private void createDataForQueryBuilderTesting() {
        List<Owner> owners = List.of(
            this.createOwner("owner1"),
            this.createOwner("owner2"),
            this.createOwner("owner3"));

        List<Product> globalProducts = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            Product gprod = new Product()
                .setId("g-prod-" + i)
                .setName("global product " + i);

            globalProducts.add(this.productCurator.create(gprod));
        }

        for (int oidx = 1; oidx <= owners.size(); ++oidx) {
            Owner owner = owners.get(oidx - 1);

            List<Product> ownerProducts = new ArrayList<>();

            // create two custom products
            for (int i = 1; i <= 2; ++i) {
                Product cprod = new Product()
                    .setId(String.format("o%d-prod-%d", oidx, i))
                    .setName(String.format("%s product %d", owner.getKey(), i))
                    .setNamespace(owner.getKey());

                ownerProducts.add(this.productCurator.create(cprod));
            }

            // create some pools:
            // - one which references a global product
            // - one which references a custom product
            Pool globalPool = this.createPool(owner, globalProducts.get(0));
            Pool customPool = this.createPool(owner, ownerProducts.get(0));
        }
    }

    private void verifyQueryBuilderOutput(ProductQueryBuilder queryBuilder, List<String> expectedPids) {
        int count = (int) queryBuilder.getResultCount();
        List<Product> outputList = queryBuilder.getResultList();
        Stream<Product> outputStream = queryBuilder.getResultStream();

        assertThat(outputList)
            .isNotNull()
            .hasSize(count)
            .map(Product::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);

        assertThat(outputStream)
            .isNotNull()
            .hasSize(count)
            .map(Product::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }


    @Test
    public void testQueryBuilderFetchesEverythingWithNoFiltering() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByOwnersAsArray() {
        this.createDataForQueryBuilderTesting();

        List<Owner> owners = Stream.of("owner2", "owner3")
            .map(this.ownerCurator::getByKey)
            .toList();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOwners(owners.toArray(new Owner[0]));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByOwnersAsCollection() {
        this.createDataForQueryBuilderTesting();

        List<Owner> owners = Stream.of("owner2", "owner3")
            .map(this.ownerCurator::getByKey)
            .toList();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOwners(owners);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderSilentlyIgnoresNullOwnersInArray() {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOwners(null, null, null);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderSilentlyIgnoresNullOwnersInCollection() {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOwners(Arrays.asList(null, null, null));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByProductIdAsArray() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-2", "o1-prod-1", "o3-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds("g-prod-2", "o1-prod-1", "o3-prod-1");

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByProductIdAsCollection() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-2", "o1-prod-1", "o3-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds(List.of("g-prod-2", "o1-prod-1", "o3-prod-1"));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { " ",  "\t", "  \t  ", "\t  \t" })
    @NullAndEmptySource
    public void testQueryBuilderSilentlyIgnoresNullAndBlankProductIdsInArray(String pid) {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds(pid);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { " ",  "\t", "  \t  ", "\t  \t" })
    @NullAndEmptySource
    public void testQueryBuilderSilentlyIgnoresNullAndBlankProductIdsInCollection(String pid) {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds(Arrays.asList(pid));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersOnInvalidProductIdsInArray() {
        this.createDataForQueryBuilderTesting();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds("invalid_product_id");

        this.verifyQueryBuilderOutput(queryBuilder, List.of());
    }

    @Test
    public void testQueryBuilderFiltersOnInvalidProductIdsInCollection() {
        this.createDataForQueryBuilderTesting();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductIds(List.of("invalid_product_id"));

        this.verifyQueryBuilderOutput(queryBuilder, List.of());
    }

    @Test
    public void testQueryBuilderFiltersByProductNamesAsArray() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-3", "o1-prod-2", "o3-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductNames("owner1 product 2", "global product 3", "owner3 product 1");

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByProductNamesAsCollection() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-3", "o1-prod-2", "o3-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductNames(Arrays.asList("owner1 product 2", "global product 3", "owner3 product 1"));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { " ",  "\t", "  \t  ", "\t  \t" })
    @NullAndEmptySource
    public void testQueryBuilderSilentlyIgnoresNullAndBlankProductNamesInArray(String productName) {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductNames(productName);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { " ",  "\t", "  \t  ", "\t  \t" })
    @NullAndEmptySource
    public void testQueryBuilderSilentlyIgnoresNullAndBlankProductNamesInCollection(String productName) {
        this.createDataForQueryBuilderTesting();

        // Ignored parameters should result in an effective "fetch everything" with no other filters present
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductNames(Arrays.asList(productName));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersOnInvalidProductNamesInArray() {
        this.createDataForQueryBuilderTesting();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addProductNames("invalid_product_name");

        this.verifyQueryBuilderOutput(queryBuilder, List.of());
    }

    @Test
    public void testQueryBuilderFiltersWithExcludeActiveFilter() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-2", "g-prod-3", "o1-prod-2", "o2-prod-2", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setActive(Inclusion.EXCLUDE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersWithExclusiveActiveFilter() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-1", "o1-prod-1", "o2-prod-1", "o3-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setActive(Inclusion.EXCLUSIVE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersWithIncludeActiveFilter() {
        this.createDataForQueryBuilderTesting();

        // active = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setActive(Inclusion.INCLUDE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersWithExcludeCustomFilter() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setCustom(Inclusion.EXCLUDE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersWithExclusiveCustomFilter() {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("o1-prod-1", "o1-prod-2", "o2-prod-1", "o2-prod-2",
            "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setCustom(Inclusion.EXCLUSIVE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersWithIncludeCustomFilter() {
        this.createDataForQueryBuilderTesting();

        // custom = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .setCustom(Inclusion.INCLUDE);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderFiltersByMultipleFilters() {
        this.createDataForQueryBuilderTesting();

        // This test configures a bunch of filters which loosely resolve to the following:
        // - active global products: not custom, not inactive (active = only, custom = omit)
        // - in orgs 2 or 3
        // - matching the given list of product IDs (gp1, gp2, o1p1, o2p1, o3p2)
        // - matching the given list of product names (gp1, gp2, gp3, o2p1, o2p2)
        //
        // These filters should be applied as an intersection, resulting in a singular match on gp1

        List<Owner> owners = Stream.of("owner2", "owner3")
            .map(this.ownerCurator::getByKey)
            .toList();
        List<String> productIds = List.of("g-prod-1", "g-prod-2", "o1-prod-1", "o2-prod-1", "o3-prod-2");
        List<String> productNames = List.of("global product 1", "global product 2", "global product 3",
            "owner2 product 1", "owner2 product 2");
        Inclusion activeInclusion = Inclusion.EXCLUSIVE;
        Inclusion customInclusion = Inclusion.EXCLUDE;

        List<String> expectedPids = List.of("g-prod-1");

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOwners(owners)
            .addProductIds(productIds)
            .addProductNames(productNames)
            .setActive(activeInclusion)
            .setCustom(customInclusion);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testQueryBuilderPagesResultsByPage(int pageSize) {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        int expectedPages = pageSize < expectedPids.size() ?
            (expectedPids.size() / pageSize) + (expectedPids.size() % pageSize != 0 ? 1 : 0) :
            1;

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            queryBuilder.setPage(++pages, pageSize);

            List<String> receivedPids = queryBuilder.getResultStream()
                .map(Product::getId)
                .toList();

            if (receivedPids.isEmpty()) {
                break;
            }

            found.addAll(receivedPids);
        }

        assertEquals(expectedPages, pages);
        assertThat(found)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testQueryBuilderPagesResultsByOffsetAndLimit(int pageSize) {
        this.createDataForQueryBuilderTesting();

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o1-prod-1", "o1-prod-2",
            "o2-prod-1", "o2-prod-2", "o3-prod-1", "o3-prod-2");

        int expectedPages = pageSize < expectedPids.size() ?
            (expectedPids.size() / pageSize) + (expectedPids.size() % pageSize != 0 ? 1 : 0) :
            1;

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            queryBuilder.setOffset(pages++ * pageSize)
                .setLimit(pageSize);

            List<String> receivedPids = queryBuilder.getResultStream()
                .map(Product::getId)
                .toList();

            if (receivedPids.isEmpty()) {
                break;
            }

            found.addAll(receivedPids);
        }

        assertEquals(expectedPages, pages);
        assertThat(found)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, -100, -10000 })
    public void testQueryBuilderErrorsWithInvalidOffset(int offset) {
        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        assertThrows(IllegalArgumentException.class, () -> queryBuilder.setOffset(offset));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -100, -10000 })
    public void testQueryBuilderErrorsWithInvalidLimit(int limit) {
        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        assertThrows(IllegalArgumentException.class, () -> queryBuilder.setLimit(limit));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -100, -10000 })
    public void testQueryBuilderErrorsWithInvalidPage(int page) {
        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        assertThrows(IllegalArgumentException.class, () -> queryBuilder.setPage(page, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -100, -10000 })
    public void testQueryBuilderErrorsWithInvalidPageSize(int pageSize) {
        ProductQueryBuilder queryBuilder = this.buildQueryBuilder();

        assertThrows(IllegalArgumentException.class, () -> queryBuilder.setPage(1, pageSize));
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testQueryBuilderOrdersResultsByNameAscending(String field) {
        this.createDataForQueryBuilderTesting();

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(field, false);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testQueryBuilderOrdersResultsByNameDescending(String field) {
        this.createDataForQueryBuilderTesting();

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(field, true);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testQueryBuilderOrdersResultsByOrderAscending(String field) {
        this.createDataForQueryBuilderTesting();

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(new QueryBuilder.Order(field, false));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testQueryBuilderOrdersResultsByOrderDescending(String field) {
        this.createDataForQueryBuilderTesting();

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(new QueryBuilder.Order(field, true));

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @Test
    public void testQueryBuilderOrdersResultsByMultipleFields() {
        this.createDataForQueryBuilderTesting();

        Comparator<Product> updateComparator = Comparator.comparing(Product::getUpdated);
        Comparator<Product> nameComparator = Comparator.comparing(Product::getName);
        Comparator<Product> idComparator = Comparator.comparing(Product::getId);
        Comparator<Product> uuidComparator = Comparator.comparing(Product::getUuid);

        Comparator<Product> combined = updateComparator.reversed()
            .thenComparing(nameComparator)
            .thenComparing(idComparator)
            .thenComparing(uuidComparator.reversed());

        List<String> expectedPids = this.productCurator.listAll()
            .stream()
            .sorted(combined)
            .map(Product::getId)
            .toList();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(new QueryBuilder.Order("updated", true))
            .addOrder("name", false)
            .addOrder(new QueryBuilder.Order("id", false))
            .addOrder("uuid", true);

        this.verifyQueryBuilderOutput(queryBuilder, expectedPids);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testQueryBuilderErrorsWithInvalidOrderingByField(boolean reverse) {
        this.createDataForQueryBuilderTesting();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder("invalid_field_name", reverse);

        // At the time of writing, invalid keys don't trigger a failure until the query itself runs,
        // so we have to attempt to fetch values to see the error.
        assertThrows(InvalidOrderKeyException.class, () -> queryBuilder.getResultList());
        assertThrows(InvalidOrderKeyException.class, () -> queryBuilder.getResultStream());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testQueryBuilderErrorsWithInvalidOrderingByOrder(boolean reverse) {
        this.createDataForQueryBuilderTesting();

        ProductQueryBuilder queryBuilder = this.buildQueryBuilder()
            .addOrder(new QueryBuilder.Order("invalid_field_name", reverse));

        // At the time of writing, invalid keys don't trigger a failure until the query itself runs,
        // so we have to attempt to fetch values to see the error.
        assertThrows(InvalidOrderKeyException.class, () -> queryBuilder.getResultList());
        assertThrows(InvalidOrderKeyException.class, () -> queryBuilder.getResultStream());
    }

    // These tests verify the definition of "active" is properly implemented, ensuring "active" is defined
    // as a product which is attached to a pool which has started and has not expired, or attached to
    // another active product (recursively).
    //
    // This definition is recursive in nature, so the effect is that it should consider any product that
    // is a descendant of a product attached to a non-future pool that hasn't yet expired.

    @Test
    public void testQueryBuilderActiveFilterOnlyConsidersActivePools() {
        // - "active" only considers pools which have started but not expired (start time < now() < end time)
        Owner owner = this.createOwner("test_org");

        Product prod1 = this.createProduct("prod1");
        Product prod2 = this.createProduct("prod2");
        Product prod3 = this.createProduct("prod3");

        Function<Integer, Date> days = (offset) -> TestUtil.createDateOffset(0, 0, offset);

        // Create three pools: expired, current (active), future
        Pool pool1 = this.createPool(owner, prod1, 1L, days.apply(-3), days.apply(-1));
        Pool pool2 = this.createPool(owner, prod2, 1L, days.apply(-1), days.apply(1));
        Pool pool3 = this.createPool(owner, prod3, 1L, days.apply(1), days.apply(3));

        // Active = exclusive should only find the active pool; future and expired pools should be omitted
        Stream<Product> output = this.buildQueryBuilder()
            .setActive(Inclusion.EXCLUSIVE)
            .getResultStream();

        assertThat(output)
            .isNotNull()
            .map(Product::getId)
            .singleElement()
            .isEqualTo(prod2.getId());
    }

    @Test
    public void testGetActiveProductsAlsoConsidersDescendantsOfActivePoolProducts() {
        // - "active" includes descendants of products attached to a pool
        Owner owner = this.createOwner("test_org");

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 20; ++i) {
            Product product = new Product()
                .setId("p" + i)
                .setName("product " + i);

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

        List<String> expectedPids = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9",
            "p10", "p11", "p12", "p13", "p19");

        Stream<Product> output = this.buildQueryBuilder()
            .setActive(Inclusion.EXCLUSIVE)
            .getResultStream();

        assertThat(output)
            .isNotNull()
            .map(Product::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

}

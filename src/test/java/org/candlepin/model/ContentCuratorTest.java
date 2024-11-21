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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;



/**
 * ContentCuratorTest
 */
public class ContentCuratorTest extends DatabaseTestFixture {

    private Product createProductWithContent(Owner owner, Content... contents) {
        String productId = "test-product-" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, productId);

        if (contents != null && contents.length > 0) {
            for (Content content : contents) {
                product.addContent(content, true);
            }
        }

        return this.createProduct(product);
    }

    @Test
    public void testCannotPersistIdenticalProducts() {
        Content c1 = new Content("test-content")
            .setName("test-content")
            .setType("content-type")
            .setLabel("content-label")
            .setVendor("content-vendor");

        this.contentCurator.create(c1, true);
        this.contentCurator.clear();

        Content c2 = new Content("test-content")
            .setName("test-content")
            .setType("content-type")
            .setLabel("content-label")
            .setVendor("content-vendor");

        assertThrows(PersistenceException.class, () -> this.contentCurator.create(c2, true));
    }

    /**
     * Creates and persists a very basic content using the given content ID and namespace. If the
     * namespace is null or empty, the content will be created in the global namespace.
     *
     * @param contentId
     *  the string to use for the content ID and name
     *
     * @param namespace
     *  the namespace in which to create the content
     *
     * @return
     *  the newly created content
     */
    private Content createNamespacedContent(String contentId, String namespace) {
        Content content = new Content(contentId)
            .setName(contentId)
            .setLabel(contentId + "-label")
            .setType(contentId + "-type")
            .setVendor(contentId + "-vendor")
            .setNamespace(namespace);

        return this.createContent(content);
    }

    private Content createContentWithRequiredProductIds(String contentId,
        Collection<String> requiredProductIds) {

        Content content = new Content(contentId)
            .setName(contentId)
            .setLabel(contentId + "-label")
            .setType(contentId + "-type")
            .setVendor(contentId + "-vendor");

        if (requiredProductIds != null) {
            content.setModifiedProductIds(requiredProductIds);
        }

        return this.createContent(content);
    }

    private static Stream<Arguments> lockModeTypeSource() {
        return Stream.of(
            Arguments.of((LockModeType) null),
            Arguments.of(LockModeType.NONE),
            Arguments.of(LockModeType.OPTIMISTIC),
            Arguments.of(LockModeType.PESSIMISTIC_READ),
            Arguments.of(LockModeType.PESSIMISTIC_WRITE),
            Arguments.of(LockModeType.READ),
            Arguments.of(LockModeType.WRITE));
    }

    @Test
    public void testGetContentById() {
        Content content1 = this.createNamespacedContent("test_content-1", null); // global namespace
        Content content2 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content3 = this.createNamespacedContent("test_content-3", "namespace-2");

        Content output = this.contentCurator.getContentById(content2.getNamespace(), content2.getId());
        assertEquals(content2, output);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "namespace-1")
    public void testGetContentByIdRestrictsLookupToNamespace(String namespace) {
        String id = "test_content-1";

        Content content1 = this.createNamespacedContent(id, namespace);
        Content content2 = this.createNamespacedContent(id, "namespace-2");

        if (namespace != null && !namespace.isEmpty()) {
            Content content3 = this.createNamespacedContent(id, null);
        }

        Content output = this.contentCurator.getContentById(namespace, id);
        assertEquals(content1, output);
    }

    @Test
    public void testGetContentByIdDoesNotFallBackToGlobalNamespace() {
        Content content1 = this.createNamespacedContent("test_content-1", null);

        Content output = this.contentCurator.getContentById("namespace-1", content1.getId());
        assertNull(output);
    }

    @Test
    public void testGetContentByIdHandlesNullContentId() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-1", "namespace-1");

        Content output = this.contentCurator.getContentById("namespace-1", null);
        assertNull(output);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetContentByIdWithLockMode(LockModeType lockMode) {
        String id = "test_content-1";

        Content content1 = this.createNamespacedContent(id, null); // global namespace
        Content content2 = this.createNamespacedContent(id, "namespace-1");
        Content content3 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content4 = this.createNamespacedContent(id, "namespace-2");

        Content output = this.contentCurator.getContentById(content2.getNamespace(), id, lockMode);
        assertEquals(content2, output);
    }

    @Test
    public void testGetContentsByIds() {
        Content content1 = this.createNamespacedContent("test_content-1", null); // global namespace
        Content content2 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content3 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content4 = this.createNamespacedContent("test_content-4", "namespace-2");

        String namespace = "namespace-1";
        List<String> ids = List.of(content2.getId(), content3.getId(), content4.getId());

        Map<String, Content> expected = Map.of(
            content2.getId(), content2,
            content3.getId(), content3);

        Map<String, Content> output = this.contentCurator.getContentsByIds(namespace, ids);
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "namespace-1")
    public void testGetContentsByIdsRestrictsLookupToNamespace(String namespace) {
        Map<String, List<Content>> contentMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Content content1 = this.createNamespacedContent("test_content-1", ns);
            Content content2 = this.createNamespacedContent("test_content-2", ns);
            Content content3 = this.createNamespacedContent("test_content-3", ns);

            contentMap.put(ns, List.of(content1, content2, content3));
        }

        List<String> ids = List.of("test_content-1", "test_content-3", "test_content-404");

        Map<String, Content> expected = contentMap.get(namespace != null ? namespace : "")
            .stream()
            .filter(entity -> ids.contains(entity.getId()))
            .collect(Collectors.toMap(Content::getId, Function.identity()));

        Map<String, Content> output = this.contentCurator.getContentsByIds(namespace, ids);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testGetContentsByIdsDoesNotFallBackToGlobalNamespace() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-2", "namespace-1");

        Map<String, Content> output = this.contentCurator.getContentsByIds("namespace-1",
            List.of(content1.getId()));

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetContentsByIdsHandlesNullCollection() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-1", "namespace-1");

        Map<String, Content> output = this.contentCurator.getContentsByIds("namespace-1", null);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetContentsByIdsHandlesNullElements() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-1", "namespace-1");

        List<String> ids = Arrays.asList("test_content-1", null);

        Map<String, Content> output = this.contentCurator.getContentsByIds("namespace-1", ids);

        assertThat(output)
            .isNotNull()
            .hasSize(1)
            .containsEntry(content2.getId(), content2);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetContentsByIdsWithLockMode(LockModeType lockMode) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns1 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content3ns2 = this.createNamespacedContent("test_content-3", "namespace-2");

        List<String> ids = List.of("test_content-1", "test_content-2", "test_content-404");
        Map<String, Content> expected = Map.of(
            content1ns1.getId(), content1ns1,
            content2ns1.getId(), content2ns1);

        Map<String, Content> output = this.contentCurator.getContentsByIds("namespace-1", ids, lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "namespace-1", "bad_namespace" })
    public void testGetContentsByNamespaceRestrictsLookupToNamespace(String namespace) {
        Map<String, List<Content>> contentMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Content content1 = this.createNamespacedContent("test_content-1", ns);
            Content content2 = this.createNamespacedContent("test_content-2", ns);
            Content content3 = this.createNamespacedContent("test_content-3", ns);

            contentMap.put(ns, List.of(content1, content2, content3));
        }

        List<Content> expected = contentMap.getOrDefault(namespace != null ? namespace : "", List.of());

        List<Content> output = this.contentCurator.getContentsByNamespace(namespace);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetContentsByNamespaceWithLockMode(LockModeType lockMode) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content3ns2 = this.createNamespacedContent("test_content-3", "namespace-2");

        List<Content> expected = List.of(content1ns1, content3ns1);

        List<Content> output = this.contentCurator.getContentsByNamespace("namespace-1", lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveContentId() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns1 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");

        Content output = this.contentCurator.resolveContentId(content1ns1.getNamespace(),
            content2ns1.getId());

        assertEquals(content2ns1, output);
    }

    @Test
    public void testResolveContentIdFallsBackToGlobalNamespace() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");

        Content output = this.contentCurator.resolveContentId("namespace-1", content1nsG.getId());
        assertEquals(content1nsG, output);
    }

    @Test
    public void testResolveContentIdDoesNotFallbackFromGlobalNamespace() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");

        Content output = this.contentCurator.resolveContentId(null, content1nsG.getId());
        assertEquals(content1nsG, output);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_content-404" })
    public void testResolveContentIdHandlesInvalidContentIds(String id) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");

        Content output = this.contentCurator.resolveContentId("namespace-1", id);
        assertNull(output);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveContentIdWithLockMode(LockModeType lockMode) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns1 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");

        Content output = this.contentCurator.resolveContentId("namespace-1", "test_content-1", lockMode);

        assertEquals(content1ns1, output);
    }

    @Test
    public void testResolveContentIds() {
        Content content1 = this.createNamespacedContent("test_content-1", null); // global namespace
        Content content2 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content3 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content4 = this.createNamespacedContent("test_content-4", "namespace-2");

        String namespace = "namespace-1";
        List<String> ids = List.of(content2.getId(), content3.getId(), content4.getId());

        Map<String, Content> expected = Map.of(
            content2.getId(), content2,
            content3.getId(), content3);

        Map<String, Content> output = this.contentCurator.resolveContentIds(namespace, ids);
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "namespace-1", "namespace-404" })
    public void testResolveContentIdsPrefersSpecifiedNamespaceOverGlobal(String namespace) {
        Map<String, List<Content>> contentMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Content content1 = this.createNamespacedContent("test_content-1", ns);
            Content content2 = this.createNamespacedContent("test_content-2", ns);
            Content content3 = this.createNamespacedContent("test_content-3", ns);

            contentMap.put(ns, List.of(content1, content2, content3));
        }

        List<String> ids = List.of("test_content-1", "test_content-3", "test_content-404");

        List<Content> expectedContents = contentMap.get(namespace != null ? namespace : "");
        if (expectedContents == null) {
            expectedContents = contentMap.get("");
        }

        Map<String, Content> expected = expectedContents.stream()
            .filter(entity -> ids.contains(entity.getId()))
            .collect(Collectors.toMap(Content::getId, Function.identity()));

        Map<String, Content> output = this.contentCurator.resolveContentIds(namespace, ids);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testResolveContentIdsCanFallBackToGlobalNamespace() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-2", "namespace-1");

        Map<String, Content> output = this.contentCurator.resolveContentIds("namespace-1",
            List.of(content1.getId(), content2.getId()));

        assertThat(output)
            .isNotNull()
            .hasSize(2)
            .containsEntry(content1.getId(), content1)
            .containsEntry(content2.getId(), content2);
    }

    @Test
    public void testResolveContentIdsHandlesNullCollection() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-1", "namespace-1");

        Map<String, Content> output = this.contentCurator.resolveContentIds("namespace-1", null);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testResolveContentIdsHandlesNullElements() {
        Content content1 = this.createNamespacedContent("test_content-1", null);
        Content content2 = this.createNamespacedContent("test_content-1", "namespace-1");

        List<String> ids = Arrays.asList("test_content-1", null);

        Map<String, Content> output = this.contentCurator.resolveContentIds("namespace-1", ids);

        assertThat(output)
            .isNotNull()
            .hasSize(1)
            .containsEntry(content2.getId(), content2);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveContentIdsWithLockMode(LockModeType lockMode) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content3ns2 = this.createNamespacedContent("test_content-3", "namespace-2");

        List<String> ids = List.of("test_content-1", "test_content-2", "test_content-404");
        Map<String, Content> expected = Map.of(
            content1ns1.getId(), content1ns1,
            content2nsG.getId(), content2nsG);

        Map<String, Content> output = this.contentCurator.resolveContentIds("namespace-1", ids, lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testResolveContentsByNamespaceRestrictsLookupToNamespace() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns1 = this.createNamespacedContent("test_content-2", "namespace-1");
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");
        Content content3ns2 = this.createNamespacedContent("test_content-3", "namespace-2");

        List<Content> expected = List.of(content1ns1, content2ns1, content3ns1);

        Collection<Content> output = this.contentCurator.resolveContentsByNamespace("namespace-1");

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveContentsByNamespaceFallsBackToGlobalNamespace() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");

        List<Content> expected = List.of(content1ns1, content2nsG, content3ns1);

        Collection<Content> output = this.contentCurator.resolveContentsByNamespace("namespace-1");

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveContentsByNamespaceWithGlobalNamespaceHasNoFallback() {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content3nsG = this.createNamespacedContent("test_content-3", null);
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");

        List<Content> expected = List.of(content1nsG, content2nsG, content3nsG);

        Collection<Content> output = this.contentCurator.resolveContentsByNamespace(null);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveContentsByNamespaceWithLockMode(LockModeType lockMode) {
        Content content1nsG = this.createNamespacedContent("test_content-1", null);
        Content content1ns1 = this.createNamespacedContent("test_content-1", "namespace-1");
        Content content1ns2 = this.createNamespacedContent("test_content-1", "namespace-2");
        Content content2nsG = this.createNamespacedContent("test_content-2", null);
        Content content2ns2 = this.createNamespacedContent("test_content-2", "namespace-2");
        Content content3ns1 = this.createNamespacedContent("test_content-3", "namespace-1");

        List<Content> expected = List.of(content1ns1, content2nsG, content3ns1);

        Collection<Content> output = this.contentCurator.resolveContentsByNamespace("namespace-1", lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testBulkDeleteByUuids() {
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        this.contentCurator.flush();
        this.contentCurator.clear();

        assertIterableEquals(List.of(content1, content2, content3), this.contentCurator
            .getContentsByUuids(List.of(content1.getUuid(), content2.getUuid(), content3.getUuid())));

        this.contentCurator.flush();
        this.contentCurator.clear();

        int output = this.contentCurator.bulkDeleteByUuids(Set.of(content1.getUuid(), content2.getUuid()));
        assertEquals(2, output);

        assertIterableEquals(List.of(content3), this.contentCurator
            .getContentsByUuids(List.of(content1.getUuid(), content2.getUuid(), content3.getUuid())));
    }

    @Test
    public void testBulkDeleteByUuidsCascadesToChildren() {
        // Impl note: aside from the required fields, we *must* set some modified/required product IDs
        // to populate the child collection table for this test.

        Content content1 = new Content("test_content-1")
            .setName("test content 1")
            .setLabel("test_content-1")
            .setType("content-type")
            .setVendor("test vendor")
            .setModifiedProductIds(Set.of("pid1", "pid2", "pid3"));

        Content content2 = new Content("test_content-2")
            .setName("test content 2")
            .setLabel("test_content-2")
            .setType("content-type")
            .setVendor("test vendor")
            .setModifiedProductIds(Set.of("pid1", "pid2", "pid3"));

        content1 = this.contentCurator.create(content1);
        content2 = this.contentCurator.create(content2);

        this.contentCurator.flush();
        this.contentCurator.clear();

        int output = this.contentCurator.bulkDeleteByUuids(Set.of(content1.getUuid(), content2.getUuid()));
        assertEquals(2, output);

        assertNull(this.contentCurator.get(content1.getUuid()));
        assertNull(this.contentCurator.get(content2.getUuid()));
    }

    @Test
    public void testBulkDeleteByUuidsHandlesEmptyInput() {
        int output = this.contentCurator.bulkDeleteByUuids(Set.of());
        assertEquals(0, output);
    }

    @Test
    public void testBulkDeleteByUuidsHandlesNullInput() {
        int output = this.contentCurator.bulkDeleteByUuids(null);
        assertEquals(0, output);
    }

    @Test
    public void testGetProductsReferencingContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProductWithContent(owner1, content1);
        Product product2 = this.createProductWithContent(owner2, content2, content3);
        Product product3 = this.createProductWithContent(owner1);

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertEquals(3, output.size());

        assertTrue(output.containsKey(content1.getUuid()));
        assertEquals(Set.of(product1.getUuid()), output.get(content1.getUuid()));

        assertTrue(output.containsKey(content2.getUuid()));
        assertEquals(Set.of(product2.getUuid()), output.get(content2.getUuid()));

        assertTrue(output.containsKey(content3.getUuid()));
        assertEquals(Set.of(product2.getUuid()), output.get(content3.getUuid()));
    }

    @Test
    public void testGetProductsRefrencingContentWithMultipleReferences() {
        Owner owner = this.createOwner();

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProductWithContent(owner, content1, content2);
        Product product2 = this.createProductWithContent(owner, content2, content3);
        Product product3 = this.createProductWithContent(owner, content3, content1);

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertEquals(3, output.size());

        assertTrue(output.containsKey(content1.getUuid()));
        assertEquals(Set.of(product1.getUuid(), product3.getUuid()), output.get(content1.getUuid()));

        assertTrue(output.containsKey(content2.getUuid()));
        assertEquals(Set.of(product1.getUuid(), product2.getUuid()), output.get(content2.getUuid()));

        assertTrue(output.containsKey(content3.getUuid()));
        assertEquals(Set.of(product2.getUuid(), product3.getUuid()), output.get(content3.getUuid()));
    }

    @Test
    public void testGetProductsReferencingContentWithNoReferences() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsReferencingContentHandlesEmptyInput() {
        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(Set.of());
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsReferencingContentHandlesNullInput() {
        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    private Product createProductWithContent(String productId, int contentCount) {
        Product product = new Product()
            .setId(productId)
            .setName(productId);

        for (int i = 0; i < contentCount; ++i) {
            String cid = productId + "_content-" + i;
            Content content = this.createContent(cid);

            product.addContent(content, true);
        }

        return this.createProduct(product);
    }

    @Test
    public void testGetChildrenContentOfProductsByUuids() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithContent("p1", 0);
        Product product2 = this.createProductWithContent("p2", 1);
        Product product3 = this.createProductWithContent("p3", 2);
        Product product4 = this.createProductWithContent("p4", 3);

        List<Product> products = List.of(product1, product2, product3, product4);

        Set<Content> expected = products.stream()
            .flatMap(product -> product.getProductContent().stream())
            .map(ProductContent::getContent)
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testGetChildrenContentOfProductsByUuidsIgnoresInvalidProductUuids() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithContent("p1", 1);
        Product product2 = this.createProductWithContent("p2", 2);
        Product product3 = this.createProductWithContent("p3", 3);
        Product product4 = this.createProductWithContent("p4", 4);

        List<Product> products = List.of(product2, product3);

        Set<Content> expected = products.stream()
            .flatMap(product -> product.getProductContent().stream())
            .map(ProductContent::getContent)
            .collect(Collectors.toSet());

        List<String> input = Arrays.asList(product2.getUuid(), "invalid", product3.getUuid(), null);

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetChildrenContentOfProductsByUuidsHandlesNullAndEmptyCollections(List<String> input) {
        // Create some products just to ensure it doesn't pull random existing things for this case
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithContent("p1", 1);
        Product product2 = this.createProductWithContent("p2", 2);
        Product product3 = this.createProductWithContent("p3", 3);
        Product product4 = this.createProductWithContent("p4", 4);

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetActiveContentByOwnerEmpty() {
        Owner owner = createOwner();

        List<ProductContent> activeContentByOwner = contentCurator.getActiveContentByOwner(owner.getId());

        assertThat(activeContentByOwner)
            .isEmpty();
    }

    @Test
    public void testGetActiveContentByOwner() {
        Owner owner = createOwner();
        Content content1 = createContent();
        Content content2 = createContent();
        Content content3 = createContent();

        createProductContent(owner, true, content1, content2, content3);

        this.contentCurator.flush();
        this.contentCurator.clear();
        List<ProductContent> activeContentByOwner = contentCurator.getActiveContentByOwner(owner.getId());

        assertThat(activeContentByOwner).hasSize(3)
            .extracting(ProductContent::getContent)
            .containsExactlyInAnyOrder(content1, content2, content3);

        assertThat(activeContentByOwner)
            .extracting(ProductContent::isEnabled)
            .containsOnly(true);
    }

    @Test
    public void testGetActiveContentByOwnerMultipleOwners() {
        Owner owner1 = createOwner();
        Owner owner2 = createOwner();
        Content content1 = createContent();
        Content content2 = createContent();
        Content content3 = createContent();

        createProductContent(owner1, true, content1);
        createProductContent(owner2, false, content2, content3);

        this.contentCurator.flush();
        this.contentCurator.clear();
        List<ProductContent> activeContentByOwner1 = contentCurator.getActiveContentByOwner(owner1.getId());

        this.contentCurator.clear();
        List<ProductContent> activeContentByOwner2 = contentCurator.getActiveContentByOwner(owner2.getId());

        assertThat(activeContentByOwner1)
            .hasSize(1)
            .extracting(ProductContent::getContent)
            .containsExactly(content1);
        assertThat(activeContentByOwner1)
            .extracting(ProductContent::isEnabled)
            .containsOnly(true);

        assertThat(activeContentByOwner2)
            .hasSize(2)
            .extracting(ProductContent::getContent)
            .containsExactlyInAnyOrder(content2, content3);
        assertThat(activeContentByOwner2)
            .extracting(ProductContent::isEnabled)
            .containsOnly(false);
    }

    @Test
    public void testGetRequiredProductIds() {
        Owner owner = this.createOwner();

        Content content1 = this.createContentWithRequiredProductIds("test_content-1",
            Set.of("p1", "p2", "p3"));

        Content content2 = this.createContentWithRequiredProductIds("test_content-2",
            Set.of("p2", "p3", "p4"));

        Content content3 = this.createContentWithRequiredProductIds("test_content-3",
            Set.of("pA", "pB", "pC"));

        Map<String, Set<String>> expected = Map.of(
            content1.getUuid(), content1.getRequiredProductIds(),
            content2.getUuid(), content2.getRequiredProductIds(),
            content3.getUuid(), content3.getRequiredProductIds());

        Map<String, Set<String>> output = this.contentCurator.getRequiredProductIds(expected.keySet());

        assertThat(output)
            .isNotNull()
            .hasSize(expected.size())
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testGetRequiredProductIdsExcludesInvalidConsumerUuids() {
        Content content1 = this.createContentWithRequiredProductIds("test_content-1",
            Set.of("p1", "p2", "p3"));

        Map<String, Set<String>> expected = Map.of(
            content1.getUuid(), content1.getRequiredProductIds());

        List<String> cuuids = Arrays.asList(content1.getUuid(), null, "", "invalid uuid");

        Map<String, Set<String>> output = this.contentCurator.getRequiredProductIds(cuuids);

        assertThat(output)
            .isNotNull()
            .hasSize(expected.size())
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetRequiredProductIdsWithNoInputReturnsEmptyMap(Collection<String> cuuids) {
        Map<String, Set<String>> output = this.contentCurator.getRequiredProductIds(cuuids);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testContentHasParentProductsWithoutParentProduct() {
        Content content = this.createContent();

        assertFalse(this.contentCurator.contentHasParentProducts(content));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testContentHasParentProductsWithSingleParentProduct(boolean enabled) {
        Content content = this.createContent();

        Product product = this.createProduct().addContent(content, enabled);
        this.productCurator.merge(product);

        assertTrue(this.contentCurator.contentHasParentProducts(content));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testContentHasParentProductsWithMultipleParentProducts(boolean enabled) {
        Content content = this.createContent();

        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        for (Product product : List.of(product1, product2, product3)) {
            product.addContent(content, enabled);
            this.productCurator.merge(product);
        }

        assertTrue(this.contentCurator.contentHasParentProducts(content));
    }

    @Test
    public void testContentHasParentProductsWithMultipleParentProductsMixedEnablement() {
        Content content = this.createContent();

        Product product1 = this.createProduct().addContent(content, true);
        this.productCurator.merge(product1);
        Product product2 = this.createProduct().addContent(content, false);
        this.productCurator.merge(product2);
        Product product3 = this.createProduct().addContent(content, true);
        this.productCurator.merge(product3);

        assertTrue(this.contentCurator.contentHasParentProducts(content));
    }

    @Test
    public void testContentHasParentProductsWithNullContent() {
        assertFalse(this.contentCurator.contentHasParentProducts(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_uuid" })
    public void testContentHasParentProductsWithUnmanagedContent(String uuid) {
        Content content = new Content()
            .setUuid(uuid);

        // This should not throw an exception or otherwise fail
        assertFalse(this.contentCurator.contentHasParentProducts(content));
    }
}

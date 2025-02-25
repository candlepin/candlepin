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
package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;



/**
 * ContentManagerTest
 */
public class ContentManagerTest extends DatabaseTestFixture {

    // TODO: Maybe move this to something like org.candlepin.test.CandlepinMatchers?
    private static class CollectionContentMatcher<T> implements ArgumentMatcher<Collection> {

        private final Collection<T> elements;

        public CollectionContentMatcher(T... elements) {
            this.elements = List.of(elements);
        }

        public CollectionContentMatcher(Collection<T> elements) {
            this.elements = elements;
        }

        @Override
        public boolean matches(Collection argument) {
            if (argument == null) {
                return false;
            }

            return this.elements.stream()
                .allMatch(argument::contains);
        }

        @Override
        public String toString() {
            return this.elements.toString();
        }
    }

    private ContentManager contentManager;
    private ContentAccessManager mockContentAccessManager;
    private EntitlementCertificateService mockEntCertService;
    private ProductManager productManager;

    @BeforeEach
    public void setup() throws Exception {
        this.mockContentAccessManager = mock(ContentAccessManager.class);
        this.mockEntCertService = mock(EntitlementCertificateService.class);

        this.contentManager = new ContentManager(this.mockContentAccessManager,
            this.mockEntCertService, this.productCurator, this.contentCurator, this.environmentCurator);
    }

    private ContentInfo mockContentInfo() {
        ContentInfo mock = mock(ContentInfo.class);

        // We must set this as a default, because mockito will otherwise return 0 here instead.
        doReturn(null).when(mock).getMetadataExpiration();

        return mock;
    }

    private ContentInfo mockAccessor(ContentInfo mock, String propertyName, Object value) {
        switch (propertyName) {
            case "Type" -> doReturn(value).when(mock).getType();
            case "Label" -> doReturn(value).when(mock).getLabel();
            case "Name" -> doReturn(value).when(mock).getName();
            case "Vendor" -> doReturn(value).when(mock).getVendor();
            case "ContentUrl" -> doReturn(value).when(mock).getContentUrl();
            case "RequiredTags" -> doReturn(value).when(mock).getRequiredTags();
            case "ReleaseVersion" -> doReturn(value).when(mock).getReleaseVersion();
            case "GpgUrl" -> doReturn(value).when(mock).getGpgUrl();
            case "MetadataExpiration" -> doReturn(value).when(mock).getMetadataExpiration();
            case "Arches" -> doReturn(value).when(mock).getArches();

            default -> throw new IllegalArgumentException("Unknown property name: " + propertyName);
        }

        return mock;
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Date initialLastContentUpdate = owner.getLastContentUpdate();
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey())
            .setLabel("test-label")
            .setType("test-test")
            .setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));

        Content output = this.contentManager.createContent(owner, content);

        assertEquals(output, this.contentCurator.getContentById(owner.getKey(), content.getId()));
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());
    }

    @Test
    public void testCreateContentThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey())
            .setLabel("test-label")
            .setType("test-test")
            .setVendor("test-vendor");

        Content output = this.contentManager.createContent(owner, content);

        // Verify the creation worked
        assertNotNull(output);
        assertEquals(output, this.contentCurator.getContentById(owner.getKey(), content.getId()));

        // This should fail, since it already exists
        assertThrows(IllegalStateException.class, () -> this.contentManager.createContent(owner, content));
    }

    @Test
    public void testCreateContentInOrgUsingLongKey() {
        Owner owner = this.createOwner("test-owner".repeat(25));
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey())
            .setLabel("test-label")
            .setType("test-test")
            .setVendor("test-vendor");

        assertNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));

        Content output = this.contentManager.createContent(owner, content);

        assertEquals(output, this.contentCurator.getContentById(owner.getKey(), content.getId()));
    }

    @Test
    public void testUpdateContentNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        this.createContent(content);

        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product);

        Content clone = content.clone();
        clone.setUuid(null);
        Content output = this.contentManager.updateContent(owner, content, clone, true);

        assertEquals(output.getUuid(), content.getUuid());
        assertEquals(output, content);

        verifyNoInteractions(this.mockEntCertService);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Date initialLastContentUpdate = owner.getLastContentUpdate();
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        this.createContent(content);

        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product);

        Content update = TestUtil.createContent("c1", "new content name");
        Content output = this.contentManager.updateContent(owner, content, update, regenCerts);

        assertEquals(output.getName(), update.getName());
        assertNotNull(this.contentCurator.getContentById(owner.getKey(), content.getId()));
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());

        if (regenCerts) {
            verify(this.mockEntCertService, times(1)).regenerateCertificatesOf(
                argThat(new CollectionContentMatcher<Owner>(owner)),
                argThat(new CollectionContentMatcher<Product>(product)),
                eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertService);
        }
    }

    @Test
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        Content update = TestUtil.createContent("c1", "new_name");

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.contentManager.updateContent(owner, content, update, false));

        assertThat(throwable.getMessage())
            .isNotNull()
            .isEqualTo("content is not a managed entity");
    }

    @Test
    public void testRemoveContent() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Date initialLastContentUpdate = owner.getLastContentUpdate();
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        content = this.createContent(content);

        assertNotNull(content.getUuid());
        assertNotNull(this.contentCurator.get(content.getUuid()));

        this.contentManager.removeContent(owner, content);

        assertNull(this.contentCurator.get(content.getUuid()));
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());
    }

    @Test
    public void testRemoveContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.contentManager.removeContent(owner, content));

        assertThat(throwable.getMessage())
            .isNotNull()
            .isEqualTo("content is not a managed entity");
    }

    @Test
    public void testRemoveContentWontRemoveContentHavingASingleParentProducts() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        this.contentCurator.create(content);

        Product parent = this.createProduct().addContent(content, true);
        this.productCurator.merge(parent);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.contentManager.removeContent(owner, content));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Content is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.contentCurator.get(content.getUuid()));
    }

    @Test
    public void testRemoveContentWontRemoveContentHavingMultipleParentProducts() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        this.contentCurator.create(content);

        Product parent1 = this.createProduct().addContent(content, true);
        this.productCurator.merge(parent1);
        Product parent2 = this.createProduct().addContent(content, false);
        this.productCurator.merge(parent2);
        Product parent3 = this.createProduct().addContent(content, true);
        this.productCurator.merge(parent3);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.contentManager.removeContent(owner, content));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Content is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.contentCurator.get(content.getUuid()));
    }

    protected static Stream<Arguments> equalityTestParameterProvider() {
        return Stream.of(
            Arguments.of("Type", "test_value", "alt_value"),
            Arguments.of("Label", "test_value", "alt_value"),
            Arguments.of("Name", "test_value", "alt_value"),
            Arguments.of("Vendor", "test_value", "alt_value"),
            Arguments.of("ContentUrl", "test_value", "alt_value"),
            Arguments.of("RequiredTags", "test_value", "alt_value"),
            Arguments.of("ReleaseVersion", "test_value", "alt_value"),
            Arguments.of("GpgUrl", "test_value", "alt_value"),
            Arguments.of("MetadataExpiration", 1234L, 5678L),
            Arguments.of("Arches", "test_value", "alt_value"));
    }

    private static Method[] getAccessorAndMutator(Class objClass, String propertyName, Class argClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = objClass.getDeclaredMethod("get" + propertyName);
        }
        catch (NoSuchMethodException e) {
            accessor = objClass.getDeclaredMethod("is" + propertyName);
        }

        try {
            mutator = objClass.getDeclaredMethod("set" + propertyName, argClass);
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(argClass)) {
                mutator = objClass.getDeclaredMethod("set" + propertyName, Collection.class);
            }
            else if (Boolean.class.isAssignableFrom(argClass)) {
                mutator = objClass.getDeclaredMethod("set" + propertyName, boolean.class);
            }
            else {
                throw e;
            }
        }

        return new Method[] { accessor, mutator };
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("equalityTestParameterProvider")
    public void testIsChangedByDetectsNonNullChanges(String propertyName, Object initialValue,
        Object updatedValue) throws Exception {

        Method[] methods = getAccessorAndMutator(Content.class, propertyName, initialValue.getClass());
        Method mutator = methods[1];

        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        mutator.invoke(entity, initialValue);
        this.mockAccessor(update, propertyName, updatedValue);

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("equalityTestParameterProvider")
    public void testIsChangedByIgnoresIdenticalValues(String propertyName, Object initialValue,
        Object updatedValue) throws Exception {

        Method[] methods = getAccessorAndMutator(Content.class, propertyName, initialValue.getClass());
        Method mutator = methods[1];

        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        mutator.invoke(entity, initialValue);
        this.mockAccessor(update, propertyName, initialValue);

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("equalityTestParameterProvider")
    public void testIsChangedByIgnoresNullUpstreamFields(String propertyName, Object initialValue,
        Object updatedValue) throws Exception {

        Method[] methods = getAccessorAndMutator(Content.class, propertyName, initialValue.getClass());
        Method mutator = methods[1];

        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        mutator.invoke(entity, initialValue);

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    protected static Stream<Arguments> emptyAsNullFieldProvider() {
        return Stream.of(
            Arguments.of("Arches"),
            Arguments.of("ContentUrl"),
            Arguments.of("GpgUrl"),
            Arguments.of("ReleaseVersion"),
            Arguments.of("RequiredTags"));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("emptyAsNullFieldProvider")
    public void testIsChangedByHandlesSpecialEmptyFields(String propertyName) throws Exception {
        Method[] methods = getAccessorAndMutator(Content.class, propertyName, String.class);
        Method mutator = methods[1];

        Content entity = new Content("test_content");
        mutator.invoke(entity, new Object[] { null });

        ContentInfo update = this.mockAccessor(this.mockContentInfo(), propertyName, "");

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    protected static Stream<Arguments> standardStringFieldProvider() {
        return Stream.of(
            Arguments.of("Type"),
            Arguments.of("Label"),
            Arguments.of("Name"),
            Arguments.of("Vendor"));
    }

    // This test verifies that the null-as-empty behavior does not extend to the other string
    // fields.
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("standardStringFieldProvider")
    public void testIsChangedByLimitsApplicationOfEmptyStringBehavior(String propertyName) throws Exception {
        Method[] methods = getAccessorAndMutator(Content.class, propertyName, String.class);
        Method mutator = methods[1];

        Content entity = new Content("test_content");
        mutator.invoke(entity, new Object[] { null });

        ContentInfo update = this.mockAccessor(this.mockContentInfo(), propertyName, "");

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    // The required/modified products tests need special attention due to the default behavior of
    // the content object on that field (never return null).
    @Test
    public void testIsChangedByDetectsChangesInRequiredProducts() {
        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of("1", "2", "3")).when(update).getRequiredProductIds();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @Test
    public void testIsChangedByDetectsChangesInRequiredProductsWithEmptyList() {
        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of()).when(update).getRequiredProductIds();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @Test
    public void testIsChangedByIgnoresUnchangedRequiredProductsField() {
        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of("a", "b", "c")).when(update).getRequiredProductIds();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    @Test
    public void testIsChangedByIgnoresNullRequiredProductsField() {
        Content entity = new Content("test_content");
        ContentInfo update = this.mockContentInfo();

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(null).when(update).getRequiredProductIds();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }
}

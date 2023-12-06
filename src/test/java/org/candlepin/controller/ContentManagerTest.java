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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ProductManager productManager;

    @BeforeEach
    public void setup() throws Exception {
        this.mockContentAccessManager = mock(ContentAccessManager.class);
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);

        this.contentManager = new ContentManager(this.mockContentAccessManager,
            this.mockEntCertGenerator, this.productCurator, this.contentCurator, this.environmentCurator);
    }

    @Test
    public void testCreateContent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
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

        verifyNoInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
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

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                argThat(new CollectionContentMatcher<Owner>(owner)),
                argThat(new CollectionContentMatcher<Product>(product)),
                eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testUpdateContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");
        Content update = TestUtil.createContent("c1", "new_name");

        assertThrows(IllegalStateException.class,
            () -> this.contentManager.updateContent(owner, content, update, false));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Content content = TestUtil.createContent("c1", "content-1")
            .setNamespace(owner.getKey());
        content = this.createContent(content);

        Product product = new Product("p1", "product-1");
        product.addContent(content, true);
        this.createProduct(product);

        assertNotNull(content.getUuid());
        assertNotNull(this.contentCurator.get(content.getUuid()));

        this.contentManager.removeContent(owner, content, regenCerts);

        assertNull(this.contentCurator.get(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                argThat(new CollectionContentMatcher<Owner>(owner)),
                argThat(new CollectionContentMatcher<Product>(product)),
                eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testRemoveContentThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1", "content-1");

        assertThrows(IllegalStateException.class,
            () -> this.contentManager.removeContent(owner, content, true));
    }

    protected static Stream<Arguments> equalityTestParameterProvider() {
        return Stream.of(
            Arguments.of("Id", "test_value", "alt_value"),
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

        Content entity = new Content();
        Content update = new Content(); // Note: Content is a ContentInfo impl

        mutator.invoke(entity, initialValue);
        mutator.invoke(update, updatedValue);

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("equalityTestParameterProvider")
    public void testIsChangedByIgnoresIdenticalValues(String propertyName, Object initialValue,
        Object updatedValue) throws Exception {

        Method[] methods = getAccessorAndMutator(Content.class, propertyName, initialValue.getClass());
        Method mutator = methods[1];

        Content entity = new Content();
        Content update = new Content(); // Note: Content is a ContentInfo impl

        mutator.invoke(entity, initialValue);
        mutator.invoke(update, initialValue);

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("equalityTestParameterProvider")
    public void testIsChangedByIgnoresNullUpstreamFields(String propertyName, Object initialValue,
        Object updatedValue) throws Exception {

        Method[] methods = getAccessorAndMutator(Content.class, propertyName, initialValue.getClass());
        Method mutator = methods[1];

        Content entity = new Content();
        Content update = new Content(); // Note: Content is a ContentInfo impl

        mutator.invoke(entity, initialValue);
        mutator.invoke(update, new Object[] { null });

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

        Content entity = new Content();
        Content update = new Content(); // Note: Content is a ContentInfo impl

        mutator.invoke(entity, new Object[] { null });
        mutator.invoke(update, "");

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    protected static Stream<Arguments> standardStringFieldProvider() {
        return Stream.of(
            Arguments.of("Id"),
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

        Content entity = new Content();
        Content update = new Content(); // Note: Content is a ContentInfo impl

        mutator.invoke(entity, new Object[] { null });
        mutator.invoke(update, "");

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    // The required/modified products tests need special attention due to the default behavior of
    // the content object on that field (never return null).
    @Test
    public void testIsChangedByDetectsChangesInRequiredProducts() {
        Content entity = new Content();
        ContentInfo update = mock(ContentInfo.class);

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of("1", "2", "3")).when(update).getRequiredProductIds();
        doReturn(null).when(update).getMetadataExpiration();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @Test
    public void testIsChangedByDetectsChangesInRequiredProductsWithEmptyList() {
        Content entity = new Content();
        ContentInfo update = mock(ContentInfo.class);

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of()).when(update).getRequiredProductIds();
        doReturn(null).when(update).getMetadataExpiration();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertTrue(result);
    }

    @Test
    public void testIsChangedByIgnoresUnchangedRequiredProductsField() {
        Content entity = new Content();
        ContentInfo update = mock(ContentInfo.class);

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(List.of("a", "b", "c")).when(update).getRequiredProductIds();
        doReturn(null).when(update).getMetadataExpiration();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }

    @Test
    public void testIsChangedByIgnoresNullRequiredProductsField() {
        Content entity = new Content();
        ContentInfo update = mock(ContentInfo.class);

        entity.setModifiedProductIds(List.of("a", "b", "c"));
        doReturn(null).when(update).getRequiredProductIds();
        doReturn(null).when(update).getMetadataExpiration();

        boolean result = ContentManager.isChangedBy(entity, update);
        assertFalse(result);
    }
}

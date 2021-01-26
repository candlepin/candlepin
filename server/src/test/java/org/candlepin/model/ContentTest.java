/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

/**
 * ContentTest
 */
public class ContentTest extends DatabaseTestFixture {

    @Test
    public void testContent() {
        Owner owner = new Owner("Example-Corporation");
        ownerCurator.create(owner);

        Content content = TestUtil.createContent("test-content");

        HashSet<String> modifiedProductIds = new HashSet<>();
        modifiedProductIds.add("ProductA");
        modifiedProductIds.add("ProductB");

        content.setModifiedProductIds(modifiedProductIds);
        Long metadataExpire = new Long(60 * 60 * 24);
        content.setMetadataExpiration(metadataExpire);

        contentCurator.create(content);

        Content lookedUp = contentCurator.get(content.getUuid());
        assertEquals(content.getContentUrl(), lookedUp.getContentUrl());

        MatcherAssert.assertThat(lookedUp.getModifiedProductIds(), Matchers.hasItem("ProductB"));
        assertEquals(metadataExpire, lookedUp.getMetadataExpiration());
    }

    @Test
    public void testContentWithArches() {
        Owner owner = new Owner("Example-Corporation");
        ownerCurator.create(owner);

        String arches = "x86_64, i386";
        Content content = TestUtil.createContent("test_content");
        content.setArches(arches);
        contentCurator.create(content);

        Content lookedUp = contentCurator.get(content.getUuid());
        assertEquals(lookedUp.getArches(), arches);
    }

    @Test
    public void testCreateOrUpdateWithNewLabel() {
        // TODO:
        // This test may no longer have meaning with the addition of the content manager

        Owner owner = this.createOwner("Example-Corporation");
        Content content = this.createContent("test_content", "test_content", owner);

        // Same ID, but label changed:
        String newLabel = "test-content-label-new";
        String newName = "Test Content Updated";
        Content modifiedContent = TestUtil.createContent("test_content");
        modifiedContent.setName(newName);
        modifiedContent.setLabel(newLabel);

        modifiedContent.setUuid(content.getUuid());

        contentCurator.merge(modifiedContent);

        content = this.ownerContentCurator.getContentById(owner, content.getId());
        assertEquals(newLabel, content.getLabel());
        assertEquals(newName, content.getName());
    }

    protected static Stream<Arguments> getValuesForEqualityAndReplication() {
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
            Arguments.of("ModifiedProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5", "6")),
            Arguments.of("Arches", "test_value", "alt_value"));
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = Content.class.getDeclaredMethod("get" + methodSuffix);
        }
        catch (NoSuchMethodException e) {
            accessor = Content.class.getDeclaredMethod("is" + methodSuffix);
        }

        try {
            mutator = Content.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(mutatorInputClass)) {
                mutator = Content.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
            }
            else if (Boolean.class.isAssignableFrom(mutatorInputClass)) {
                mutator = Content.class.getDeclaredMethod("set" + methodSuffix, boolean.class);
            }
            else {
                throw e;
            }
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        Content lhs = new Content();
        Content rhs = new Content();

        assertFalse(lhs.equals(null));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("getValuesForEqualityAndReplication")
    public void testEquality(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        Content lhs = new Content();
        Content rhs = new Content();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertFalse(lhs.equals(rhs));
        assertFalse(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
    }

    @Test
    public void testBaseEntityVersion() {
        Content lhs = new Content();
        Content rhs = new Content();

        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("getValuesForEqualityAndReplication")
    public void testEntityVersion(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        Content lhs = new Content();
        Content rhs = new Content();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertNotEquals(lhs.getEntityVersion(), rhs.getEntityVersion());
    }

    protected static Stream<Arguments> getValueNamesForNullConversion() {
        return Stream.of(
            Arguments.of("ContentUrl"),
            Arguments.of("RequiredTags"),
            Arguments.of("ReleaseVersion"),
            Arguments.of("GpgUrl"),
            Arguments.of("Arches"));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("getValueNamesForNullConversion")
    public void testEmptyToNullConversion(String valueName) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, String.class);
        Method accessor = methods[0];
        Method mutator = methods[1];

        Content content = new Content();

        assertNull(accessor.invoke(content));
        mutator.invoke(content, "");
        assertNull(accessor.invoke(content));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("getValueNamesForNullConversion")
    public void testEmptyToNullConversionMaintainsEquality(String valueName) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, String.class);
        Method accessor = methods[0];
        Method mutator = methods[1];

        Content lhs = new Content();
        Content rhs = new Content();

        assertEquals(lhs, rhs);
        assertEquals(lhs.hashCode(), rhs.hashCode());
        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());

        // Put an empty value in the rhs and verify that the conversion to null maintains
        // equality with the unmodified lhs (which should default to nulls)
        mutator.invoke(rhs, "");

        assertEquals(lhs, rhs);
        assertEquals(lhs.hashCode(), rhs.hashCode());
        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());
    }
}

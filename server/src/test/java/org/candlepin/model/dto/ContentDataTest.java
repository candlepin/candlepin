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
package org.candlepin.model.dto;

import org.candlepin.model.Content;
import org.candlepin.util.Util;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;



/**
 * Test suite for the ContentData class
 */
@RunWith(JUnitParamsRunner.class)
public class ContentDataTest {

    // public .* (?:get|is)(.*)\(\) {\npublic .* set\1\(.*\) {

    // @Test
    // public void testGetSet\1() {
    //     ContentData dto = new ContentData();
    //     String input = "test_value";

    //     String output = dto.get\1();
    //     assertNull(output);

    //     ContentData output2 = dto.set\1(input);
    //     assertSame(output2, dto);

    //     output = dto.get\1();
    //     assertEquals(input, output);
    // }

    protected Object[][] getBadStringValues() {
        return new Object[][] {
            new Object[] { null },
            new Object[] { "" },
        };
    }

    @Test
    public void testGetSetUuid() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getUuid();
        assertNull(output);

        ContentData output2 = dto.setUuid(input);
        assertSame(output2, dto);

        output = dto.getUuid();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetId() {
        ContentData dto = new ContentData();
        String input = "test_id";

        String output = dto.getId();
        assertNull(output);

        ContentData output2 = dto.setId(input);
        assertSame(output2, dto);

        output = dto.getId();
        assertEquals(input, output);
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "getBadStringValues")
    public void testGetSetIdBadValues(String input) {
        ContentData dto = new ContentData();

        String output = dto.getId();
        assertNull(output);

        dto.setId(input);
    }

    @Test
    public void testGetSetType() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getType();
        assertNull(output);

        ContentData output2 = dto.setType(input);
        assertSame(output2, dto);

        output = dto.getType();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetLabel() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getLabel();
        assertNull(output);

        ContentData output2 = dto.setLabel(input);
        assertSame(output2, dto);

        output = dto.getLabel();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetName() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getName();
        assertNull(output);

        ContentData output2 = dto.setName(input);
        assertSame(output2, dto);

        output = dto.getName();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetVendor() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getVendor();
        assertNull(output);

        ContentData output2 = dto.setVendor(input);
        assertSame(output2, dto);

        output = dto.getVendor();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetContentUrl() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getContentUrl();
        assertNull(output);

        ContentData output2 = dto.setContentUrl(input);
        assertSame(output2, dto);

        output = dto.getContentUrl();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetRequiredTags() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getRequiredTags();
        assertNull(output);

        ContentData output2 = dto.setRequiredTags(input);
        assertSame(output2, dto);

        output = dto.getRequiredTags();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetReleaseVersion() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getReleaseVersion();
        assertNull(output);

        ContentData output2 = dto.setReleaseVersion(input);
        assertSame(output2, dto);

        output = dto.getReleaseVersion();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetGpgUrl() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getGpgUrl();
        assertNull(output);

        ContentData output2 = dto.setGpgUrl(input);
        assertSame(output2, dto);

        output = dto.getGpgUrl();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetMetadataExpiration() {
        ContentData dto = new ContentData();
        Long input = 12345L;

        Long output = dto.getMetadataExpiration();
        assertNull(output);

        ContentData output2 = dto.setMetadataExpiration(input);
        assertSame(output2, dto);

        output = dto.getMetadataExpiration();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetModifiedProductIds() {
        ContentData dto = new ContentData();
        Collection<String> input = Arrays.asList("1", "2", "3");

        Collection<String> output = dto.getModifiedProductIds();
        assertNull(output);

        ContentData output2 = dto.setModifiedProductIds(input);
        assertSame(output2, dto);

        output = dto.getModifiedProductIds();

        assertTrue(Util.collectionsAreEqual(input, output));
    }

    @Test
    public void testAddModifiedProductId() {
        ContentData dto = new ContentData();

        Collection<String> pids = dto.getModifiedProductIds();
        assertNull(pids);

        boolean output = dto.addModifiedProductId("1");
        pids = dto.getModifiedProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1"), pids));

        output = dto.addModifiedProductId("2");
        pids = dto.getModifiedProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));

        output = dto.addModifiedProductId("1");
        pids = dto.getModifiedProductIds();

        assertFalse(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));
    }

    @Test
    public void testRemoveModifiedProductId() {
        ContentData dto = new ContentData();

        Collection<String> pids = dto.getModifiedProductIds();
        assertNull(pids);

        boolean output = dto.removeModifiedProductId("1");
        pids = dto.getModifiedProductIds();

        assertFalse(output);
        assertNull(pids);

        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        pids = dto.getModifiedProductIds();

        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));

        output = dto.removeModifiedProductId("1");
        pids = dto.getModifiedProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("2"), pids));

        output = dto.removeModifiedProductId("3");
        pids = dto.getModifiedProductIds();

        assertFalse(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("2"), pids));

        output = dto.removeModifiedProductId("2");
        pids = dto.getModifiedProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.<String>asList(), pids));
    }

    @Test
    public void testGetSetArches() {
        ContentData dto = new ContentData();
        String input = "test_value";

        String output = dto.getArches();
        assertNull(output);

        ContentData output2 = dto.setArches(input);
        assertSame(output2, dto);

        output = dto.getArches();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetLocked() {
        ContentData dto = new ContentData();
        Boolean input = Boolean.TRUE;

        Boolean output = dto.isLocked();
        assertNull(output);

        ContentData output2 = dto.setLocked(input);
        assertSame(output2, dto);

        output = dto.isLocked();
        assertEquals(input, output);
    }

    protected Object[][] getValuesForEqualityAndReplication() {
        return new Object[][] {
            new Object[] { "Uuid", "test_value", "alt_value" },
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Type", "test_value", "alt_value" },
            new Object[] { "Label", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Vendor", "test_value", "alt_value" },
            new Object[] { "ContentUrl", "test_value", "alt_value" },
            new Object[] { "RequiredTags", "test_value", "alt_value" },
            new Object[] { "ReleaseVersion", "test_value", "alt_value" },
            new Object[] { "GpgUrl", "test_value", "alt_value" },
            new Object[] { "MetadataExpiration", 1234L, 5678L },
            new Object[] { "ModifiedProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5", "6") },
            new Object[] { "Arches", "test_value", "alt_value" },
            new Object[] { "Locked", Boolean.TRUE, false }
        };
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ContentData.class.getDeclaredMethod("get" + methodSuffix, null);
        }
        catch (NoSuchMethodException e) {
            accessor = ContentData.class.getDeclaredMethod("is" + methodSuffix, null);
        }

        try {
            mutator = ContentData.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (!Collection.class.isAssignableFrom(mutatorInputClass)) {
                throw e;
            }

            mutator = ContentData.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        ContentData lhs = new ContentData();
        ContentData rhs = new ContentData();

        assertFalse(lhs.equals(null));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testEquality(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ContentData lhs = new ContentData();
        ContentData rhs = new ContentData();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertEquals(lhs.hashCode(), rhs.hashCode());

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertFalse(lhs.equals(rhs));
        assertFalse(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testClone(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ContentData base = new ContentData();

        mutator.invoke(base, value1);

        ContentData clone = (ContentData) base.clone();

        assertEquals(accessor.invoke(base, null), accessor.invoke(clone, null));
        assertEquals(base, clone);
        assertEquals(base.hashCode(), clone.hashCode());
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testPopulateWithDTO(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ContentData base = new ContentData();
        ContentData source = new ContentData();

        mutator.invoke(source, value1);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : ContentData.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+") &&
                !method.getName().equals("getRequiredProductIds")) {

                // The getRequiredProductIds method is a special case that would require large architectural
                // changes to these tests to handle properly. Basically, it's just another

                Object output = method.invoke(base, null);

                if (method.getName().equals(accessor.getName())) {
                    if (value1 instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) value1, (Collection) output));
                    }
                    else {
                        assertEquals(value1, output);
                    }
                }
                else {
                    assertNull(output);
                }
            }
        }
    }

    protected Object[][] getValuesPopulationByEntity() {
        return new Object[][] {
            new Object[] { "Uuid", "test_value", null },
            new Object[] { "Id", "test_value", null },
            new Object[] { "Type", "test_value", null },
            new Object[] { "Label", "test_value", null },
            new Object[] { "Name", "test_value", null },
            new Object[] { "Vendor", "test_value", null },
            new Object[] { "ContentUrl", "test_value", null },
            new Object[] { "RequiredTags", "test_value", null },
            new Object[] { "ReleaseVersion", "test_value", null },
            new Object[] { "GpgUrl", "test_value", null },
            new Object[] { "MetadataExpiration", 1234L, null },
            new Object[] { "ModifiedProductIds", Arrays.asList("1", "2", "3"), Arrays.asList() },
            new Object[] { "Arches", "test_value", null },
            new Object[] { "Locked", Boolean.TRUE, false }
        };
    }

    @Test
    @Parameters(method = "getValuesPopulationByEntity")
    public void testPopulateWithEntity(String valueName, Object input, Object defaultValue) throws Exception {
        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ContentData.class.getDeclaredMethod("get" + valueName, null);
        }
        catch (NoSuchMethodException e) {
            accessor = ContentData.class.getDeclaredMethod("is" + valueName, null);
        }

        try {
            mutator = Content.class.getDeclaredMethod("set" + valueName, input.getClass());
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(input.getClass())) {
                mutator = Content.class.getDeclaredMethod("set" + valueName, Collection.class);
            }
            else if (Boolean.class.isAssignableFrom(input.getClass())) {
                mutator = Content.class.getDeclaredMethod("set" + valueName, boolean.class);
            }
            else {
                throw e;
            }
        }

        ContentData base = new ContentData();
        Content source = new Content();

        mutator.invoke(source, input);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : ContentData.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+")) {
                Object output = method.invoke(base, null);

                if (method.getName().equals(accessor.getName())) {
                    if (input instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) input, (Collection) output));
                    }
                    else {
                        assertEquals(input, output);
                    }
                }
                else {
                    for (Object[] values : this.getValuesPopulationByEntity()) {
                        if (method.getName().endsWith((String) values[0])) {
                            if (values[2] instanceof Collection) {
                                assertTrue(output instanceof Collection);
                                assertTrue(Util.collectionsAreEqual((Collection) values[2],
                                    (Collection) output));
                            }
                            else {
                                assertEquals(values[2], output);
                            }
                        }
                    }
                }
            }
        }
    }

}

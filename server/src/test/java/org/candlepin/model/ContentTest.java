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


import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.*;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;



/**
 * ContentTest
 */
@RunWith(JUnitParamsRunner.class)
public class ContentTest extends DatabaseTestFixture {

    @Test
    public void testContent() {
        Owner owner = new Owner("Example-Corporation");
        ownerCurator.create(owner);

        Content content = TestUtil.createContent("test-content");

        HashSet<String> modifiedProductIds = new HashSet<String>();
        modifiedProductIds.add("ProductA");
        modifiedProductIds.add("ProductB");

        content.setModifiedProductIds(modifiedProductIds);
        Long metadataExpire = new Long(60 * 60 * 24);
        content.setMetadataExpire(metadataExpire);

        contentCurator.create(content);

        Content lookedUp = contentCurator.find(content.getUuid());
        assertEquals(content.getContentUrl(), lookedUp.getContentUrl());
        assertThat(lookedUp.getModifiedProductIds(), hasItem("ProductB"));
        assertEquals(metadataExpire, lookedUp.getMetadataExpire());
    }

    @Test
    public void testContentWithArches() {
        Owner owner = new Owner("Example-Corporation");
        ownerCurator.create(owner);

        String arches = "x86_64, i386";
        Content content = TestUtil.createContent("test_content");
        content.setArches(arches);
        contentCurator.create(content);

        Content lookedUp = contentCurator.find(content.getUuid());
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

    @Test
    public void testLockStateAffectsEquality() {
        Owner owner = new Owner("Example-Corporation");
        Content c1 = TestUtil.createContent("test_content-1");
        Content c2 = TestUtil.createContent("test_content-1");

        assertEquals(c1, c2);

        c2.setLocked(true);
        assertNotEquals(c1, c2);

        c1.setLocked(true);
        assertEquals(c1, c2);
    }

    protected Object[][] getValuesForEqualityAndReplication() {
        return new Object[][] {
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Type", "test_value", "alt_value" },
            new Object[] { "Label", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Vendor", "test_value", "alt_value" },
            new Object[] { "ContentUrl", "test_value", "alt_value" },
            new Object[] { "RequiredTags", "test_value", "alt_value" },
            new Object[] { "ReleaseVersion", "test_value", "alt_value" },
            new Object[] { "GpgUrl", "test_value", "alt_value" },
            new Object[] { "MetadataExpire", 1234L, 5678L },
            new Object[] { "ModifiedProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5", "6") },
            new Object[] { "Arches", "test_value", "alt_value" },
            new Object[] { "Locked", Boolean.TRUE, Boolean.FALSE }
        };
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = Content.class.getDeclaredMethod("get" + methodSuffix, null);
        }
        catch (NoSuchMethodException e) {
            accessor = Content.class.getDeclaredMethod("is" + methodSuffix, null);
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

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
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

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
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
}

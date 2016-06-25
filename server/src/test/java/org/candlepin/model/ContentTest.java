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

import org.junit.Test;

import java.util.HashSet;



/**
 * ContentTest
 */
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

    @Test
    public void testLockStateAffectsHashCode() {
        Owner owner = new Owner("Example-Corporation");
        Content c1 = TestUtil.createContent("test_content-1");
        Content c2 = TestUtil.createContent("test_content-1");

        assertEquals(c1.hashCode(), c2.hashCode());

        c2.setLocked(true);
        assertNotEquals(c1.hashCode(), c2.hashCode());

        c1.setLocked(true);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}

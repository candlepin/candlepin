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


import static org.hamcrest.collection.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.HashSet;

import javax.inject.Inject;


/**
 * ContentTest
 */
public class ContentTest extends DatabaseTestFixture {
    @Inject private ContentCurator contentCurator;

    @Test
    public void testContent() {
        Owner owner = new Owner("Example-Corporation");
        String  contentHash = String.valueOf(
            Math.abs(Long.valueOf("test-content".hashCode())));
        Content content = new Content(owner, "test-content", contentHash,
                            "test-content-label", "yum", "test-vendor",
                             "test-content-url", "test-gpg-url",
                             "test-arch1,test-arch2");
        HashSet<String> modifiedProductIds = new HashSet<String>();
        modifiedProductIds.add("ProductA");
        modifiedProductIds.add("ProductB");

        content.setModifiedProductIds(modifiedProductIds);
        Long metadataExpire = new Long(60 * 60 * 24);
        content.setMetadataExpire(metadataExpire);

        contentCurator.create(content);

        Content lookedUp = contentCurator.find(content.getId());
        assertEquals(content.getContentUrl(), lookedUp.getContentUrl());
        assertThat(lookedUp.getModifiedProductIds(), hasItem("ProductB"));
        assertEquals(metadataExpire, lookedUp.getMetadataExpire());
    }

    @Test
    public void testContentWithArches() {
        Owner owner = new Owner("Example-Corporation");
        String  contentHash = String.valueOf(
            Math.abs(Long.valueOf("test-content-arches".hashCode())));

        Content content = new Content(owner, "test-content-arches", contentHash,
                            "test-content-arches-label", "yum", "test-vendor",
                             "test-content-url", "test-gpg-url", "");
        String arches = "x86_64, i386";
        content.setArches(arches);
        contentCurator.create(content);

        Content lookedUp = contentCurator.find(content.getId());
        assertEquals(lookedUp.getArches(), arches);
    }

    @Test
    public void testCreateOrUpdateWithNewLabel() {
        Owner owner = new Owner("Example-Corporation");

        Content content = new Content(owner, "Test Content", "100",
            "test-content-label", "yum", "test-vendor",
             "test-content-url", "test-gpg-url", "test-arch1");
        contentCurator.create(content);

        // Same ID, but label changed:
        String newLabel = "test-content-label-new";
        String newName = "Test Content Updated";
        Content modifiedContent = new Content(owner, newName, "100",
            newLabel, "yum", "test-vendor", "test-content-url",
            "test-gpg-url", "test-arch1");
        contentCurator.createOrUpdate(modifiedContent);

        content = contentCurator.find("100");
        assertEquals(newLabel, content.getLabel());
        assertEquals(newName, content.getName());
    }
}

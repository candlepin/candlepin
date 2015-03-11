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

import static org.junit.Assert.assertEquals;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;

import javax.inject.Inject;

/**
 * ContentCuratorTest
 */
public class ContentCuratorTest extends DatabaseTestFixture {
    @Inject private ContentCurator contentCurator;

    private Content updates;

    /* FIXME: Add Arches here */

    @Before
    public void setUp() {
        updates = new Content(
            "Test Content 1", "100",
            "test-content-label-1", "yum-1", "test-vendor-1",
            "test-content-url-1", "test-gpg-url-1", "test-arch1,test-arch2");
        updates.setRequiredTags("required-tags");
        updates.setReleaseVer("releaseVer");
        updates.setMetadataExpire(new Long(1));
        updates.setModifiedProductIds(new HashSet<String>() { { add("productIdOne"); } });
    }

    @Test
    public void shouldUpdateContentWithNewValues() {
        Content toBeUpdated = new Content(
            "Test Content", updates.getId(),
            "test-content-label", "yum", "test-vendor",
            "test-content-url", "test-gpg-url", "test-arch1");
        contentCurator.create(toBeUpdated);

        toBeUpdated = contentCurator.createOrUpdate(updates);

        assertEquals(toBeUpdated.getName(), updates.getName());
        assertEquals(toBeUpdated.getLabel(), updates.getLabel());
        assertEquals(toBeUpdated.getType(), updates.getType());
        assertEquals(toBeUpdated.getVendor(), updates.getVendor());
        assertEquals(toBeUpdated.getContentUrl(), updates.getContentUrl());
        assertEquals(toBeUpdated.getRequiredTags(), updates.getRequiredTags());
        assertEquals(toBeUpdated.getReleaseVer(), updates.getReleaseVer());
        assertEquals(toBeUpdated.getMetadataExpire(), updates.getMetadataExpire());
        assertEquals(toBeUpdated.getModifiedProductIds(), updates.getModifiedProductIds());
        assertEquals(toBeUpdated.getArches(), updates.getArches());
    }

    @Test
    public void forceMetadataExpire() {
        Content c1 = new Content(
                "c1", "1",
                "test-c1", "yum", "Vendor",
                "test-content-url-1", "test-gpg-url-1", "test-arch1,test-arch2");
        c1.setMetadataExpire(new Long(86000));
        contentCurator.create(c1);

        Content c2 = new Content(
                "c2", "2",
                "test-c2", "yum", "Vendor",
                "test-content-url-1", "test-gpg-url-1", "test-arch1,test-arch2");
        c2.setMetadataExpire(new Long(86000));
        contentCurator.create(c2);

        int updated = contentCurator.forceMetadataExpiry(new Long(0));
        assertEquals(2, updated);

        // Need to get these out of the session otherwise hibernate sees the old:
        contentCurator.evict(c1);
        contentCurator.evict(c2);

        for (Content c : contentCurator.listAll()) {
            assertEquals(new Long(0), c.getMetadataExpire());
        }

        // Running again should not re-update the same rows:
        updated = contentCurator.forceMetadataExpiry(new Long(0));
        assertEquals(0, updated);
    }
}

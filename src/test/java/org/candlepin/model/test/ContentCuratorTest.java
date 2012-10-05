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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashSet;

import org.candlepin.model.Content;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ContentCuratorTest
 */
public class ContentCuratorTest extends DatabaseTestFixture {

    private Content updates;

    @Before
    public void setUp() {
        updates = new Content(
            "Test Content 1", "100",
            "test-content-label-1", "yum-1", "test-vendor-1",
            "test-content-url-1", "test-gpg-url-1");
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
            "test-content-url", "test-gpg-url");
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
    }
}

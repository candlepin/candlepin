/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import java.util.HashSet;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.collection.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;


/**
 * ContentTest
 */
public class ContentTest extends DatabaseTestFixture {

    @Test
    public void testContent() {
        String  contentHash = String.valueOf(
            Math.abs(Long.valueOf("test-content".hashCode())));
        Content content = new Content("test-content", contentHash, 
                            "test-content-label", "yum", "test-vendor",
                             "test-content-url", "test-gpg-url");
        HashSet<String> modifiedProductIds = new HashSet<String>();
        modifiedProductIds.add("ProductA");
        modifiedProductIds.add("ProductB");

        content.setModifiedProductIds(modifiedProductIds);
        
        contentCurator.create(content);
        
        Content lookedUp = contentCurator.find(content.getId());
        assertEquals(content.getContentUrl(), lookedUp.getContentUrl());
        assertThat(lookedUp.getModifiedProductIds(), hasItem("ProductB"));
    }
}

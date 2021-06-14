/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.nodes;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the ContentNode class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentNodeTest extends AbstractNodeTest<Content, ContentInfo> {

    @Override
    protected EntityNode<Content, ContentInfo> buildEntityNode(Owner owner, String entityId) {
        return new ContentNode(owner, entityId);
    }

    @Override
    protected Content buildLocalEntity(Owner owner, String entityId) {
        return new Content()
            .setId(entityId);
    }

    @Override
    protected ContentInfo buildImportedEntity(Owner owner, String entityId) {
        return new Content()
            .setId(entityId);
    }

    @Test
    public void testGetEntityClass() {
        Owner owner = TestUtil.createOwner();
        ContentNode node = new ContentNode(owner, "test_id");

        assertEquals(Content.class, node.getEntityClass());
    }

}

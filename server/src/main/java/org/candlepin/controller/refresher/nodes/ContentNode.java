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

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;



/**
 * ContentNode is an EntityNode implementation for mapping Content to ContentInfo instances
 */
public class ContentNode extends AbstractNode<Content, ContentInfo> {

    /**
     * Creates a new ContentNode owned by the given organization, using the specified content ID.
     *
     * @param owner
     *  the organization to own this node
     *
     * @param id
     *  the content ID to use for this node
     *
     * @throws IllegalArgumentException
     *  if owner is null, or id is null or invalid
     */
    public ContentNode(Owner owner, String id) {
        super(owner, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Content> getEntityClass() {
        return Content.class;
    }

}

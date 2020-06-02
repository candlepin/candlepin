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
package org.candlepin.controller.refresher.mappers;

import org.candlepin.model.Content;
import org.candlepin.service.model.ContentInfo;



/**
 * The ContentMapper class is a simple implementation of the EntityMapper specific to Content.
 */
public class ContentMapper extends AbstractEntityMapper<Content, ContentInfo> {

    /**
     * Creates a new ContentMapper instance
     */
    public ContentMapper() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Content> getExistingEntityClass() {
        return Content.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ContentInfo> getImportedEntityClass() {
        return ContentInfo.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addExistingEntity(Content entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.addExistingEntity(entity.getId(), entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addImportedEntity(ContentInfo entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.addImportedEntity(entity.getId(), entity);
    }

}

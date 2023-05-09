/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
    protected String getEntityId(ContentInfo entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("entity lacks a mappable ID: " + entity);
        }

        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEntityId(Content entity) {
        return this.getEntityId((ContentInfo) entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean entitiesMatch(Content current, Content inbound) {
        if (current == null) {
            throw new IllegalArgumentException("current is null");
        }

        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        // Impl note:
        // This test is contingent on some internal knowledge of the model and how Content.equals
        // works. Since content entities are expected to be immutable, a matching UUID *should* also
        // guarantee entity equality. Content.equals also leverages this knowledge to avoid a bunch
        // of additional field checks if the UUID is present and equal on both entities, which is
        // why we don't bother calling into it unless one of them lacks a UUID (which itself isn't
        // something that should happen).
        // Even in the weird case where the UUIDs equal but the product instances themselves are
        // not, we'll still be defaulting to taking the last one seen (which should be the locally
        // mapped instance from the DB), so we should maintain consistency even in the worst case.

        String currentUuid = current.getUuid();
        String inboundUuid = inbound.getUuid();

        return currentUuid == null || inboundUuid == null ?
            current.equals(inbound) :
            currentUuid.equals(inboundUuid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean entitiesMatch(ContentInfo current, ContentInfo inbound) {
        if (current == null) {
            throw new IllegalArgumentException("current is null");
        }

        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        return current.equals(inbound);
    }

}

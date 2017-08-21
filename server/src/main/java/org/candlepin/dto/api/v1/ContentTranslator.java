/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;



/**
 * The ContentTranslator provides translation from Content model objects to
 * ContentDTOs
 */
public class ContentTranslator extends TimestampedEntityTranslator<Content, ContentDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO translate(Content source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO translate(ModelTranslator translator, Content source) {
        return source != null ? this.populate(translator, source, new ContentDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO populate(Content source, ContentDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO populate(ModelTranslator translator, Content source, ContentDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setUuid(source.getUuid());
        destination.setId(source.getId());
        destination.setType(source.getType());
        destination.setLabel(source.getLabel());
        destination.setName(source.getName());
        destination.setVendor(source.getVendor());
        destination.setContentUrl(source.getContentUrl());
        destination.setRequiredTags(source.getRequiredTags());
        destination.setReleaseVersion(source.getReleaseVersion());
        destination.setGpgUrl(source.getGpgUrl());
        destination.setMetadataExpiration(source.getMetadataExpire());
        destination.setModifiedProductIds(source.getModifiedProductIds());
        destination.setArches(source.getArches());
        destination.setLocked(source.isLocked());

        return destination;
    }

}

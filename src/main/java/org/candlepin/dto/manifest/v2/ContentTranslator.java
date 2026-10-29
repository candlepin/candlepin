/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Content;

/**
 * Translation of Content objects to version 2 manifest ContentDTOs
 */
public class ContentTranslator extends TimestampedEntityTranslator<Content, ContentDTO> {

    @Override
    public ContentDTO translate(Content source) {
        return this.translate(null, source);
    }

    @Override
    public ContentDTO translate(ModelTranslator translator, Content source) {
        return source != null ? this.populate(translator, source, new ContentDTO()) : null;
    }

    @Override
    public ContentDTO populate(Content source, ContentDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public ContentDTO populate(ModelTranslator translator, Content source, ContentDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setId(source.getId());
        destination.setType(source.getType());
        destination.setLabel(source.getLabel());
        destination.setName(source.getName());
        destination.setVendor(source.getVendor());
        destination.setContentUrl(source.getContentUrl());
        destination.setRequiredTags(source.getRequiredTags());
        destination.setReleaseVer(source.getReleaseVersion());
        destination.setGpgUrl(source.getGpgUrl());
        destination.setArches(source.getArches());

        return destination;
    }
}

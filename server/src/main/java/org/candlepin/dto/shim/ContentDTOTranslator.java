/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.model.dto.ContentData;



/**
 * The ContentDTOTranslator provides translation from the new ContentDTOs (manifest import/export)
 * to the traditional ContentData DTO objects
 *
 */
public class ContentDTOTranslator implements ObjectTranslator<ContentDTO, ContentData> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData translate(ContentDTO source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData translate(ModelTranslator translator, ContentDTO source) {
        return source != null ? this.populate(translator, source, new ContentData()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData populate(ContentDTO source, ContentData destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note: the 'locked' field is always set to false since it is not currently being exported.</p>
     */
    @Override
    public ContentData populate(ModelTranslator translator, ContentDTO source, ContentData dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.setCreated(source.getCreated());
        dest.setUpdated(source.getUpdated());

        dest.setUuid(source.getUuid());
        dest.setId(source.getId());
        dest.setType(source.getType());
        dest.setLabel(source.getLabel());
        dest.setName(source.getName());
        dest.setVendor(source.getVendor());
        dest.setContentUrl(source.getContentUrl());
        dest.setRequiredTags(source.getRequiredTags());
        dest.setReleaseVersion(source.getReleaseVersion());
        dest.setGpgUrl(source.getGpgUrl());
        dest.setMetadataExpiration(source.getMetadataExpiration());
        dest.setArches(source.getArches());
        dest.setModifiedProductIds(source.getModifiedProductIds());

        // We manually set this to false since it is not included in the exported data.
        dest.setLocked(false);

        return dest;
    }
}

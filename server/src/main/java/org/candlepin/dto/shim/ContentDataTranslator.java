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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.model.dto.ContentData;
import org.candlepin.util.Util;

import java.util.HashSet;


/**
 * The ContentDataTranslator provides translation from ContentData DTO objects to the new
 * ContentDTOs (API)
 */
public class ContentDataTranslator implements ObjectTranslator<ContentData, ContentDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO translate(ContentData source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO translate(ModelTranslator translator, ContentData source) {
        return source != null ? this.populate(translator, source, new ContentDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO populate(ContentData source, ContentDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentDTO populate(ModelTranslator translator, ContentData source, ContentDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.uuid(source.getUuid())
            .id(source.getId())
            .type(source.getType())
            .label(source.getLabel())
            .name(source.getName())
            .vendor(source.getVendor())
            .contentUrl(source.getContentUrl())
            .requiredTags(source.getRequiredTags())
            .releaseVer(source.getReleaseVersion())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .gpgUrl(source.getGpgUrl())
            .metadataExpire(source.getMetadataExpiration())
            .modifiedProductIds(new HashSet<>(source.getModifiedProductIds()))
            .arches(source.getArches());

        return dest;
    }
}

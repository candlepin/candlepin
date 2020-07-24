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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.ContentOverride;
import org.candlepin.util.Util;

/**
 * The ContentOverrideTranslator provides the base translation bits for ContentOverride model
 * objects and their derivatives.
 */
public class ContentOverrideTranslator implements ObjectTranslator<ContentOverride, ContentOverrideDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentOverrideDTO translate(ContentOverride source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentOverrideDTO translate(ModelTranslator translator, ContentOverride source) {
        return source != null ? this.populate(translator, source, new ContentOverrideDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentOverrideDTO populate(ContentOverride source, ContentOverrideDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentOverrideDTO populate(ModelTranslator translator, ContentOverride source,
        ContentOverrideDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.contentLabel(source.getContentLabel())
            .name(source.getName())
            .value(source.getValue())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()));

        return dest;
    }
}

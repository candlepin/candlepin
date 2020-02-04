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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.GuestId;

import java.time.ZoneOffset;

/**
 * The GuestIdTranslator provides translation from GuestId model objects to
 * GuestIdDTOs
 */
public class GuestIdTranslator implements ObjectTranslator<GuestId, GuestIdDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO translate(GuestId source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO translate(ModelTranslator translator, GuestId source) {
        return source != null ? this.populate(translator, source, new GuestIdDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO populate(GuestId source, GuestIdDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO populate(ModelTranslator translator, GuestId source, GuestIdDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.created(source.getCreated() != null ?
                source.getCreated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .updated(source.getUpdated() != null ?
                source.getUpdated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .id(source.getId())
            .guestId(source.getGuestId())
            .attributes(source.getAttributes());

        return dest;
    }

}

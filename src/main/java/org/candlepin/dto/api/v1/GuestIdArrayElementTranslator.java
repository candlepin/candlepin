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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.GuestId;
import org.candlepin.util.Util;

/**
 * The GuestIdTranslator provides translation from GuestId model objects to
 * GuestIdDTOArrayElement (a special version of GuestIdDTOs that do not include the attributes field for
 * performance optimization reasons).
 */
public class GuestIdArrayElementTranslator implements ObjectTranslator<GuestId, GuestIdDTOArrayElement> {

    @Override
    public GuestIdDTOArrayElement translate(GuestId source) {
        return this.translate(null, source);
    }

    @Override
    public GuestIdDTOArrayElement translate(ModelTranslator translator, GuestId source) {
        return source != null ? this.populate(translator, source, new GuestIdDTOArrayElement()) : null;
    }

    @Override
    public GuestIdDTOArrayElement populate(GuestId source, GuestIdDTOArrayElement dest) {
        return this.populate(null, source, dest);
    }

    @Override
    public GuestIdDTOArrayElement populate(ModelTranslator modelTranslator, GuestId source,
        GuestIdDTOArrayElement dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .guestId(source.getGuestId());

        return dest;
    }
}

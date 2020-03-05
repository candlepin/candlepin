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
import org.candlepin.model.Owner;

/**
 * Provides translation from NestedOwnerDTO to Owner model objects
 */
public class NestedOwnerDTOTranslator implements ObjectTranslator<NestedOwnerDTO, Owner> {

    @Override
    public Owner translate(NestedOwnerDTO source) {
        return this.translate(null, source);
    }

    @Override
    public Owner translate(ModelTranslator translator, NestedOwnerDTO source) {
        return source != null ? this.populate(translator, source, new Owner()) : null;
    }

    @Override
    public Owner populate(NestedOwnerDTO source, Owner destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public Owner populate(ModelTranslator translator, NestedOwnerDTO source, Owner dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setId(source.getId());
        dest.setKey(source.getKey());
        dest.setDisplayName(source.getDisplayName());
        dest.setHref(source.getHref());

        return dest;
    }
}

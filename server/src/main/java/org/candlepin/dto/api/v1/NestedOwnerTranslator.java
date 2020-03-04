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
 * Provides translation from Owner model objects to NestedOwnerDTOs
 */
public class NestedOwnerTranslator implements ObjectTranslator<Owner, NestedOwnerDTO> {

    @Override
    public NestedOwnerDTO translate(Owner source) {
        return this.translate(null, source);
    }

    @Override
    public NestedOwnerDTO translate(ModelTranslator translator, Owner source) {
        return source != null ? this.populate(translator, source, new NestedOwnerDTO()) : null;
    }

    @Override
    public NestedOwnerDTO populate(Owner source, NestedOwnerDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public NestedOwnerDTO populate(ModelTranslator translator, Owner source, NestedOwnerDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .key(source.getKey())
            .displayName(source.getDisplayName())
            .href(source.getHref());

        return dest;
    }
}

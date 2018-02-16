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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Owner;



/**
 * The OwnerTranslator provides translation from Owner model objects to OwnerDTOs,
 * as used by the Rules framework.
 */
public class OwnerTranslator implements ObjectTranslator<Owner, OwnerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(Owner source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(ModelTranslator translator, Owner source) {
        return source != null ? this.populate(translator, source, new OwnerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(Owner source, OwnerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(ModelTranslator translator, Owner source, OwnerDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setId(source.getId());
        dest.setDefaultServiceLevel(source.getDefaultServiceLevel());

        return dest;
    }
}

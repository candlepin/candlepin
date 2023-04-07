/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;



/**
 * The EntitlementTranslator provides translation from Entitlement model objects to EntitlementDTOs.
 */
public class EntitlementTranslator implements ObjectTranslator<Entitlement, EntitlementDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO translate(Entitlement source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO translate(ModelTranslator translator, Entitlement source) {
        return source != null ? this.populate(translator, source, new EntitlementDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(Entitlement source, EntitlementDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntitlementDTO populate(ModelTranslator modelTranslator, Entitlement source, EntitlementDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setId(source.getId());
        dest.setQuantity(source.getQuantity());
        dest.setStartDate(source.getStartDate());
        dest.setEndDate(source.getEndDate());

        if (modelTranslator != null) {
            Pool pool = source.getPool();
            dest.setPool(pool != null ? modelTranslator.translate(pool, PoolDTO.class) : null);
        }

        return dest;
    }
}

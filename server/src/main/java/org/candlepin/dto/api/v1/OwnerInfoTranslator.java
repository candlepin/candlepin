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
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.util.Util;

/**
 * The OwnerInfoTranslator provides translation from OwnerInfo service model objects to OwnerDTOs
 */
public class OwnerInfoTranslator implements ObjectTranslator<OwnerInfo, OwnerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(OwnerInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(ModelTranslator translator, OwnerInfo source) {
        return source != null ? this.populate(translator, source, new OwnerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(OwnerInfo source, OwnerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(ModelTranslator translator, OwnerInfo source, OwnerDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        // Service model objects do not provide an ID
        dest.id(null)
            .key(source.getKey())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))

        // These fields are not present on the service model
            .displayName(null)
            .contentPrefix(null)
            .defaultServiceLevel(null)
            .logLevel(null)
            .autobindDisabled(null)
            .autobindHypervisorDisabled(null)
            .contentAccessMode(null)
            .contentAccessModeList(null)
            .lastRefreshed(null)
            .parentOwner(null)
            .upstreamConsumer(null);

        return dest;
    }

}

/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import org.candlepin.dto.api.server.v1.HypervisorConsumerWithGuestDTO;
import org.candlepin.model.HypervisorConsumerWithGuest;

public class HypervisorConsumerWithGuestTranslator
    implements ObjectTranslator<HypervisorConsumerWithGuest, HypervisorConsumerWithGuestDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerWithGuestDTO translate(HypervisorConsumerWithGuest source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerWithGuestDTO translate(ModelTranslator translator,
        HypervisorConsumerWithGuest source) {

        return source != null ?
            this.populate(translator, source, new HypervisorConsumerWithGuestDTO()) :
            null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerWithGuestDTO populate(HypervisorConsumerWithGuest source,
        HypervisorConsumerWithGuestDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerWithGuestDTO populate(ModelTranslator translator,
        HypervisorConsumerWithGuest source, HypervisorConsumerWithGuestDTO destination) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setHypervisorConsumerUuid(source.getHypervisorConsumerUuid());
        destination.setHypervisorConsumerName(source.getHypervisorConsumerName());
        destination.setGuestUuid(source.getGuestConsumerUuid());
        destination.setGuestId(source.getGuestId());

        return destination;
    }

}


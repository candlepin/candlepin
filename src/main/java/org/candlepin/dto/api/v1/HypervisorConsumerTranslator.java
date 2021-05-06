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
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;


/**
 * The HypervisorConsumerTranslator provides translation from Consumer model objects to
 * HypervisorConsumerDTOs, as used by the API for hypervisor update requests.
 */
public class HypervisorConsumerTranslator implements ObjectTranslator<Consumer, HypervisorConsumerDTO> {

    private OwnerCurator ownerCurator;

    public HypervisorConsumerTranslator(OwnerCurator ownerCurator) {
        if (ownerCurator == null) {
            throw new IllegalArgumentException("OwnerCurator is null");
        }
        this.ownerCurator = ownerCurator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO translate(Consumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO translate(ModelTranslator translator, Consumer source) {
        return source != null ? this.populate(translator, source, new HypervisorConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO populate(Consumer source, HypervisorConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO populate(ModelTranslator translator, Consumer source,
        HypervisorConsumerDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.uuid(source.getUuid())
            .name(source.getName());

        if (source.getOwnerId() != null) {
            Owner owner = this.ownerCurator.findOwnerById(source.getOwnerId());
            if (owner != null && owner.getKey() != null) {
                NestedOwnerDTO ownerDTO = new NestedOwnerDTO();
                ownerDTO.setKey(owner.getKey());
                dest.setOwner(ownerDTO);
            }
        }

        return dest;
    }
}

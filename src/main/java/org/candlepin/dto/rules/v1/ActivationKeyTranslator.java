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
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;

import java.util.HashSet;
import java.util.Set;

/**
 * The ActivationKeyTranslator provides translation from ActivationKey model objects to ActivationKeyDTOs
 * for the Rules framework.
 */
public class ActivationKeyTranslator implements ObjectTranslator<ActivationKey, ActivationKeyDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO translate(ActivationKey source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO translate(ModelTranslator translator, ActivationKey source) {
        return source != null ? this.populate(translator, source, new ActivationKeyDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ActivationKey source, ActivationKeyDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ActivationKeyDTO populate(ModelTranslator modelTranslator, ActivationKey source,
        ActivationKeyDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.setId(source.getId());

        Set<ActivationKeyPool> pools = source.getPools();
        if (pools != null) {
            Set<ActivationKeyDTO.ActivationKeyPoolDTO> poolDTOs = new HashSet<>();

            for (ActivationKeyPool poolEntry : pools) {
                if (poolEntry != null) {
                    ActivationKeyDTO.ActivationKeyPoolDTO akPoolDTO =
                        new ActivationKeyDTO.ActivationKeyPoolDTO();
                    akPoolDTO.setQuantity(poolEntry.getQuantity());

                    ActivationKeyDTO.InternalPoolDTO internalPoolDTO = new ActivationKeyDTO.InternalPoolDTO();
                    internalPoolDTO.setId(poolEntry.getPool().getId());
                    internalPoolDTO.setAttributes(poolEntry.getPool().getAttributes());
                    internalPoolDTO.setProductAttributes(poolEntry.getPool().getProductAttributes());
                    akPoolDTO.setPool(internalPoolDTO);

                    poolDTOs.add(akPoolDTO);
                }
            }

            dest.setPools(poolDTOs);
        }
        else {
            dest.setPools(null);
        }

        return dest;
    }
}

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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;

import java.util.HashSet;
import java.util.Set;



/**
 * The ActivationKeyTranslator provides translation from ActivationKey model objects to ActivationKeyDTOs
 */
public class ActivationKeyTranslator extends TimestampedEntityTranslator<ActivationKey, ActivationKeyDTO> {

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
    public ActivationKeyDTO populate(ModelTranslator modelTranslator,
        ActivationKey source, ActivationKeyDTO dest) {

        dest = super.populate(modelTranslator, source, dest);

        dest.setId(source.getId())
            .setName(source.getName())
            .setDescription(source.getDescription())
            .setServiceLevel(source.getServiceLevel())
            .setAutoAttach(source.isAutoAttach());


        // Set activation key product IDs
        Set<Product> products = source.getProducts();
        if (products != null) {
            Set<String> productIds = new HashSet<>();

            for (Product prod : products) {
                if (prod != null && prod.getId() != null && !prod.getId().isEmpty()) {
                    productIds.add(prod.getId());
                }
            }

            dest.setProductIds(productIds);
        }
        else {
            dest.setProductIds(null);
        }

        // Set release version
        Release release = source.getReleaseVer();
        dest.setReleaseVersion(release != null ? release.getReleaseVer() : null);

        // Process nested DTO objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            dest.setOwner(modelTranslator.translate(source.getOwner(), OwnerDTO.class));

            Set<ActivationKeyPool> pools = source.getPools();
            if (pools != null) {
                Set<ActivationKeyDTO.ActivationKeyPoolDTO> poolDTOs = new HashSet<>();

                for (ActivationKeyPool poolEntry : pools) {
                    if (poolEntry != null) {
                        poolDTOs.add(new ActivationKeyDTO.ActivationKeyPoolDTO(
                            poolEntry.getPool().getId(), poolEntry.getQuantity()));
                    }
                }

                dest.setPools(poolDTOs);
            }
            else {
                dest.setPools(null);
            }

            // Process content overrides
            Set<? extends ContentOverride> overrides = source.getContentOverrides();
            if (overrides != null) {
                Set<ContentOverrideDTO> dtos = new HashSet<>();

                for (ContentOverride override : overrides) {
                    dtos.add(modelTranslator.translate(override, ContentOverrideDTO.class));
                }

                dest.setContentOverrides(dtos);
            }
            else {
                dest.setContentOverrides(null);
            }
        }
        else {
            dest.setOwner(null);
            dest.setPools(null);
        }

        return dest;
    }
}

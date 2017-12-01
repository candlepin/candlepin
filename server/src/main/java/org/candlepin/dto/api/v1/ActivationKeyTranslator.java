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
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.model.activationkeys.ActivationKeyPool;

import java.util.Collections;
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

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, OwnerDTO.class) : null);

            Set<ActivationKeyPool> pools = source.getPools();
            if (pools != null && !pools.isEmpty()) {
                for (ActivationKeyPool poolEntry : pools) {
                    if (poolEntry != null) {
                        dest.addPool(new ActivationKeyDTO.ActivationKeyPoolDTO(
                            poolEntry.getPool().getId(), poolEntry.getQuantity()));
                    }
                }
            }
            else {
                dest.setPools(Collections.<ActivationKeyDTO.ActivationKeyPoolDTO>emptySet());
            }

            Set<Product> products = source.getProducts();
            if (products != null && !products.isEmpty()) {
                for (Product prod : products) {
                    if (prod != null) {
                        dest.addProductId(prod.getId());
                    }
                }
            }
            else {
                dest.setProductIds(Collections.<String>emptySet());
            }

            Set<ActivationKeyContentOverride> overrides = source.getContentOverrides();
            if (overrides != null && !overrides.isEmpty()) {
                for (ActivationKeyContentOverride override : overrides) {
                    if (override != null) {
                        dest.addContentOverride(
                            new ActivationKeyDTO.ActivationKeyContentOverrideDTO(
                            override.getContentLabel(),
                            override.getName(),
                            override.getValue()));
                    }
                }
            }
            else {
                dest.setContentOverrides(
                    Collections.<ActivationKeyDTO.ActivationKeyContentOverrideDTO>emptySet());
            }

            Release release = source.getReleaseVer();
            if (release != null) {
                dest.setReleaseVersion(release.getReleaseVer());
            }
        }

        return dest;
    }
}

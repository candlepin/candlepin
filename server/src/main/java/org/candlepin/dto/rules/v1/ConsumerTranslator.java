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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;

import java.util.HashSet;
import java.util.Set;

/**
 * The ConsumerTranslator provides translation from Consumer model objects to
 * ConsumerDTOs, as used by the Rules framework.
 */
public class ConsumerTranslator extends TimestampedEntityTranslator<Consumer, ConsumerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(Consumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(ModelTranslator translator, Consumer source) {
        return source != null ? this.populate(translator, source, new ConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(Consumer source, ConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ModelTranslator translator, Consumer source, ConsumerDTO dest) {

        dest = super.populate(translator, source, dest);

        dest.setUuid(source.getUuid())
            .setUsername(source.getUsername())
            .setServiceLevel(source.getServiceLevel())
            .setFacts(source.getFacts());

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            dest.setOwner(translator.translate(source.getOwner(), OwnerDTO.class));

            Set<ConsumerInstalledProduct> installedProducts = source.getInstalledProducts();
            if (installedProducts != null) {
                Set<String> ips = new HashSet<String>();
                for (ConsumerInstalledProduct cip : installedProducts) {
                    if (cip != null && cip.getProductId() != null) {
                        ips.add(cip.getProductId());
                    }
                }
                dest.setInstalledProducts(ips);
            }

            Set<ConsumerCapability> capabilities = source.getCapabilities();
            if (capabilities != null) {
                Set<String> capabilitiesDTO = new HashSet<String>();
                for (ConsumerCapability capability : capabilities) {
                    if (capability != null && capability.getName() != null) {
                        capabilitiesDTO.add(capability.getName());
                    }
                }
                dest.setCapabilities(capabilitiesDTO);
            }

            dest.setType(translator.translate(source.getType(), ConsumerTypeDTO.class));
        }
        else {
            dest.setOwner(null);
            dest.setInstalledProducts(null);
            dest.setCapabilities(null);
            dest.setType(null);
        }

        return dest;
    }
}

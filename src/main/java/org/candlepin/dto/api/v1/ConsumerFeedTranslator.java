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
import org.candlepin.dto.api.server.v1.ConsumerFeedDTO;
import org.candlepin.dto.api.server.v1.ConsumerFeedInstalledProductDTO;
import org.candlepin.model.ConsumerFeed;
import org.candlepin.model.ConsumerFeedInstalledProduct;
import org.candlepin.util.Util;

import java.util.HashSet;
import java.util.Set;

public class ConsumerFeedTranslator implements ObjectTranslator<ConsumerFeed, ConsumerFeedDTO> {

    @Override
    public ConsumerFeedDTO translate(ConsumerFeed source) {
        return this.translate(null, source);
    }

    @Override
    public ConsumerFeedDTO translate(ModelTranslator translator, ConsumerFeed source) {
        return source != null ? this.populate(translator, source, new ConsumerFeedDTO()) : null;
    }

    @Override
    public ConsumerFeedDTO populate(ConsumerFeed source, ConsumerFeedDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public ConsumerFeedDTO populate(ModelTranslator translator, ConsumerFeed source,
        ConsumerFeedDTO destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setId(source.getId());
        destination.setUuid(source.getUuid());
        destination.setName(source.getName());
        destination.setTypeId(source.getTypeId());
        destination.setOwnerKey(source.getOwnerKey());
        destination.setLastCheckin(Util.toDateTime(source.getLastCheckin()));
        destination.setGuestId(source.getGuestId());
        destination.setHypervisorUuid(source.getHypervisorUuid());
        destination.setHypervisorName(source.getHypervisorName());
        destination.setServiceLevel(source.getServiceLevel());
        destination.setSyspurposeRole(source.getSyspurposeRole());
        destination.setSyspurposeUsage(source.getSyspurposeUsage());
        destination.setSyspurposeAddons(source.getSyspurposeAddons());
        destination.setFacts(source.getFacts());

        if (translator != null) {
            Set<ConsumerFeedInstalledProduct> installedProducts = source.getInstalledProducts();
            if (installedProducts != null) {
                ObjectTranslator<ConsumerFeedInstalledProduct, ConsumerFeedInstalledProductDTO> cipTranslator
                    = translator.findTranslatorByClass(ConsumerFeedInstalledProduct.class,
                    ConsumerFeedInstalledProductDTO.class);
                Set<ConsumerFeedInstalledProductDTO> ips = new HashSet<>();
                for (ConsumerFeedInstalledProduct cip : installedProducts) {
                    if (cip != null) {
                        ConsumerFeedInstalledProductDTO dto = cipTranslator.translate(translator, cip);
                        if (dto != null) {
                            ips.add(dto);
                        }
                    }
                }
                destination.setInstalledProducts(ips);
            }
        }

        return destination;
    }
}

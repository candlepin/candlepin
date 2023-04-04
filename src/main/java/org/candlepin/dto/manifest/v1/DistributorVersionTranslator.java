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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;

import java.util.Collections;
import java.util.Set;

/**
 * The DistributorVersionTranslator provides translation from DistributorVersion model objects
 * to DistributorVersionDTOs as used by the manifest import/export framework.
 */
public class DistributorVersionTranslator
    extends TimestampedEntityTranslator<DistributorVersion, DistributorVersionDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO translate(DistributorVersion source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO translate(ModelTranslator translator, DistributorVersion source) {
        return source != null ? this.populate(translator, source, new DistributorVersionDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO populate(DistributorVersion source, DistributorVersionDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO populate(ModelTranslator modelTranslator, DistributorVersion source,
        DistributorVersionDTO dest) {

        dest = super.populate(modelTranslator, source, dest);

        dest.setId(source.getId());
        dest.setName(source.getName());
        dest.setDisplayName(source.getDisplayName());

        if (modelTranslator != null) {

            Set<DistributorVersionCapability> capabilities = source.getCapabilities();
            if (capabilities != null && !capabilities.isEmpty()) {
                for (DistributorVersionCapability capability : capabilities) {
                    if (capability != null) {
                        dest.addCapability(new DistributorVersionDTO.DistributorVersionCapabilityDTO(
                            capability.getId(), capability.getName()));
                    }
                }
            }
            else {
                dest.setCapabilities(Collections.emptySet());
            }
        }

        return dest;
    }
}

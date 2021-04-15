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
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.util.Util;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The DistributorVersionTranslator provides translation from DistributorVersion model objects
 * to DistributorVersionDTOs as used by the API.
 */
public class DistributorVersionTranslator
    implements ObjectTranslator<DistributorVersion, DistributorVersionDTO> {

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

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .name(source.getName())
            .displayName(source.getDisplayName())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .capabilities(toDto(source.getCapabilities()));

        return dest;
    }

    private Set<DistributorVersionCapabilityDTO> toDto(Set<DistributorVersionCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.emptySet();
        }
        return capabilities.stream()
            .filter(Objects::nonNull)
            .map(this::toCapabilityDto)
            .collect(Collectors.toSet());
    }

    private DistributorVersionCapabilityDTO toCapabilityDto(DistributorVersionCapability source) {
        DistributorVersionCapabilityDTO result = new DistributorVersionCapabilityDTO();
        result.id(source.getId());
        result.name(source.getName());
        return result;
    }
}

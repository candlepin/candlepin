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
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.DistributorVersionDTO;
import org.candlepin.dto.manifest.v1.DistributorVersionDTO.DistributorVersionCapabilityDTO;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;


/**
 * DistributorVersionImporter
 */
public class DistributorVersionImporter {
    private static Logger log = LoggerFactory.getLogger(DistributorVersionImporter.class);

    private DistributorVersionCurator curator;

    public DistributorVersionImporter(DistributorVersionCurator curator) {
        this.curator = curator;
    }

    public DistributorVersionDTO createObject(ObjectMapper mapper, Reader reader) throws IOException {
        DistributorVersionDTO distributorVersion = mapper.readValue(reader, DistributorVersionDTO.class);

        if (distributorVersion != null) {
            distributorVersion.setId(null);

            Set<DistributorVersionCapabilityDTO> capabilities = distributorVersion.getCapabilities();
            capabilities.forEach(cap -> cap.setId(null));
        }

        return distributorVersion;
    }

    /**
     * @param distVers Set of Distributor Versions.
     */
    public void store(Set<DistributorVersionDTO> distVers) {
        log.debug("Creating/updating distributor versions");
        for (DistributorVersionDTO distVer : distVers) {
            // TODO: this should be using bulk entity lookup to improve performance
            DistributorVersion existing = curator.findByName(distVer.getName());
            if (existing == null) {
                DistributorVersion newDistVer = distributorVersionDTOtoDistributorVersionEntity(distVer);
                curator.create(newDistVer);
                log.debug("Created distributor version: " + distVer.getName());
            }
            else {
                existing.setCapabilities(capabilityDTOsToCapabilityEntities(distVer.getCapabilities()));
                existing.setDisplayName(distVer.getDisplayName());
                curator.merge(existing);
                log.debug("Updating distributor version: " + distVer.getName());
            }
        }
    }

    /**
     * Utility method that creates a new DistributorVersion object based on a DistributorVersionDTO.
     *
     * @param dto the DistributorVersionDTO that the data is drawn from
     *
     * @return a newly created and populated DistributorVersion object
     */
    private DistributorVersion distributorVersionDTOtoDistributorVersionEntity(DistributorVersionDTO dto) {
        DistributorVersion entity = new DistributorVersion();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setCreated(dto.getCreated());
        entity.setUpdated(dto.getUpdated());
        entity.setCapabilities(capabilityDTOsToCapabilityEntities(dto.getCapabilities()));
        return entity;
    }

    /**
     * Utility method that creates and populates a set of new DistributorVersionCapability objects
     * based on the provided set of DistributorVersionCapabilityDTOs.
     *
     * @param dtos the DistributorVersionCapabilityDTO Set that the data is drawn from
     *
     * @return a newly created and populated Set of DistributorVersionCapability objects
     */
    private Set<DistributorVersionCapability> capabilityDTOsToCapabilityEntities(
        Set<DistributorVersionDTO.DistributorVersionCapabilityDTO> dtos) {

        Set<DistributorVersionCapability> entities = new HashSet<>();
        for (DistributorVersionDTO.DistributorVersionCapabilityDTO dto : dtos) {
            if (dto != null) {
                DistributorVersionCapability capability = new DistributorVersionCapability();
                capability.setId(dto.getId());
                capability.setName(dto.getName());
                entities.add(capability);
            }
        }
        return entities;
    }
}

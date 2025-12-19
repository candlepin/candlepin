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

import org.candlepin.dto.manifest.v1.CdnDTO;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;


/**
 * CdnImporter reads Cdn objects from a manifest and creates/updates them on the system as needed.
 */
public class CdnImporter {
    private static Logger log =  LoggerFactory.getLogger(CdnImporter.class);

    private CdnCurator curator;

    public CdnImporter(CdnCurator curator) {
        this.curator = curator;
    }

    public CdnDTO createObject(ObjectMapper mapper, Reader reader) throws IOException {
        CdnDTO cdnDTO = mapper.readValue(reader, CdnDTO.class);
        cdnDTO.setId(null);
        return cdnDTO;
    }

    /**
     * Creates the imported CDNs, or updates them if they already exist.
     *
     * @param cdnSet Set of CDN's.
     */
    public void store(Set<CdnDTO> cdnSet) {
        log.debug("Creating/updating cdns");
        for (CdnDTO cdnDTO : cdnSet) {
            // TODO: this should be using bulk entity lookup to improve performance
            cdnDTO.setCertificate(null);
            Cdn existing = curator.getByLabel(cdnDTO.getLabel());
            if (existing == null) {
                Cdn entity = new Cdn();
                populateEntity(entity, cdnDTO);

                log.debug("Creating CDN: {}", cdnDTO);
                curator.create(entity);
            }
            else {
                log.debug("Updating CDN: {}", cdnDTO);
                existing.setName(cdnDTO.getName());
                existing.setUrl(cdnDTO.getUrl());
                curator.merge(existing);
            }
        }
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    private void populateEntity(Cdn entity, CdnDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the cdn model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the cdn dto is null");
        }

        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setUrl(dto.getUrl());
        entity.setLabel(dto.getLabel());
        entity.setUpdated(dto.getUpdated());
        entity.setCreated(dto.getCreated());

        if (dto.getCertificate() != null) {
            CdnCertificate cdnCert = new CdnCertificate();
            ImporterUtils.populateEntity(cdnCert, dto.getCertificate());
            cdnCert.setId(dto.getId());
            entity.setCertificate(cdnCert);
        }
    }
}

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
package org.candlepin.resource;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.DistributorVersionDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.resource.server.v1.DistributorVersionsApi;
import org.candlepin.resource.validation.DTOValidator;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * DistributorVersionResource
 */
public class DistributorVersionResource implements DistributorVersionsApi {

    private final I18n i18n;
    private final DistributorVersionCurator curator;
    private final ModelTranslator translator;
    private final DTOValidator validator;

    @Inject
    public DistributorVersionResource(I18n i18n, DistributorVersionCurator curator,
        ModelTranslator translator, DTOValidator validator) {
        this.i18n = Objects.requireNonNull(i18n);
        this.curator = Objects.requireNonNull(curator);
        this.translator = Objects.requireNonNull(translator);
        this.validator = Objects.requireNonNull(validator);
    }

    /**
     * Populates the specified entity with data from the provided DTO. This method will not set the
     * ID, key, upstream consumer, content access mode list or content access mode fields.
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
    private void populateEntity(DistributorVersion entity, DistributorVersionDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the distributor version model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the distributor version dto is null");
        }

        if (dto.getDisplayName() != null) {
            entity.setDisplayName(dto.getDisplayName());
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }

        if (dto.getCapabilities() != null) {
            if (dto.getCapabilities().isEmpty()) {
                entity.setCapabilities(Collections.emptySet());
            }
            else {
                entity.setCapabilities(dto.getCapabilities()
                    .stream()
                    .map(capability -> new DistributorVersionCapability(entity, capability.getName()))
                    .collect(Collectors.toSet()));
            }
        }

    }

    @Override
    @Transactional
    public Stream<DistributorVersionDTO> getVersions(String nameSearch, String capability) {
        List<DistributorVersion> versions;
        if (!StringUtils.isBlank(nameSearch)) {
            versions = curator.findByNameSearch(nameSearch);
        }
        else if (!StringUtils.isBlank(capability)) {
            versions = curator.findByCapability(capability);
        }
        else {
            versions = curator.listAll();
        }

        return versions.stream().map(
            this.translator.getStreamMapper(DistributorVersion.class, DistributorVersionDTO.class));
    }

    @Override
    @Transactional
    public void delete(String id) {
        DistributorVersion dv = curator.get(id);
        if (dv != null) {
            curator.delete(dv);
        }
    }

    @Override
    @Transactional
    public DistributorVersionDTO create(DistributorVersionDTO dto) {
        this.validator.validateCollectionElementsNotNull(dto::getCapabilities);
        DistributorVersion existing = curator.findByName(dto.getName());
        if (existing != null) {
            throw new BadRequestException(
                this.i18n.tr("A distributor version with name {0} already exists", dto.getName()));
        }
        DistributorVersion toCreate = new DistributorVersion();
        populateEntity(toCreate, dto);

        toCreate = this.curator.create(toCreate, true);

        return this.translator.translate(toCreate, DistributorVersionDTO.class);
    }

    @Override
    @Transactional
    public DistributorVersionDTO update(String id, DistributorVersionDTO dto) {
        this.validator.validateCollectionElementsNotNull(dto::getCapabilities);

        DistributorVersion existing = verifyAndLookupDistributorVersion(id);
        existing.setDisplayName(dto.getDisplayName());
        existing.setCapabilities(dto.getCapabilities()
            .stream()
            .map(capability -> new DistributorVersionCapability(existing, capability.getName()))
            .collect(Collectors.toSet()));

        DistributorVersion updated = this.curator.merge(existing);
        this.curator.flush();

        return this.translator.translate(updated, DistributorVersionDTO.class);
    }

    private DistributorVersion verifyAndLookupDistributorVersion(String id) {
        DistributorVersion dv = curator.get(id);

        if (dv == null) {
            throw new NotFoundException(i18n.tr("No such distributor version: {0}", id));
        }
        return dv;
    }
}

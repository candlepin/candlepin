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
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.resource.server.v1.ConsumerTypeApi;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.stream.Stream;

import javax.inject.Inject;


/**
 * API implementation for ConsumerType operations
 */
public class ConsumerTypeResource implements ConsumerTypeApi {
    private static Logger log = LoggerFactory.getLogger(ConsumerTypeResource.class);

    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;
    private ModelTranslator translator;

    @Inject
    public ConsumerTypeResource(ConsumerTypeCurator consumerTypeCurator, I18n i18n,
        ModelTranslator translator) {

        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
        this.translator = translator;
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
    private void populateEntity(ConsumerType entity, ConsumerTypeDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getLabel() != null) {
            entity.setLabel(dto.getLabel());
        }

        if (dto.getManifest() != null) {
            entity.setManifest(dto.getManifest());
        }
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public Stream<ConsumerTypeDTO> getConsumerTypes() {
        return this.consumerTypeCurator.listAll()
            .stream()
            .map(this.translator.getStreamMapper(ConsumerType.class, ConsumerTypeDTO.class));
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public ConsumerTypeDTO getConsumerType(String id) {
        ConsumerType type = consumerTypeCurator.get(id);

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with id \"{0}\" could not be found.", id));
        }

        return this.translator.translate(type, ConsumerTypeDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public ConsumerTypeDTO createConsumerType(ConsumerTypeDTO dto) {
        try {
            ConsumerType type = new ConsumerType();

            this.populateEntity(type, dto);
            type = this.consumerTypeCurator.create(type, true);

            return this.translator.translate(type, ConsumerTypeDTO.class);
        }
        catch (Exception e) {
            log.error("Problem creating unit type: ", e);
            throw new BadRequestException(i18n.tr("Problem creating unit type: {0}", dto));
        }
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public ConsumerTypeDTO updateConsumerType(String id, ConsumerTypeDTO dto) {
        ConsumerType type = consumerTypeCurator.get(id);

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with label {0} could not be found.", id));
        }

        this.populateEntity(type, dto);
        type = this.consumerTypeCurator.merge(type);
        this.consumerTypeCurator.flush();

        return this.translator.translate(type, ConsumerTypeDTO.class);
    }

    /**
     * {@InheritDoc}
     */
    @Override
    @Transactional
    public void deleteConsumerType(String id) {
        ConsumerType type = consumerTypeCurator.get(id);

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with id {0} could not be found.", id));
        }

        consumerTypeCurator.delete(type);
    }
}

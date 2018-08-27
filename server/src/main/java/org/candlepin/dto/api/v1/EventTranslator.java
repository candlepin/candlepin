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

import org.candlepin.audit.Event;
import org.candlepin.auth.PrincipalData;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;

/**
 * The EventTranslator provides translation from Event model objects to EventDTOs
 */
public class EventTranslator implements ObjectTranslator<Event, EventDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO translate(Event source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO translate(ModelTranslator modelTranslator, Event source) {
        return source != null ? this.populate(modelTranslator, source, new EventDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO populate(Event source, EventDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO populate(ModelTranslator modelTranslator, Event source, EventDTO destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setId(source.getId());
        destination.setTargetName(source.getTargetName());
        destination.setConsumerUuid(source.getConsumerUuid());
        destination.setEntityId(source.getEntityId());
        destination.setMessageText(source.getMessageText());
        destination.setOwnerId(source.getOwnerId());
        destination.setPrincipalStore(source.getPrincipalStore());
        destination.setReferenceId(source.getReferenceId());
        destination.setTimestamp(source.getTimestamp());
        destination.setType(source.getType() != null ? source.getType().name() : null);
        destination.setTarget(source.getTarget() != null ? source.getTarget().name() : null);
        destination.setReferenceType(source.getReferenceType() != null ?
            source.getReferenceType().name() :
            null);
        destination.setEventData(source.getEventData());

        if (modelTranslator != null) {
            PrincipalData principalData = source.getPrincipal();
            EventDTO.PrincipalDataDTO principalDataDTO =
                new EventDTO.PrincipalDataDTO(principalData.getType(), principalData.getName());
            destination.setPrincipal(principalDataDTO);
        }

        return destination;
    }
}

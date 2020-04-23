/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.GuestIdDTOArrayElement;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.resource.util.GuestMigration;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Provider;

/**
 * API Gateway for registered consumers guests
 */
public class GuestIdResource implements ConsumersApi{

    private static Logger log = LoggerFactory.getLogger(GuestIdResource.class);

    private GuestIdCurator guestIdCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private Provider<GuestMigration> migrationProvider;
    private ModelTranslator translator;

    @Inject
    private PrincipalProvider principalProvider;

    @Inject
    public GuestIdResource(GuestIdCurator guestIdCurator, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, ConsumerResource consumerResource, I18n i18n,
        EventFactory eventFactory, EventSink sink, Provider<GuestMigration> migrationProvider,
        ModelTranslator translator) {

        this.guestIdCurator = guestIdCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerResource = consumerResource;
        this.i18n = i18n;
        this.eventFactory = eventFactory;
        this.sink = sink;
        this.migrationProvider = migrationProvider;
        this.translator = translator;
    }

    @Override
    public CandlepinQuery<GuestIdDTOArrayElement> getGuestIds(@Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        return  translator.translateQuery(guestIdCurator.listByConsumer(consumer),
            GuestIdDTOArrayElement.class);
    }

    @Override
    public GuestIdDTO getGuestId(@Verify(Consumer.class) String consumerUuid, String guestId) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        GuestId result = validateGuestId(
            guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);
        return translator.translate(result, GuestIdDTO.class);
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     *
     * @param guestId
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntity(GuestId guestId, GuestIdDTO dto) {
        if (guestId == null) {
            throw new IllegalArgumentException("the guestId model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the guestId dto is null");
        }

        guestId.setId(dto.getId());
        guestId.setGuestId(dto.getGuestId());
        if (dto.getAttributes() != null) {
            guestId.setAttributes(dto.getAttributes());
        }
    }

    /**
     * Populates the specified entities with data from the provided guestIds.
     *
     * @param entities
     *  The entities instance to populate
     *
     * @param guestIds
     *  The list of string containing the guestIds to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntities(List<GuestId> entities, List<String> guestIds) {
        if (entities == null) {
            throw new IllegalArgumentException("the guestId model entity is null");
        }

        if (guestIds == null) {
            throw new IllegalArgumentException("the list of guestId is null");
        }

        for (String guestId : guestIds) {
            if (guestId == null) {
                continue;
            }
            entities.add(new GuestId(guestId));
        }
    }

    @Override
    public void updateGuests(@Verify(Consumer.class) String consumerUuid, List<GuestIdDTO> guestIdDTOs) {
        Consumer toUpdate = consumerCurator.findByUuid(consumerUuid);

        // Create a skeleton consumer for consumerResource.performConsumerUpdates
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setGuestIds(guestIdDTOs);

        Set<String> allGuestIds = new HashSet<>();
        for (GuestIdDTO gid : consumer.getGuestIds()) {
            allGuestIds.add(gid.getGuestId());
        }
        VirtConsumerMap guestConsumerMap = consumerCurator.getGuestConsumersMap(
            toUpdate.getOwnerId(), allGuestIds);

        GuestMigration guestMigration = migrationProvider.get().buildMigrationManifest(consumer, toUpdate);
        if (consumerResource.performConsumerUpdates(consumer, toUpdate, guestMigration)) {

            if (guestMigration.isMigrationPending()) {
                guestMigration.migrate();
            }
            else {
                consumerCurator.update(toUpdate);
            }
        }
    }

    @Override
    public void updateGuest(
        @Verify(Consumer.class) String consumerUuid, String guestId, GuestIdDTO updatedDTO) {

        // I'm not sure this can happen
        if (guestId == null || guestId.isEmpty()) {
            throw new BadRequestException(
                i18n.tr("Please supply a valid guest id"));
        }

        if (updatedDTO == null) {
            // If they're not sending attributes, we can get the guestId from the url
            updatedDTO = new GuestIdDTO().guestId(guestId);
        }

        // Allow the id to be left out in this case, we can use the path param
        if (updatedDTO.getGuestId() == null) {
            updatedDTO.setGuestId(guestId);
        }

        // If the guest uuids do not match, something is wrong
        if (!guestId.equalsIgnoreCase(updatedDTO.getGuestId())) {
            throw new BadRequestException(
                i18n.tr("Guest ID in json \"{0}\" does not match path guest ID \"{1}\"",
                    updatedDTO.getGuestId(), guestId));
        }

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        GuestId guestIdEntity = new GuestId();
        populateEntity(guestIdEntity, updatedDTO);
        guestIdEntity.setConsumer(consumer);
        GuestId toUpdate = guestIdCurator.findByGuestIdAndOrg(guestId, consumer.getOwnerId());
        if (toUpdate != null) {
            guestIdEntity.setId(toUpdate.getId());
        }
        guestIdCurator.merge(guestIdEntity);
    }

    @Override
    public void deleteGuest(@Verify(Consumer.class) String consumerUuid, String guestId, Boolean unregister) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        GuestId toDelete = validateGuestId(guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);

        if (unregister.booleanValue()) {
            Principal principal = (this.principalProvider == null ? null : this.principalProvider.get());
            unregisterConsumer(toDelete, principal);
        }

        sink.queueEvent(eventFactory.guestIdDeleted(toDelete));
        guestIdCurator.delete(toDelete);
    }

    private GuestId validateGuestId(GuestId guest, String guestUuid) {
        if (guest == null) {
            throw new NotFoundException(i18n.tr("Guest with UUID {0} could not be found.", guestUuid));
        }

        return guest;
    }

    private void unregisterConsumer(GuestId guest, Principal principal) {
        Consumer guestConsumer = consumerCurator.findByVirtUuid(guest.getGuestId(),
            guest.getConsumer().getOwnerId());
        if (guestConsumer != null) {
            if ((principal == null) || principal.canAccess(guestConsumer, SubResource.NONE, Access.ALL)) {
                consumerResource.deleteConsumer(guestConsumer.getUuid(), principal);
            }
            else {
                ConsumerType type = this.consumerTypeCurator.get(guestConsumer.getTypeId());

                throw new ForbiddenException(i18n.tr("Cannot unregister {0} {1} because: {2}",
                    type, guestConsumer.getName(), i18n.tr("Invalid Credentials")));
            }
        }
    }
}

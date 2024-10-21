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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTOArrayElement;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.server.v1.GuestIdsApi;
import org.candlepin.resource.util.GuestMigration;

import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;



public class GuestIdResource implements GuestIdsApi {

    private final I18n i18n;
    private final EventSink sink;
    private final ConsumerCurator consumerCurator;
    private final GuestIdCurator guestIdCurator;
    private final PrincipalProvider principalProvider;
    private final EventFactory eventFactory;
    private final ModelTranslator translator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final Provider<GuestMigration> migrationProvider;
    private final ConsumerResource consumerResource;
    private PagingUtilFactory pagingUtilFactory;

    @Inject
    @SuppressWarnings({"checkstyle:parameternumber"})
    public GuestIdResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        GuestIdCurator guestIdCurator,
        I18n i18n,
        EventSink sink,
        EventFactory eventFactory,
        Provider<GuestMigration> migrationProvider,
        ModelTranslator translator,
        PrincipalProvider principalProvider,
        ConsumerResource consumerResource,
        PagingUtilFactory  pagingUtilFactory) {

        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.guestIdCurator = guestIdCurator;
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.sink = Objects.requireNonNull(sink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.migrationProvider = Objects.requireNonNull(migrationProvider);
        this.translator = Objects.requireNonNull(translator);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.consumerResource = consumerResource;
        this.pagingUtilFactory = pagingUtilFactory;
    }

    @Override
    @Transactional
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
    @Transactional
    public void deleteGuest(@Verify(Consumer.class) String consumerUuid, String guestId, Boolean unregister) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        GuestId toDelete = validateGuestId(guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);

        if (unregister) {
            Principal principal = (this.principalProvider == null ? null : this.principalProvider.get());
            unregisterConsumer(toDelete, principal);
        }

        sink.queueEvent(eventFactory.guestIdDeleted(toDelete));
        guestIdCurator.delete(toDelete);
    }

    @Override
    @Transactional
    @RootResource.LinkedResource
    public Stream<GuestIdDTOArrayElement> getGuestIds(@Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        List<GuestId> guestIds = guestIdCurator.listByConsumer(consumer);
        if (guestIds != null) {
            Stream<GuestIdDTOArrayElement> stream = guestIds.stream()
                .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTOArrayElement.class));
            return this.pagingUtilFactory.forClass(GuestIdDTOArrayElement.class)
                .applyPaging(stream, guestIds.size());
        }
        return Stream.empty();
    }

    @Override
    @Transactional
    public GuestIdDTO getGuestId(@Verify(Consumer.class) String consumerUuid, String guestId) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        GuestId result = validateGuestId(
            guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);
        return translator.translate(result, GuestIdDTO.class);
    }

    @Override
    @Transactional
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

        // TODO: FIXME: Stop calling into consumer resource to do this work. Move common work to some
        // consumer service/controller or something.
        if (consumerResource.performConsumerUpdates(consumer, toUpdate, guestMigration)) {
            if (guestMigration.isMigrationPending()) {
                guestMigration.migrate();
            }
            else {
                consumerCurator.update(toUpdate);
            }
        }
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
                consumerResource.deleteConsumer(guestConsumer.getUuid());
            }
            else {
                ConsumerType type = this.consumerTypeCurator.get(guestConsumer.getTypeId());

                throw new ForbiddenException(i18n.tr("Cannot unregister {0} {1} because: {2}",
                    type, guestConsumer.getName(), i18n.tr("Invalid Credentials")));
            }
        }
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
    private void populateEntity(GuestId guestId, GuestIdDTO dto) {
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
}

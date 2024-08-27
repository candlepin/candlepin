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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTOArrayElement;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.util.Providers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.inject.Provider;


/**
 * GuestIdResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GuestIdResourceTest {

    private I18n i18n;
    private Provider<I18n> i18nProvider = () -> i18n;

    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private GuestIdCurator guestIdCurator;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private EventSink sink;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private PrincipalProvider principalProvider;
    @Mock private ConsumerResource consumerResource;

    private GuestIdResource resource;

    private Configuration config;
    private Consumer consumer;
    private Owner owner;
    private ConsumerType ct;
    protected ModelTranslator modelTranslator;
    private PagingUtilFactory pagingUtilFactory;
    private GuestMigration testMigration;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
        this.testMigration = spy(new GuestMigration(consumerCurator));

        this.modelTranslator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator,
            this.ownerCurator);
        this.pagingUtilFactory = new PagingUtilFactory(this.config, i18nProvider);

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.owner = TestUtil.createOwner();
        this.ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        this.ct.setId("test-system-ctype");

        this.consumer = new Consumer()
            .setUuid(Util.generateUUID())
            .setName("consumer")
            .setUsername("test")
            .setOwner(owner)
            .setType(ct);

        this.resource = new GuestIdResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.guestIdCurator,
            this.i18n,
            this.sink,
            this.eventFactory,
            Providers.of(this.testMigration),
            this.modelTranslator,
            this.principalProvider,
            this.consumerResource,
            this.pagingUtilFactory
        );

        when(consumerCurator.findByUuid(consumer.getUuid())).thenReturn(consumer);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(this.eventBuilder.setEventData(any(Consumer.class)))
            .thenReturn(this.eventBuilder);
        when(this.eventFactory.getEventBuilder(any(Event.Target.class), any(Event.Type.class)))
            .thenReturn(this.eventBuilder);
    }

    @Test
    public void getGuestIdsEmpty() {
        List<GuestId> queryList = new LinkedList<>();
        List<GuestIdDTOArrayElement> dtoQueryList = new LinkedList<>();
        when(guestIdCurator.listByConsumer(eq(consumer))).thenReturn(queryList);

        Stream<GuestIdDTOArrayElement> result = resource.getGuestIds(consumer.getUuid());
        assertEquals(result.toList(), dtoQueryList);
    }

    @Test
    public void getGuestIdNoGuests() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class))).thenReturn(null);

        assertThrows(NotFoundException.class,
            () -> resource.getGuestId(consumer.getUuid(), "some-id"));
    }

    @Test
    public void getGuestId() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class)))
            .thenReturn(new GuestId("guest"));
        GuestIdDTO result = resource.getGuestId(consumer.getUuid(), "some-id");
        assertEquals(TestUtil.createGuestIdDTO("guest"), result);
    }

    @Test
    public void updateGuests() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));

        when(consumerResource.performConsumerUpdates(any(ConsumerDTO.class), any(Consumer.class),
            eq(testMigration))).thenReturn(true);
        resource.updateGuests(consumer.getUuid(), guestIds);

        verify(testMigration, times(1)).migrate();
    }

    @Test
    public void updateGuestsNoUpdate() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));


        // resource tells us nothing changed
        resource.updateGuests(consumer.getUuid(), guestIds);

        verify(consumerCurator, never()).update(eq(consumer));
    }

    @Test
    public void updateGuest() {
        GuestIdDTO guest = TestUtil.createGuestIdDTO("some_guest");
        GuestId guestEnt = new GuestId();
        guestEnt.setId("some_id");
        resource.updateGuest(consumer.getUuid(), guest.getGuestId(), guest);
        when(guestIdCurator.findByGuestIdAndOrg(anyString(), any(String.class))).thenReturn(guestEnt);
        ArgumentCaptor<GuestId> guestCaptor = ArgumentCaptor.forClass(GuestId.class);
        verify(guestIdCurator, times(1)).merge(guestCaptor.capture());
        GuestId result = guestCaptor.getValue();
        assertEquals(consumer, result.getConsumer());
    }

    @Test
    public void updateGuestMismatchedGuestId() {
        GuestIdDTO guest = TestUtil.createGuestIdDTO("some_guest");

        assertThrows(BadRequestException.class,
            () -> resource.updateGuest(consumer.getUuid(), "other_id", guest));
    }

    /*
     * Update should add the id from the url to the GuestId object
     * if it does not already have one.
     */
    @Test
    public void updateGuestNoGuestId() {
        GuestIdDTO guestIdDTO = new GuestIdDTO();

        resource.updateGuest(consumer.getUuid(), "some_id", guestIdDTO);

        ArgumentCaptor<GuestId> guestCaptor = ArgumentCaptor.forClass(GuestId.class);
        verify(guestIdCurator, times(1)).merge(guestCaptor.capture());
        GuestId guest = guestCaptor.getValue();
        assertEquals(consumer, guest.getConsumer());
        assertEquals("some_id", guest.getGuestId());
        verify(guestIdCurator, times(1)).merge(eq(guest));
    }

    @Test
    public void deleteGuestNoConsumer() {
        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwnerId())).thenReturn(null);

        resource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), false);

        verify(guestIdCurator, times(1)).delete(eq(guest));
    }

    @Test
    public void updateGuestRevokeHostSpecific() {
        Consumer guestConsumer = new Consumer()
            .setName("guest_consumer")
            .setUsername("guest_consumer")
            .setOwner(owner)
            .setType(ct);

        GuestId originalGuest = new GuestId("guest-id", guestConsumer);
        GuestIdDTO guest = TestUtil.createGuestIdDTO("guest-id");

        when(guestIdCurator.findByGuestIdAndOrg(
            eq(guest.getGuestId()), eq(owner.getId()))).thenReturn(originalGuest);
        when(consumerCurator.findByVirtUuid(eq(guest.getGuestId()),
            eq(owner.getId()))).thenReturn(guestConsumer);

        resource.updateGuest(consumer.getUuid(),
            guest.getGuestId(), guest);

        ArgumentCaptor<GuestId> captor = ArgumentCaptor.forClass(GuestId.class);
        verify(guestIdCurator, times(1)).merge(captor.capture());

        GuestId guestId = captor.getValue();
        assertEquals("guest-id", guestId.getGuestId());

        // We now check for migration when the system checks in, not during guest ID updates.
        verify(testMigration, never()).migrate();
    }

    @Test
    public void deleteGuestAndUnregister() {
        Consumer guestConsumer = new Consumer()
            .setUuid(Util.generateUUID())
            .setName("guest_consumer")
            .setUsername("guest_consumer")
            .setOwner(owner)
            .setType(ct);

        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwnerId())).thenReturn(guestConsumer);

        resource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), true);

        verify(guestIdCurator, times(1)).delete(eq(guest));
        verify(consumerResource, times(1))
            .deleteConsumer(eq(guestConsumer.getUuid()));
    }

    /*
     * Should behave just like deleteGuest with no consumer
     */
    @Test
    public void deleteGuestNotFound() {
        GuestId guest = new GuestId("guest-id", consumer);

        when(guestIdCurator.findByConsumerAndId(eq(consumer), eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(), consumer.getOwnerId()))
            .thenReturn(null);

        resource.deleteGuest(consumer.getUuid(), guest.getGuestId(), true);

        verify(guestIdCurator, times(1)).delete(eq(guest));
        verify(testMigration, never()).migrate();
    }

}

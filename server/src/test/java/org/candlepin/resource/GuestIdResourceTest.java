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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.GuestIdDTOArrayElement;
import org.candlepin.model.CandlepinQuery;
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
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ElementTransformer;
import org.candlepin.util.ServiceLevelValidator;
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

import javax.inject.Provider;



/**
 * GuestIdResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GuestIdResourceTest {

    private I18n i18n;

    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private GuestIdCurator guestIdCurator;
    @Mock private ConsumerResourceForTesting consumerResource;
    @Mock private EventFactory eventFactory;
    @Mock private EventSink sink;
    @Mock private ServiceLevelValidator mockedServiceLevelValidator;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private JobManager jobManager;
    @Mock private DTOValidator dtoValidator;

    private GuestIdResource guestIdResource;

    private Consumer consumer;
    private Owner owner;
    private ConsumerType ct;
    protected ModelTranslator modelTranslator;

    private GuestMigration testMigration;
    private Provider<GuestMigration> migrationProvider;

    @BeforeEach
    public void setUp() {
        testMigration = spy(new GuestMigration(consumerCurator));
        migrationProvider = Providers.of(testMigration);

        this.modelTranslator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator,
            this.ownerCurator);

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        owner = TestUtil.createOwner();
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct.setId("test-system-ctype");

        consumer = new Consumer("consumer", "test", owner, ct).setUuid(Util.generateUUID());
        guestIdResource = new GuestIdResource(guestIdCurator, consumerCurator, consumerTypeCurator,
            consumerResource, i18n, eventFactory, sink, migrationProvider, this.modelTranslator);

        when(consumerCurator.findByUuid(consumer.getUuid())).thenReturn(consumer);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
    }

    @Test
    public void getGuestIdsEmpty() {
        CandlepinQuery<GuestId> query = mock(CandlepinQuery.class);
        CandlepinQuery<GuestIdDTO> dtoQuery = mock(CandlepinQuery.class);
        when(guestIdCurator.listByConsumer(eq(consumer))).thenReturn(query);
        when(query.transform((any(ElementTransformer.class)))).thenReturn(dtoQuery);
        CandlepinQuery<GuestIdDTOArrayElement> result = guestIdResource.getGuestIds(consumer.getUuid());
        verify(query, times(1)).transform(any(ElementTransformer.class));
        assertEquals(result, dtoQuery);
    }

    @Test
    public void getGuestIdNoGuests() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class))).thenReturn(null);

        assertThrows(NotFoundException.class,
            () -> guestIdResource.getGuestId(consumer.getUuid(), "some-id"));
    }

    @Test
    public void getGuestId() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class)))
            .thenReturn(new GuestId("guest"));
        GuestIdDTO result = guestIdResource.getGuestId(consumer.getUuid(), "some-id");
        assertEquals(TestUtil.createGuestIdDTO("guest"), result);
    }

    @Test
    public void updateGuests() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));
        when(consumerResource.performConsumerUpdates(any(ConsumerDTO.class),
            eq(consumer), any(GuestMigration.class))).
            thenReturn(true);

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);

        verify(consumerResource, times(1))
            .performConsumerUpdates(any(ConsumerDTO.class), eq(consumer), any(GuestMigration.class));
        // consumerResource returned true, so the consumer should be updated
        verify(testMigration, times(1)).migrate();
    }

    @Test
    public void updateGuestsNoUpdate() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));

        // consumerResource tells us nothing changed
        when(consumerResource.performConsumerUpdates(any(ConsumerDTO.class),
            eq(consumer), any(GuestMigration.class))).
            thenReturn(false);

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        verify(consumerResource, times(1))
            .performConsumerUpdates(any(ConsumerDTO.class), eq(consumer), any(GuestMigration.class));
        verify(consumerCurator, never()).update(eq(consumer));
    }

    @Test
    public void updateGuest() {
        GuestIdDTO guest = TestUtil.createGuestIdDTO("some_guest");
        GuestId guestEnt = new GuestId();
        guestEnt.setId("some_id");
        guestIdResource.updateGuest(consumer.getUuid(), guest.getGuestId(), guest);
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
            () -> guestIdResource.updateGuest(consumer.getUuid(), "other_id", guest));
    }

    /*
     * Update should add the id from the url to the GuestId object
     * if it does not already have one.
     */
    @Test
    public void updateGuestNoGuestId() {
        GuestIdDTO guestIdDTO = new GuestIdDTO();
        guestIdResource.updateGuest(consumer.getUuid(), "some_id", guestIdDTO);
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
        guestIdResource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), false);
        verify(guestIdCurator, times(1)).delete(eq(guest));
        verify(consumerResource, never()).checkForMigration(eq(consumer), any(Consumer.class));
    }

    @Test
    public void updateGuestRevokeHostSpecific() {
        Consumer guestConsumer =
            new Consumer("guest_consumer", "guest_consumer", owner, ct);
        GuestId originalGuest = new GuestId("guest-id", guestConsumer);
        GuestIdDTO guest = TestUtil.createGuestIdDTO("guest-id");

        when(guestIdCurator.findByGuestIdAndOrg(
            eq(guest.getGuestId()), eq(owner.getId()))).thenReturn(originalGuest);
        when(consumerCurator.findByVirtUuid(eq(guest.getGuestId()),
            eq(owner.getId()))).thenReturn(guestConsumer);

        guestIdResource.updateGuest(consumer.getUuid(),
            guest.getGuestId(), guest);

        ArgumentCaptor<GuestId> captor = ArgumentCaptor.forClass(GuestId.class);
        verify(guestIdCurator, times(1)).merge(captor.capture());

        GuestId guestId = captor.getValue();
        assertEquals("guest-id", guestId.getGuestId());

        // We now check for migration when the system checks in, not during guest ID updates.
        verify(consumerResource, times(0))
            .checkForMigration(any(Consumer.class), any(Consumer.class));
    }

    @Test
    public void deleteGuestAndUnregister() {
        Consumer guestConsumer =
            new Consumer("guest_consumer", "guest_consumer", owner, ct);
        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwnerId())).thenReturn(guestConsumer);
        guestIdResource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), true);
        verify(guestIdCurator, times(1)).delete(eq(guest));
        verify(consumerResource, never())
            .checkForMigration(eq(consumer), eq(guestConsumer));
        verify(consumerResource, times(1))
            .deleteConsumer(eq(guestConsumer.getUuid()), nullable(Principal.class));
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

        guestIdResource.deleteGuest(consumer.getUuid(), guest.getGuestId(), true);

        verify(guestIdCurator, times(1)).delete(eq(guest));
        verify(consumerResource, never())
            .checkForMigration(eq(consumer), any(Consumer.class));
    }

    private Page<List<GuestId>> buildPaginatedGuestIdList(List<GuestId> guests) {
        Page<List<GuestId>> page = new Page<>();
        page.setPageData(guests);
        return page;
    }

    /*
     * ConsumerResource.performConsumerUpdates and revokeGuestEntitlementsNotMatchingHost
     * are protected, so we cannot verify that they have been called.
     * This class allows us to override the methods to make sure they have been used.
     */
    private class ConsumerResourceForTesting extends ConsumerResource {
        public ConsumerResourceForTesting() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, consumerEnricher, null, modelTranslator, jobManager,
                dtoValidator);
        }

        public void checkForMigration(Consumer host, Consumer guest) {
            // Intentionally left empty
        }
    }
}

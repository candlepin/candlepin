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
package org.candlepin.resource.test;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.GuestIdResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

/**
 * GuestIdResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class GuestIdResourceTest {

    private I18n i18n;

    @Mock
    private ConsumerCurator consumerCurator;

    @Mock
    private GuestIdCurator guestIdCurator;

    @Mock
    private ConsumerResourceForTesting consumerResource;

    @Mock
    private EventFactory eventFactory;

    @Mock
    private EventSink sink;

    private GuestIdResource guestIdResource;

    private Consumer consumer;
    private Owner owner;
    private ConsumerType ct;

    @Before
    public void setUp() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        owner = new Owner("test-owner", "Test Owner");
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumer = new Consumer("consumer", "test", owner, ct);
        guestIdResource = new GuestIdResource(guestIdCurator,
            consumerCurator, consumerResource, i18n, eventFactory, sink);
        when(consumerCurator.findByUuid(consumer.getUuid())).thenReturn(consumer);
        when(consumerCurator.verifyAndLookupConsumer(
            consumer.getUuid())).thenReturn(consumer);
    }

    @Test
    public void getGuestIdsEmpty() {
        when(guestIdCurator.listByConsumer(eq(consumer), any(PageRequest.class)))
            .thenReturn(buildPaginatedGuestIdList(new LinkedList<GuestId>()));
        List<GuestId> result = guestIdResource.getGuestIds(consumer.getUuid(), null);
        assertEquals(0, result.size());
    }

    @Test
    public void getGuestIds() {
        List<GuestId> guestIds = new LinkedList<GuestId>();
        guestIds.add(new GuestId("1"));
        guestIds.add(new GuestId("2"));
        when(guestIdCurator.listByConsumer(eq(consumer), any(PageRequest.class)))
            .thenReturn(buildPaginatedGuestIdList(guestIds));
        List<GuestId> result = guestIdResource.getGuestIds(consumer.getUuid(), null);
        assertEquals(2, result.size());
        assertTrue(result.contains(new GuestId("1")));
        assertTrue(result.contains(new GuestId("2")));
    }

    @Test(expected = NotFoundException.class)
    public void getGuestIdNoGuests() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class)))
            .thenReturn(null);
        GuestId result = guestIdResource.getGuestId(consumer.getUuid(), "some-id");
    }

    @Test
    public void getGuestId() {
        when(guestIdCurator.findByConsumerAndId(eq(consumer), any(String.class)))
            .thenReturn(new GuestId("guest"));
        GuestId result = guestIdResource.getGuestId(consumer.getUuid(), "some-id");
        assertEquals(new GuestId("guest"), result);
    }

    @Test
    public void updateGuests() {
        List<GuestId> guestIds = new LinkedList<GuestId>();
        guestIds.add(new GuestId("1"));
        when(consumerResource.performConsumerUpdates(any(Consumer.class),
            eq(consumer))).thenReturn(true);

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        Mockito.verify(consumerResource, Mockito.times(1))
            .performConsumerUpdates(any(Consumer.class), eq(consumer));
        // consumerResource returned true, so the consumer should be updated
        Mockito.verify(consumerCurator, Mockito.times(1)).update(eq(consumer));
    }

    @Test
    public void updateGuestsNoUpdate() {
        List<GuestId> guestIds = new LinkedList<GuestId>();
        guestIds.add(new GuestId("1"));

        // consumerResource tells us nothing changed
        when(consumerResource.performConsumerUpdates(any(Consumer.class),
            eq(consumer))).thenReturn(false);

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        Mockito.verify(consumerResource, Mockito.times(1))
            .performConsumerUpdates(any(Consumer.class), eq(consumer));
        Mockito.verify(consumerCurator, Mockito.never()).update(eq(consumer));
    }

    @Test
    public void updateGuest() {
        GuestId guest = new GuestId("some_guest");
        guestIdResource.updateGuest(consumer.getUuid(), guest.getGuestId(), guest);
        assertEquals(consumer, guest.getConsumer());
        Mockito.verify(guestIdCurator, Mockito.times(1)).merge(eq(guest));
    }

    @Test(expected = BadRequestException.class)
    public void updateGuestMismatchedGuestId() {
        GuestId guest = new GuestId("some_guest");
        guestIdResource.updateGuest(consumer.getUuid(), "other_id", guest);
    }

    /*
     * Update should add the id from the url to the GuestId object
     * if it does not already have one.
     */
    @Test
    public void updateGuestNoGuestId() {
        GuestId guest = new GuestId();
        guestIdResource.updateGuest(consumer.getUuid(), "some_id", guest);
        assertEquals(consumer, guest.getConsumer());
        assertEquals("some_id", guest.getGuestId());
        Mockito.verify(guestIdCurator, Mockito.times(1)).merge(eq(guest));
    }

    @Test
    public void deleteGuestNoConsumer() {
        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwner().getId())).thenReturn(null);
        guestIdResource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), false, null);
        Mockito.verify(guestIdCurator, Mockito.times(1)).delete(eq(guest));
        Mockito.verify(consumerResource, Mockito.never())
            .revokeGuestEntitlementsNotMatchingHost(eq(consumer), any(Consumer.class));
    }

    @Test
    public void updateGuestRevokeHostSpecific() {
        Consumer guestConsumer =
            new Consumer("guest_consumer", "guest_consumer", owner, ct);
        GuestId originalGuest = new GuestId("guest-id", guestConsumer);
        GuestId guest = new GuestId("guest-id");

        when(guestIdCurator.findByGuestIdAndOrg(
            eq(guest.getGuestId()), eq(owner))).thenReturn(originalGuest);
        when(consumerCurator.findByVirtUuid(eq(guest.getGuestId()),
            eq(owner.getId()))).thenReturn(guestConsumer);

        guestIdResource.updateGuest(consumer.getUuid(),
            guest.getGuestId(), guest);

        Mockito.verify(guestIdCurator, Mockito.times(1)).merge(eq(guest));
        Mockito.verify(consumerResource, Mockito.times(1))
            .revokeGuestEntitlementsNotMatchingHost(any(Consumer.class),
                any(Consumer.class));
    }

    @Test
    public void deleteGuestAndUnregister() {
        Consumer guestConsumer =
            new Consumer("guest_consumer", "guest_consumer", owner, ct);
        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwner().getId())).thenReturn(guestConsumer);
        guestIdResource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), true, null);
        Mockito.verify(guestIdCurator, Mockito.times(1)).delete(eq(guest));
        Mockito.verify(consumerResource, Mockito.never())
            .revokeGuestEntitlementsNotMatchingHost(eq(consumer), eq(guestConsumer));
        Mockito.verify(consumerResource, Mockito.times(1))
            .deleteConsumer(eq(guestConsumer.getUuid()), any(Principal.class));
    }

    /*
     * Should behave just like deleteGuest with no consumer
     */
    @Test
    public void deleteGuestNotFound() {
        GuestId guest = new GuestId("guest-id", consumer);
        when(guestIdCurator.findByConsumerAndId(eq(consumer),
            eq(guest.getGuestId()))).thenReturn(guest);
        when(consumerCurator.findByVirtUuid(guest.getGuestId(),
            consumer.getOwner().getId())).thenReturn(null);
        guestIdResource.deleteGuest(consumer.getUuid(),
            guest.getGuestId(), true, null);
        Mockito.verify(guestIdCurator, Mockito.times(1)).delete(eq(guest));
        Mockito.verify(consumerResource, Mockito.never())
            .revokeGuestEntitlementsNotMatchingHost(eq(consumer), any(Consumer.class));
    }

    private Page<List<GuestId>> buildPaginatedGuestIdList(List<GuestId> guests) {
        Page<List<GuestId>> page = new Page<List<GuestId>>();
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
            super(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        }

        public boolean performConsumerUpdates(Consumer updated, Consumer toUpdate) {
            return true;
        }

        public void revokeGuestEntitlementsNotMatchingHost(Consumer host, Consumer guest) {
        }
    }
}

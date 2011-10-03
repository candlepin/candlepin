/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.GuestId;
import org.fedoraproject.candlepin.model.ConsumerInstalledProduct;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Locale;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerResourceUpdateTest {

    @Mock private UserServiceAdapter userService;
    @Mock private IdentityCertServiceAdapter idCertService;
    @Mock private SubscriptionServiceAdapter subscriptionService;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private EventSink sink;
    @Mock private EventFactory eventFactory;
    @Mock private ActivationKeyCurator activationKeyCurator;
    private I18n i18n;

    private ConsumerResource resource;

    @Before
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.resource = new ConsumerResource(this.consumerCurator,
            this.consumerTypeCurator, null, this.subscriptionService, null,
            this.idCertService, null, this.i18n, this.sink, this.eventFactory, null, null,
            this.userService, null, null, null, null, null,
            this.activationKeyCurator, null);

    }

    @Test
    public void nothingChanged() throws Exception {
        Consumer consumer = new Consumer();
        String uuid = "FAKEUUID";
        consumer.setUuid(uuid);
        when(this.consumerCurator.findByUuid(uuid)).thenReturn(consumer);
        this.resource.updateConsumer(consumer.getUuid(), consumer);
        verify(sink, never()).sendEvent((Event) any());
    }

    @Test
    public void installedPackagesChanged() throws Exception {
        ConsumerInstalledProduct a = new ConsumerInstalledProduct("a", "Product A");
        ConsumerInstalledProduct b = new ConsumerInstalledProduct("b", "Product B");
        ConsumerInstalledProduct c = new ConsumerInstalledProduct("c", "Product C");

        Consumer consumer = new Consumer();
        String uuid = "FAKEUUID";
        consumer.setUuid(uuid);
        consumer.addInstalledProduct(a);
        consumer.addInstalledProduct(b);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(consumer);

        Consumer incoming = new Consumer();
        incoming.addInstalledProduct(b);
        incoming.addInstalledProduct(c);

        this.resource.updateConsumer(consumer.getUuid(), incoming);
        verify(sink).sendEvent((Event) any());
    }

    @Test
    public void testInstalledPackageSetEquality() throws Exception {
        Consumer a = new Consumer();
        a.addInstalledProduct(new ConsumerInstalledProduct("a", "Product A"));
        a.addInstalledProduct(new ConsumerInstalledProduct("b", "Product B"));
        a.addInstalledProduct(new ConsumerInstalledProduct("c", "Product C"));

        Consumer b = new Consumer();
        b.addInstalledProduct(new ConsumerInstalledProduct("a", "Product A"));
        b.addInstalledProduct(new ConsumerInstalledProduct("b", "Product B"));
        b.addInstalledProduct(new ConsumerInstalledProduct("c", "Product C"));

        Consumer c = new Consumer();
        c.addInstalledProduct(new ConsumerInstalledProduct("a", "Product A"));
        c.addInstalledProduct(new ConsumerInstalledProduct("c", "Product C"));

        Consumer d = new Consumer();
        d.addInstalledProduct(new ConsumerInstalledProduct("a", "Product A"));
        d.addInstalledProduct(new ConsumerInstalledProduct("b", "Product B"));
        d.addInstalledProduct(new ConsumerInstalledProduct("d", "Product D"));

        assertEquals(a.getInstalledProducts(), b.getInstalledProducts());
        assertFalse(a.getInstalledProducts().equals(c.getInstalledProducts()));
        assertFalse(a.getInstalledProducts().equals(d.getInstalledProducts()));
    }

    @Test
    public void testGuestListEquality() throws Exception {
        Consumer a = new Consumer();
        a.addGuestId(new GuestId("Guest A"));
        a.addGuestId(new GuestId("Guest B"));
        a.addGuestId(new GuestId("Guest C"));

        Consumer b = new Consumer();
        b.addGuestId(new GuestId("Guest A"));
        b.addGuestId(new GuestId("Guest B"));
        b.addGuestId(new GuestId("Guest C"));

        Consumer c = new Consumer();
        c.addGuestId(new GuestId("Guest A"));
        c.addGuestId(new GuestId("Guest C"));

        Consumer d = new Consumer();
        d.addGuestId(new GuestId("Guest A"));
        d.addGuestId(new GuestId("Guest B"));
        d.addGuestId(new GuestId("Guest D"));

        assertEquals(a.getGuestsIds(), b.getGuestsIds());
        assertFalse(a.getGuestsIds().equals(c.getGuestsIds()));
        assertFalse(a.getGuestsIds().equals(d.getGuestsIds()));
    }

    @Test
    public void testUpdateConsumerUpdatesGuestIds() {
        String uuid = "TEST_CONSUMER";
        String[] existingGuests = new String[]{"Guest 1", "Guest 2", "Guest 3"};
        Consumer existing = createConsumerWithGuests(existingGuests);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        // Create a consumer with 1 new guest.
        Consumer updated = createConsumerWithGuests("Guest 2");

        this.resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(1, existing.getGuestsIds().size());
        assertEquals("Guest 2", existing.getGuestsIds().get(0).getGuestId());
    }

    @Test
    public void testUpdateConsumerDoesNotChangeWhenGuestIdsNotIncludedInRequest() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        Consumer updated = new Consumer();
        this.resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(guests.length, existing.getGuestsIds().size());
    }

    @Test
    public void testUpdateConsumerClearsGuestListWhenRequestGuestListIsEmptyButNotNull() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        Consumer updated = new Consumer();
        updated.setGuestsIds(new ArrayList<GuestId>());
        this.resource.updateConsumer(existing.getUuid(), updated);
        assertTrue(existing.getGuestsIds().isEmpty());
    }

    private Consumer createConsumerWithGuests(String ... guestIds) {
        Consumer a = new Consumer();
        for (String guestId: guestIds) {
            a.addGuestId(new GuestId(guestId));
        }
        return a;
    }
}

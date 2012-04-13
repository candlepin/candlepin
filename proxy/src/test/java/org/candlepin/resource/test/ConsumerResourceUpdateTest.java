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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
    @Mock private PoolManager poolManager;
    @Mock private ComplianceRules complianceRules;
    @Mock private Entitler entitler;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;

    private I18n i18n;

    private ConsumerResource resource;

    @Before
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.resource = new ConsumerResource(this.consumerCurator,
            this.consumerTypeCurator, null, this.subscriptionService, null,
            this.idCertService, null, this.i18n, this.sink, this.eventFactory, null, null,
            this.userService, null, poolManager, null, null, null, null,
            this.activationKeyCurator, this.entitler, this.complianceRules,
            this.deletedConsumerCurator);

        when(complianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(new ComplianceStatus(new Date()));
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
    public void setStatusOnUpdate() throws Exception {
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
        verify(complianceRules).getStatus(eq(consumer), any(Date.class));
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

        assertEquals(a.getGuestIds(), b.getGuestIds());
        assertFalse(a.getGuestIds().equals(c.getGuestIds()));
        assertFalse(a.getGuestIds().equals(d.getGuestIds()));
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
        assertEquals(1, existing.getGuestIds().size());
        assertEquals("Guest 2", existing.getGuestIds().get(0).getGuestId());
    }

    @Test
    public void testUpdateConsumerDoesNotChangeGuestsWhenGuestIdsNotIncludedInRequest() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        Consumer updated = new Consumer();
        this.resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(guests.length, existing.getGuestIds().size());
    }

    @Test
    public void testUpdateConsumerClearsGuestListWhenRequestGuestListIsEmptyButNotNull() {
        String uuid = "TEST_CONSUMER";
        String[] guests = new String[]{ "Guest 1", "Guest 2" };
        Consumer existing = createConsumerWithGuests(guests);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        Consumer updated = new Consumer();
        updated.setGuestIds(new ArrayList<GuestId>());
        this.resource.updateConsumer(existing.getUuid(), updated);
        assertTrue(existing.getGuestIds().isEmpty());
    }

    @Test
    public void ensureCreateEventIsSentWhenGuestIdIsAddedToConsumer() {
        String uuid = "TEST_CONSUMER";
        Consumer existing = createConsumerWithGuests(new String[0]);
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        // Create a consumer with 1 new guest.
        Consumer updated = createConsumerWithGuests("Guest 1");

        Event expectedEvent = new Event();
        when(this.eventFactory.guestIdCreated(existing, updated.getGuestIds().get(0)))
            .thenReturn(expectedEvent);

        this.resource.updateConsumer(existing.getUuid(), updated);
        verify(sink).sendEvent(eq(expectedEvent));
    }

    @Test
    public void ensureEventIsSentWhenGuestIdIsremovedFromConsumer() {
        String uuid = "TEST_CONSUMER";
        Consumer existing = createConsumerWithGuests("Guest 1", "Guest 2");
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        // Create a consumer with one less guest id.
        Consumer updated = createConsumerWithGuests("Guest 2");

        Event expectedEvent = new Event();
        when(this.eventFactory.guestIdDeleted(existing, existing.getGuestIds().get(0)))
            .thenReturn(expectedEvent);

        this.resource.updateConsumer(existing.getUuid(), updated);
        verify(sink).sendEvent(eq(expectedEvent));
    }

    @Test
    public void ensureEventIsNotFiredWhenNoChangeWasMadeToConsumerGuestIds() {
        String uuid = "TEST_CONSUMER";
        Consumer existing = createConsumerWithGuests("Guest 1", "Guest 2");
        existing.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(existing);

        Consumer updated = createConsumerWithGuests("Guest 1", "Guest 2");
        updated.setUuid(uuid);

        Event event = new Event();
        when(this.eventFactory.consumerModified(existing, updated)).thenReturn(event);

        this.resource.updateConsumer(existing.getUuid(), updated);
        verify(sink).sendEvent(eq(event));
    }

    /*
     commented out per mkhusid, see 768872 comment #41

    @Test
    public void ensureNewGuestIsHealedIfItWasMigratedFromAnotherHost() {
        String uuid = "TEST_CONSUMER";
        Consumer existingHost = createConsumerWithGuests("Guest 1", "Guest 2");
        existingHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);
        ConsumerInstalledProduct installed = mock(ConsumerInstalledProduct.class);
        guest1.addInstalledProduct(installed);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);
        // Ensure that the guests host is the existing.
        when(consumerCurator.getHost("Guest 1")).thenReturn(existingHost);
        when(consumerCurator.findByUuid("Guest 1")).thenReturn(guest1);

        Consumer existingMigratedTo = createConsumerWithGuests();
        existingMigratedTo.setUuid("MIGRATED_TO");
        when(this.consumerCurator.findByUuid(existingMigratedTo.getUuid()))
            .thenReturn(existingMigratedTo);

        this.resource.updateConsumer(existingMigratedTo.getUuid(),
            createConsumerWithGuests("Guest 1"));

        verify(poolManager).revokeEntitlement(eq(entitlement));
        verify(entitler).bindByProducts(null, guest1, null);
    } */

    @Test
    public void ensureExistingGuestHasEntitlementIsRemovedIfAlreadyAssocWithDiffHost() {
        // the guest in this test does not have any installed products, we
        // expect them to get their entitlements stripped on migration
        String uuid = "TEST_CONSUMER";
        Consumer existingHost = createConsumerWithGuests("Guest 1", "Guest 2");
        existingHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);
        // Ensure that the guests host is the existing.
        when(consumerCurator.getHost("Guest 1")).thenReturn(existingHost);

        Consumer existingMigratedTo = createConsumerWithGuests("Guest 1");
        existingMigratedTo.setUuid("MIGRATED_TO");
        when(this.consumerCurator.findByUuid(existingMigratedTo.getUuid()))
            .thenReturn(existingMigratedTo);

        this.resource.updateConsumer(existingMigratedTo.getUuid(),
            createConsumerWithGuests("Guest 1"));

        verify(poolManager).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void ensureGuestEntitlementsUntouchedWhenGuestIsNewWithNoOtherHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests();
        host.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(host);

        Consumer updatedHost = createConsumerWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);

        // Ensure that the guest was not reported by another host.
        when(consumerCurator.getHost("Guest 1")).thenReturn(null);

        this.resource.updateConsumer(host.getUuid(), updatedHost);
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void ensureGuestEntitlementsUntouchedWhenGuestExistsWithNoOtherHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests("Guest 1");
        host.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(host);

        Consumer updatedHost = createConsumerWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);

        // Ensure that the guest was already reported by same host.
        when(consumerCurator.getHost("Guest 1")).thenReturn(host);

        this.resource.updateConsumer(host.getUuid(), updatedHost);
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void ensureGuestEntitlementsAreNotRevokedWhenGuestIsRemovedFromHost() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests("Guest 1", "Guest 2");
        host.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(host);

        Consumer updatedHost = createConsumerWithGuests("Guest 2");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost);
        //verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }


    @Test
    public void ensureGuestEntitlementsAreNotRemovedWhenGuestsAndHostAreTheSame() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests("Guest 1");
        host.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(host);

        Consumer updatedHost = createConsumerWithGuests("Guest 1");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");
        entitlement.getPool().setAttribute("requires_host", uuid);

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);
        when(consumerCurator.getHost("Guest 1")).thenReturn(host);

        this.resource.updateConsumer(host.getUuid(), updatedHost);

        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void guestEntitlementsNotRemovedIfEntitlementIsVirtOnlyButRequiresHostNotSet() {
        String uuid = "TEST_CONSUMER";
        Consumer host = createConsumerWithGuests("Guest 1", "Guest 2");
        host.setUuid(uuid);

        when(this.consumerCurator.findByUuid(uuid)).thenReturn(host);

        Consumer updatedHost = createConsumerWithGuests("Guest 2");
        updatedHost.setUuid(uuid);

        Entitlement entitlement = TestUtil.createEntitlement();
        entitlement.getPool().setAttribute("virt_only", "1");

        Consumer guest1 = new Consumer();
        guest1.setUuid("Guest 1");
        guest1.addEntitlement(entitlement);

        when(consumerCurator.findByVirtUuid("Guest 1")).thenReturn(guest1);

        this.resource.updateConsumer(host.getUuid(), updatedHost);

        //verify(consumerCurator).findByVirtUuid(eq("Guest 1"));
        verify(poolManager, never()).revokeEntitlement(eq(entitlement));
    }

    @Test
    public void multipleUpdatesCanOccur() {
        String uuid = "A Consumer";
        String expectedFactName = "FACT1";
        String expectedFactValue = "F1";
        ConsumerInstalledProduct expectedInstalledProduct =
            new ConsumerInstalledProduct("P1", "Product One");
        GuestId expectedGuestId = new GuestId("GUEST_ID_1");

        Consumer updated = new Consumer();
        updated.setUuid(uuid);
        updated.setFact(expectedFactName, expectedFactValue);
        updated.addInstalledProduct(expectedInstalledProduct);
        updated.addGuestId(expectedGuestId);

        Consumer existing = new Consumer();
        existing.setUuid(updated.getUuid());
        existing.setFacts(new HashMap<String, String>());
        existing.setInstalledProducts(new HashSet<ConsumerInstalledProduct>());

        when(this.consumerCurator.findByUuid(existing.getUuid())).thenReturn(existing);

        this.resource.updateConsumer(existing.getUuid(), updated);
        assertEquals(1, existing.getFacts().size());
        assertEquals(expectedFactValue, existing.getFact(expectedFactName));
        assertEquals(1, existing.getInstalledProducts().size());
        assertTrue(existing.getInstalledProducts().contains(expectedInstalledProduct));
        assertEquals(1, existing.getGuestIds().size());
        assertTrue(existing.getGuestIds().contains(expectedGuestId));
    }

    private Consumer createConsumerWithGuests(String ... guestIds) {
        Consumer a = new Consumer();
        for (String guestId : guestIds) {
            a.addGuestId(new GuestId(guestId));
        }
        return a;
    }
}

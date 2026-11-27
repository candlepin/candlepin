/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class SubscriptionReconcilerV2Test {
    private PoolCurator curator;
    private SubscriptionReconcilerV2 reconciler;
    private Owner owner;

    @BeforeEach
    void setup() {
        this.curator = mock(PoolCurator.class);
        this.owner = new Owner().setId("owner");
        this.reconciler = new SubscriptionReconcilerV2(this.curator,
            I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK));
    }

    @Test
    void matchesPoolIdentityWithoutEntitlementOrQuantityMatching() throws Exception {
        Pool existing = this.pool("local-pool", "source", "stable-subscription");
        existing.setQuantity(37L);
        existing.setUpstreamEntitlementId("legacy-entitlement");
        when(this.curator.listByOwnerAndTypes("owner", PoolType.NORMAL)).thenReturn(List.of(existing));
        SubscriptionDTO incoming = this.subscription("source");
        this.reconciler.reconcile(this.owner, List.of(incoming));
        assertEquals("stable-subscription", incoming.getId());
        assertEquals(-1L, incoming.getQuantity());
        assertNull(incoming.getUpstreamEntitlementId());
        assertEquals("legacy-entitlement", existing.getUpstreamEntitlementId());
        assertEquals(37L, existing.getQuantity());
    }

    @Test
    void ignoresLocalPoolsAndPreservesNewSubscriptionIds() throws Exception {
        Pool local = this.pool("local", null, "local-subscription");
        when(this.curator.listByOwnerAndTypes("owner", PoolType.NORMAL)).thenReturn(List.of(local));
        SubscriptionDTO incoming = this.subscription("source");
        this.reconciler.reconcile(this.owner, List.of(incoming));
        assertEquals("new-subscription", incoming.getId());
        assertNull(local.getUpstreamEntitlementId());
    }

    @Test
    void choosesDeterministicLegacyPoolAcrossInputOrders() throws Exception {
        Pool first = this.pool("a", "source", "first");
        Pool second = this.pool("b", "source", "second");
        List<Pool> pools = new ArrayList<>(List.of(second, first));
        when(this.curator.listByOwnerAndTypes("owner", PoolType.NORMAL)).thenReturn(pools);
        SubscriptionDTO incoming = this.subscription("source");
        this.reconciler.reconcile(this.owner, List.of(incoming));
        assertEquals("first", incoming.getId());
        Collections.reverse(pools);
        this.reconciler.reconcile(this.owner, List.of(incoming));
        assertEquals("first", incoming.getId());
        assertNull(first.getUpstreamEntitlementId());
        assertNull(second.getUpstreamEntitlementId());
    }

    @Test
    void rejectsDuplicateUpstreamIdentityBeforeLookup() {
        assertThrows(SyncDataFormatException.class, () -> this.reconciler.reconcile(this.owner,
            List.of(this.subscription("same"), this.subscription("same"))));
        verifyNoInteractions(this.curator);
    }

    @Test
    void rejectsInvalidQuantityAndMissingPoolIdentity() {
        SubscriptionDTO invalid = this.subscription("source").setQuantity(0L);
        assertThrows(SyncDataFormatException.class,
            () -> this.reconciler.reconcile(this.owner, List.of(invalid)));
        invalid.setQuantity(-1L).setUpstreamPoolId("");
        assertThrows(SyncDataFormatException.class,
            () -> this.reconciler.reconcile(this.owner, List.of(invalid)));
        verifyNoInteractions(this.curator);
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> this.reconciler.reconcile(null, List.of()));
        assertThrows(NullPointerException.class, () -> this.reconciler.reconcile(this.owner, null));
        assertThrows(SyncDataFormatException.class,
            () -> this.reconciler.reconcile(this.owner, Collections.singletonList(null)));
        verifyNoInteractions(this.curator);
    }

    private SubscriptionDTO subscription(String upstreamPool) {
        return new SubscriptionDTO()
            .setId("new-subscription")
            .setUpstreamPoolId(upstreamPool)
            .setUpstreamConsumerId("consumer")
            .setQuantity(-1L);
    }

    private Pool pool(String id, String upstream, String subscription) {
        return new Pool()
            .setId(id)
            .setUpstreamPoolId(upstream)
            .setSubscriptionId(subscription);
    }
}

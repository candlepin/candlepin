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

import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;

/**
 * Reconciliation of unlimited v2 subscriptions solely by upstream pool identity
 * <p>
 * Entitlement IDs and quantities do not participate. If a legacy import left multiple pools for the same
 * upstream pool, the pool with the lexically first local ID is retained; the refresher removes the others.
 */
public class SubscriptionReconcilerV2 {
    private final PoolCurator poolCurator;
    private final I18n i18n;

    /**
     * Initializes the v2 reconciler
     *
     * @param poolCurator
     *     the local pool lookup
     * @param i18n
     *     the import error message translator
     */
    @Inject
    public SubscriptionReconcilerV2(PoolCurator poolCurator, I18n i18n) {
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    /**
     * Reuse of downstream subscription IDs for matching upstream pools
     * <p>
     * Locally managed pools are never reused. The
     * existing pool entities are not modified. New subscriptions keep their generated downstream IDs.
     *
     * @param owner
     *     the local receiving owner
     * @param subscriptions
     *     incoming subscriptions with unique, nonempty upstream pool IDs and unlimited quantities
     * @throws SyncDataFormatException
     *     if an incoming subscription has invalid identity or quantity
     */
    public void reconcile(Owner owner, Collection<SubscriptionDTO> subscriptions) throws SyncDataFormatException {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(subscriptions);
        Set<String> incoming = new HashSet<>();
        for (SubscriptionDTO subscription : subscriptions) {
            if (subscription == null || subscription.getUpstreamPoolId() == null ||
                subscription.getUpstreamPoolId().isBlank() || subscription.getUpstreamConsumerId() == null ||
                subscription.getUpstreamConsumerId().isBlank() || !Long.valueOf(-1L).equals(subscription.getQuantity()) ||
                !incoming.add(subscription.getUpstreamPoolId())) {
                throw new SyncDataFormatException(this.i18n.tr("Invalid or duplicate v2 subscription identity"));
            }
        }

        Map<String, Pool> existing = new HashMap<>();
        for (Pool pool : this.poolCurator.listByOwnerAndTypes(owner.getId(), PoolType.NORMAL)) {
            if (pool.getUpstreamPoolId() != null && pool.getSubscriptionId() != null) {
                existing.merge(pool.getUpstreamPoolId(), pool,
                    (left, right) -> left.getId().compareTo(right.getId()) <= 0 ? left : right);
            }
        }
        for (SubscriptionDTO subscription : subscriptions) {
            Pool pool = existing.get(subscription.getUpstreamPoolId());
            if (pool != null) {
                subscription.setId(pool.getSubscriptionId());
            }
        }
    }
}

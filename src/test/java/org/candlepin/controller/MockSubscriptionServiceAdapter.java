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
package org.candlepin.controller;

import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;



class MockSubscriptionServiceAdapter implements SubscriptionServiceAdapter {
    private Map<String, SubscriptionInfo> submap;

    public MockSubscriptionServiceAdapter(Collection<? extends SubscriptionInfo> sinfo) {
        this.submap = (sinfo != null ? sinfo.stream() : Stream.<SubscriptionInfo>empty())
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(SubscriptionInfo::getId, Function.identity()));
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions() {
        return this.submap.values();
    }

    @Override
    public SubscriptionInfo getSubscription(String subscriptionId) {
        return this.submap.get(subscriptionId);
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions(String ownerKey) {
        return this.submap.values();
    }

    @Override
    public Collection<String> getSubscriptionIds(String ownerKey) {
        return this.submap.values().stream()
            .map(SubscriptionInfo::getId)
            .collect(Collectors.toList());
    }

    private boolean productUsesProductId(ProductInfo pinfo, String productId) {
        if (pinfo == null || productId == null) {
            return false;
        }

        if (productId.equals(pinfo.getId())) {
            return true;
        }

        Collection<? extends ProductInfo> providedProducts = pinfo.getProvidedProducts();
        if (providedProducts != null) {
            for (ProductInfo ppinfo : providedProducts) {
                if (ppinfo != null && productId.equals(ppinfo.getId())) {
                    return true;
                }
            }
        }

        // TODO: if we need to factor in derived products, recursively call this function
        // with the derived product ref.

        return false;
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptionsByProductId(String productId) {
        return this.submap.values().stream()
            .filter(sub -> this.productUsesProductId(sub.getProduct(), productId))
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(String ownerKey) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
        // intentionally left empty
    }

    @Override
    public boolean canActivateSubscription(ConsumerInfo consumer) {
        return true;
    }

    @Override
    public void activateSubscription(ConsumerInfo consumer, String email, String emailLocale) {
        // intentionally left empty
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}

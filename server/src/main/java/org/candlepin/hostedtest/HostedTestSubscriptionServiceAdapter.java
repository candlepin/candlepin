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
package org.candlepin.hostedtest;

import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The HostedTestSubscriptionServiceAdapter is a SubscriptionServiceAdapter implementation backed by
 * the HostedTestDataStore upstream simulator.
 */
@Singleton
public class HostedTestSubscriptionServiceAdapter implements SubscriptionServiceAdapter {

    private final HostedTestDataStore datastore;

    @Inject
    public HostedTestSubscriptionServiceAdapter(HostedTestDataStore datastore) {
        if (datastore == null) {
            throw new IllegalArgumentException("datastore is null");
        }

        this.datastore = datastore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions() {
        return this.datastore.listSubscriptions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionInfo getSubscription(String subscriptionId) {
        return this.datastore.getSubscription(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions(String ownerKey) {
        if (ownerKey != null) {
            return this.datastore.listSubscriptions()
                .stream()
                .filter(s -> s.getOwner() != null && ownerKey.equals(s.getOwner().getKey()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getSubscriptionIds(String ownerKey) {
        if (ownerKey != null) {
            return this.datastore.listSubscriptions()
                .stream()
                .filter(s -> s.getOwner() != null && ownerKey.equals(s.getOwner().getKey()))
                .map(s -> s.getId())
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptionsByProductId(String productId) {
        if (productId != null) {
            Predicate<SubscriptionInfo> predicate = sub -> {
                if (sub.getProduct() != null && productId.equals(sub.getProduct().getId())) {
                    return true;
                }

                if (sub.getProvidedProducts() != null) {
                    for (ProductInfo product : sub.getProvidedProducts()) {
                        if (product != null && productId.equals(product.getId())) {
                            return true;
                        }
                    }
                }

                return false;
            };

            return this.datastore.listSubscriptions()
                .stream()
                .filter(predicate)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnacceptedSubscriptionTerms(String ownerKey) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendActivationEmail(String subscriptionId) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canActivateSubscription(ConsumerInfo consumer) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activateSubscription(ConsumerInfo consumer, String email, String emailLocale) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

}

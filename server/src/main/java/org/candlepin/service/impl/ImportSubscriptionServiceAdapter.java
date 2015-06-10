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

package org.candlepin.service.impl;

import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author mstead
 *
 */
public class ImportSubscriptionServiceAdapter implements
        SubscriptionServiceAdapter {

    private List<Subscription> subscriptions;
    private Map<String, Subscription> subsBySubId = new HashMap<String, Subscription>();

    public ImportSubscriptionServiceAdapter() {
        this(new LinkedList<Subscription>());
    }

    public ImportSubscriptionServiceAdapter(List<Subscription> subs) {
        this.subscriptions = subs;
        for (Subscription sub : this.subscriptions) {
            subsBySubId.put(sub.getId(), sub);
        }
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        return subscriptions;
    }

    @Override
    public List<String> getSubscriptionIds(Owner owner) {
        return new ArrayList<String>(subsBySubId.keySet());
    }

    @Override
    public Subscription getSubscription(String subscriptionId) {
        return this.subsBySubId.get(subscriptionId);
    }

    @Override
    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public void activateSubscription(Consumer consumer, String email,
            String emailLocale) {
    }

    @Override
    public Subscription createSubscription(Subscription s) {
        return s;
    }

    @Override
    public void deleteSubscription(Subscription s) {
        // no op for now.
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(Owner owner) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
        // hosted-only
    }

    @Override
    public boolean canActivateSubscription(Consumer consumer) {
        return false;
    }

    @Override
    public List<Subscription> getSubscriptions(Product product) {
        List<Subscription> subs = new LinkedList<Subscription>();
        for (Subscription sub : this.subscriptions) {
            if (product.getUuid().equals(sub.getProduct().getUuid())) {
                subs.add(sub);
                continue;
            }

            for (Product p : sub.getProvidedProducts()) {
                if (product.getUuid().equals(p.getUuid())) {
                    subs.add(sub);
                    break;
                }
            }
        }
        return subs;
    }

}

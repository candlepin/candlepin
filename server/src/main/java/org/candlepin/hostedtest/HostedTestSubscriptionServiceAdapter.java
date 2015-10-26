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

import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * The HostedTestSubscriptionServiceAdapter class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
public class HostedTestSubscriptionServiceAdapter implements SubscriptionServiceAdapter {

    private static Map<String, Subscription> idMap = new HashMap<String, Subscription>();
    private static Map<String, List<Subscription>> ownerMap = new HashMap<String, List<Subscription>>();
    private static Map<String, List<Subscription>> productMap = new HashMap<String, List<Subscription>>();

    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        if (ownerMap.containsKey(owner.getKey())) {
            return ownerMap.get(owner.getKey());
        }
        return new ArrayList<Subscription>();
    }

    @Override
    public List<String> getSubscriptionIds(Owner owner) {
        List<String> ids = new ArrayList<String>();
        List<Subscription> subscriptions = ownerMap.get(owner.getKey());
        if (subscriptions != null) {
            for (Subscription subscription : subscriptions) {
                ids.add(subscription.getId());
            }
        }
        return ids;
    }

    @Override
    public List<Subscription> getSubscriptions(Product product) {
        if (productMap.containsKey(product.getId())) {
            return productMap.get(product.getId());
        }
        return new ArrayList<Subscription>();
    }

    @Override
    public Subscription getSubscription(String subscriptionId) {
        return idMap.get(subscriptionId);
    }

    @Override
    public List<Subscription> getSubscriptions() {
        List<Subscription> result = new ArrayList<Subscription>();
        for (String id : idMap.keySet()) {
            result.add(idMap.get(id));
        }
        return result;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(Owner owner) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
    }

    @Override
    public boolean canActivateSubscription(Consumer consumer) {
        return false;
    }

    @Override
    public void activateSubscription(Consumer consumer, String email, String emailLocale) {
    }

    @Override
    public Subscription createSubscription(Subscription s) {
        idMap.put(s.getId(), s);
        if (!ownerMap.containsKey(s.getOwner().getKey())) {
            ownerMap.put(s.getOwner().getKey(), new ArrayList<Subscription>());
        }
        ownerMap.get(s.getOwner().getKey()).add(s);
        if (!productMap.containsKey(s.getProduct().getId())) {
            productMap.put(s.getProduct().getId(), new ArrayList<Subscription>());
        }
        productMap.get(s.getProduct().getId()).add(s);
        return s;
    }

    public Subscription updateSubscription(Subscription ss) {
        deleteSubscription(ss.getId());
        Subscription s = createSubscription(ss);
        return s;
    }

    @Override
    public void deleteSubscription(Subscription s) {
        deleteSubscription(s.getId());
    }

    public boolean deleteSubscription(String id) {
        if (idMap.containsKey(id)) {
            Subscription s = idMap.remove(id);
            ownerMap.get(s.getOwner().getKey()).remove(s);
            productMap.get(s.getProduct().getId()).remove(s);
            return true;
        }
        return false;
    }

    public void deleteAllSubscriptions() {
        idMap.clear();
        ownerMap.clear();
        productMap.clear();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

}

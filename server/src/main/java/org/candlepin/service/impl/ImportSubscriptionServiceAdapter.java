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

import org.candlepin.common.exceptions.ServiceUnavailableException;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * @author mstead
 */
public class ImportSubscriptionServiceAdapter implements SubscriptionServiceAdapter {

    private List<Subscription> subscriptions;
    private Map<String, Subscription> subsBySubId = new HashMap<>();
    @Inject private I18n i18n;

    public ImportSubscriptionServiceAdapter() {
        this(new LinkedList<>());
    }

    public ImportSubscriptionServiceAdapter(List<Subscription> subs) {
        this.subscriptions = subs;
        for (Subscription sub : this.subscriptions) {
            subsBySubId.put(sub.getId(), sub);
        }
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public SubscriptionInfo getSubscription(String subscriptionId) {
        return this.subsBySubId.get(subscriptionId);
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptions(String ownerKey) {
        return subscriptions;
    }

    @Override
    public Collection<String> getSubscriptionIds(String ownerKey) {
        return new ArrayList<>(subsBySubId.keySet());
    }

    @Override
    public Collection<? extends SubscriptionInfo> getSubscriptionsByProductId(String productId) {
        List<SubscriptionInfo> subs = new LinkedList<>();

        if (productId != null) {
            for (SubscriptionInfo sub : this.subscriptions) {
                if (productId.equals(sub.getProduct().getId())) {
                    subs.add(sub);
                    continue;
                }

                for (ProductInfo p : sub.getProvidedProducts()) {
                    if (productId.equals(p.getId())) {
                        subs.add(sub);
                        break;
                    }
                }
            }
        }

        return subs;
    }

    @Override
    public void activateSubscription(ConsumerInfo consumer, String email, String emailLocale) {
        throw new ServiceUnavailableException(
                i18n.tr("Standalone candlepin does not support redeeming a subscription."));
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(String ownerKey) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
        // hosted-only
    }

    @Override
    public boolean canActivateSubscription(ConsumerInfo consumer) {
        return false;
    }

}
